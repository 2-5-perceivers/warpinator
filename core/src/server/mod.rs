pub mod authenticator;
mod discovery;
#[cfg(feature = "power_manager")]
pub mod power_manager;
pub mod remote_manager;
pub mod remote_worker;
pub mod transfers;

use std::net::{IpAddr, SocketAddr};
use std::sync::Arc;

use mdns_sd::{ServiceDaemon, ServiceInfo};
use tokio_util::sync::CancellationToken;
use tonic::transport::{Identity, Server, ServerTlsConfig};

use crate::config::protocol::ProtocolConfig;
use crate::config::user::UserConfig;
use crate::grpc;
use crate::proto::ServiceRegistration;
use crate::proto::warp_registration_server::WarpRegistrationServer;
use crate::proto::warp_server::WarpServer;
use crate::server::discovery::DiscoveryService;

const SERVICE_DOMAIN: &str = "_warpinator._tcp.local.";
const SERVICE_API_VERSION: u16 = 2;

#[derive(Debug, thiserror::Error)]
pub enum WarpinatorBuildError {
    #[error("service name is required")]
    MissingServiceName,

    #[error("at least one bind address (IPv4 or IPv6) is required")]
    MissingBindAddress,

    #[error("failed to initialize authenticator: {0}")]
    Authenticator(#[from] authenticator::AuthenticatorError),

    #[error("failed to create remote manager")]
    RemoteManagerInit,

    #[error("failed to create mDNS daemon: {0}")]
    MdnsDaemon(#[from] mdns_sd::Error),

    #[cfg(feature = "power_manager")]
    #[error("no power manager provided. disable the feature if unwanted")]
    NoPowerManager,
}

#[derive(Debug, thiserror::Error)]
pub enum WarpinatorServeError {
    #[error("at least one bind address (IPv4 or IPv6) is required")]
    MissingBindAddress,

    #[error("failed to configure TLS: {0}")]
    TlsConfig(#[from] tonic::transport::Error),

    #[error("registration server failed: {0}")]
    RegistrationServer(tonic::transport::Error),

    #[error("warp server failed: {0}")]
    WarpServer(tonic::transport::Error),

    #[error("Failed to build mDNS service info: {0}")]
    MdnsServiceInfo(#[from] mdns_sd::Error),

    #[error("Failed to start service thread")]
    ServiceThread(#[from] tokio::task::JoinError),
}

#[derive(Default)]
pub struct WarpinatorServerBuilder {
    user_config: Option<UserConfig>,
    protocol_config: Option<ProtocolConfig>,
    service_name: Option<String>,
    #[cfg(feature = "power_manager")]
    power_manager: Option<Arc<dyn power_manager::PowerManager + Send + Sync>>,
}

impl WarpinatorServerBuilder {
    pub fn user_config(mut self, config: UserConfig) -> Self {
        self.user_config = Some(config);
        self
    }

    pub fn protocol_config(mut self, config: ProtocolConfig) -> Self {
        self.protocol_config = Some(config);
        self
    }

    pub fn service_name(mut self, service_name: &str) -> Self {
        self.service_name = Some(service_name.to_string());
        self
    }

    #[cfg(feature = "power_manager")]
    pub fn power_manager(
        mut self,
        power_manager: Arc<dyn power_manager::PowerManager + Send + Sync>,
    ) -> Self {
        self.power_manager = Some(power_manager);
        self
    }

    pub fn build(self) -> Result<WarpinatorServer, WarpinatorBuildError> {
        let service_name = self.service_name.ok_or(WarpinatorBuildError::MissingServiceName)?;
        let user_config = self.user_config.unwrap_or_default();
        let protocol_config = self.protocol_config.unwrap_or_default();

        #[cfg(feature = "power_manager")]
        let power_manager = self.power_manager.ok_or(WarpinatorBuildError::NoPowerManager)?;

        let cancellation_token = CancellationToken::new();

        let authenticator = Arc::new(authenticator::Authenticator::new(
            user_config.group_code.clone(),
            user_config.hostname.as_str(),
            user_config.bind_addr_v4.ok_or(WarpinatorBuildError::MissingBindAddress)?.into(),
        )?);

        let remotes = remote_manager::RemoteManager::new(
            cancellation_token.clone(),
            authenticator.clone(),
            protocol_config.clone(),
            user_config.clone(),
            service_name.clone(),
            #[cfg(feature = "power_manager")]
            power_manager.clone(),
        )
        .ok_or(WarpinatorBuildError::RemoteManagerInit)?;

        Ok(WarpinatorServer {
            user_config,
            protocol_config,
            service_name,
            mdns: ServiceDaemon::new()?,
            authenticator,
            remotes,
            cancellation_token,
            #[cfg(feature = "power_manager")]
            power_manager,
        })
    }
}

pub struct WarpinatorServer {
    user_config: UserConfig,
    protocol_config: ProtocolConfig,
    service_name: String,
    mdns: ServiceDaemon,
    authenticator: Arc<authenticator::Authenticator>,
    pub remotes: remote_manager::RemoteManager,
    cancellation_token: CancellationToken,
    #[cfg(feature = "power_manager")]
    power_manager: Arc<dyn power_manager::PowerManager>,
}

impl WarpinatorServer {
    pub fn builder() -> WarpinatorServerBuilder {
        WarpinatorServerBuilder::default()
    }

    pub async fn serve(self) -> Result<(), WarpinatorServeError> {
        self.serve_with_shutdown(std::future::pending()).await
    }

    #[tracing::instrument(skip(self, shutdown), name = "serve", level = "info", err)]
    pub async fn serve_with_shutdown(
        self,
        shutdown: impl Future<Output = ()>,
    ) -> Result<(), WarpinatorServeError> {
        let identity = Identity::from_pem(
            self.authenticator.as_ref().cert_pem(),
            self.authenticator.as_ref().private_key_pem(),
        );
        let tls = ServerTlsConfig::new().identity(identity);

        // Registration server
        let reg_addr_v4 = self
            .user_config
            .bind_addr_v4
            .map(|addr_v4| SocketAddr::new(IpAddr::V4(addr_v4), self.user_config.reg_port));
        let reg_addr_v6 = self
            .user_config
            .bind_addr_v6
            .map(|addr_v6| SocketAddr::new(IpAddr::V6(addr_v6), self.user_config.reg_port));
        let reg_service_message = ServiceRegistration {
            service_id: self.service_name.clone(),
            ip: reg_addr_v4.map_or("".to_string(), |reg| reg.ip().to_string()),
            port: self.user_config.port as u32,
            auth_port: self.user_config.reg_port as u32,
            hostname: self.user_config.hostname.clone(),
            api_version: SERVICE_API_VERSION as u32,
            ipv6: reg_addr_v6.map_or("".to_string(), |reg| reg.ip().to_string()),
        };
        let reg_svc = grpc::registration::RegistrationServer::new(
            self.authenticator.clone(),
            self.remotes.clone(),
            reg_service_message,
        );
        let reg_ct = self.cancellation_token.clone();
        let reg_handle = tokio::spawn(async move {
            Server::builder()
                .add_service(WarpRegistrationServer::new(reg_svc))
                .serve_with_shutdown(reg_addr_v4.unwrap(), async {
                    reg_ct.cancelled().await;
                })
                .await
        });

        // Warp server
        let warp_addr_v4 = self
            .user_config
            .bind_addr_v4
            .map(|addr_v4| SocketAddr::new(IpAddr::V4(addr_v4), self.user_config.port));
        // let warp_addr_v6 = self.user_config.bind_addr_v6.map(|addr_v6|
        // SocketAddr::new(IpAddr::V6(addr_v6), self.user_config.port));
        let warp_svc = grpc::warp::WarpServer::new(
            self.user_config.clone(),
            self.protocol_config.clone(),
            self.remotes.clone(),
            #[cfg(feature = "power_manager")]
            self.power_manager.clone(),
        );
        let warp_ct = self.cancellation_token.clone();
        let warp_handle = tokio::spawn(async move {
            Server::builder()
                .tls_config(tls)?
                .add_service(WarpServer::new(warp_svc))
                .serve_with_shutdown(warp_addr_v4.unwrap(), async {
                    warp_ct.cancelled().await;
                })
                .await
        });

        self.announce_mdns().await?;

        tokio::select! {
            _ = shutdown => {
                tracing::info!("Shutdown signal received, stopping servers...");
                self.cancellation_token.cancel();
                self.shutdown_mdns().await;
                Ok(())
            },
            reg_result = reg_handle => {
                tracing::error!("Registration server task ended unexpectedly");
                self.cancellation_token.cancel();
                let reg_result = reg_result?;
                reg_result.map_err(WarpinatorServeError::RegistrationServer)
            },
            warp_result = warp_handle => {
                tracing::error!("Warp server task ended unexpectedly");
                self.cancellation_token.cancel();
                let warp_result = warp_result?;
                warp_result.map_err(WarpinatorServeError::WarpServer)
            },
        }
    }

    async fn announce_mdns(&self) -> Result<(), WarpinatorServeError> {
        let mdns = self.mdns.clone();

        let service_info = ServiceInfo::new(
            SERVICE_DOMAIN,
            &self.service_name,
            &format!("{}.local.", self.user_config.hostname.clone()),
            IpAddr::from(
                self.user_config.bind_addr_v4.ok_or(WarpinatorServeError::MissingBindAddress)?,
            ),
            self.user_config.port,
            &[
                ("type", "real"),
                ("hostname", &self.user_config.hostname.clone()),
                ("api-version", SERVICE_API_VERSION.to_string().as_str()),
                ("auth-port", self.user_config.reg_port.to_string().as_str()),
            ][..],
        )?;

        mdns.unregister(service_info.get_fullname()).map_or_else(
            |_| tracing::debug!("No existing mDNS service to unregister"),
            |_| tracing::debug!("Unregistered existing mDNS service"),
        );

        tokio::time::sleep(std::time::Duration::from_millis(500)).await;

        mdns.register(service_info)?;
        tracing::info!(
            service_name = self.service_name,
            mdns_address =
                self.user_config.bind_addr_v4.map_or("".to_string(), |addr| addr.to_string()),
            mdns_port = self.user_config.port,
            "mDNS announced",
        );

        let discovery_service = DiscoveryService::new(
            self.remotes.clone(),
            mdns.clone(),
            SERVICE_DOMAIN.to_string(),
            #[cfg(feature = "power_manager")]
            self.power_manager.clone(),
        );

        tokio::spawn(async move {
            let _ = discovery_service.start().await;
        });

        Ok(())
    }

    async fn shutdown_mdns(&self) -> () {
        // This will automatically unregister all services and shut down the
        // daemon
        let mut result = self.mdns.shutdown();
        if let Err(mdns_sd::Error::Again) = result {
            tracing::debug!("mDNS shutdown returned Again, retrying");
            result = self.mdns.shutdown();
        }
        if let Err(e) = &result {
            tracing::warn!(e=%e, "mDNS daemon did not shut down cleanly");
        } else if let Ok(stream) = &result {
            while stream.recv_async().await.is_ok() {}
            tracing::info!("mDNS daemon shut down");
        }
    }
}
