use std::collections::HashMap;
use std::net::IpAddr;
use std::ops::Deref;
use std::str::FromStr;
use std::sync::atomic::AtomicBool;
use std::sync::{Arc, Weak};

use thiserror::Error;
use tokio::sync::{RwLock, broadcast};
use tokio_util::sync::CancellationToken;
use tonic::Code;
use tonic::transport::Channel;
use tracing::instrument;

use crate::config::protocol::ProtocolConfig;
use crate::config::user::UserConfig;
use crate::proto::ServiceRegistration;
use crate::server::SERVICE_API_VERSION;
use crate::server::authenticator::Authenticator;
use crate::server::remote_worker::{ConnectRemoteError, RemoteWorker};
#[cfg(feature = "messaging")]
use crate::types::message::Message;
use crate::types::remote::{Remote, RemoteState};
use crate::types::transfer::Transfer;

#[non_exhaustive]
#[derive(Debug, Clone)]
#[cfg_attr(feature = "serde", derive(serde::Serialize))]
pub enum WarpEvent {
    RemoteAdded(String),             // uuid
    RemoteUpdated(String),           // uuid
    TransferAdded(String, String),   // remote_uuid, transfer_uuid
    TransferUpdated(String, String), // remote_uuid, transfer_uuid
    TransferRemoved(String, String), // remote_uuid, transfer_uuid
    #[cfg(feature = "messaging")]
    MessageAdded(String, String), // remote_uuid, message_uuid
    #[cfg(feature = "messaging")]
    MessageRemoved(String, String), // remote_uuid, message_uuid
}

#[derive(Error, Debug)]
pub enum UpdateError {
    #[error("Resource not found")]
    NotFound,
}

#[cfg_attr(feature = "serde", derive(serde::Serialize))]
#[cfg_attr(feature = "serde", serde(rename_all = "snake_case"))]
#[derive(Error, Debug)]
pub enum ManualConnectionError {
    #[error("Invalid URL")]
    InvalidUrl,
    #[error("Failed to register with remote")]
    FailedToRegister,
    #[error("Remote is unavailable")]
    Unavailable,
    #[error("Remote had an internal error")]
    RemoteInternal,
    #[error("Remote does not support manual connections")]
    RemoteUnimplemented,
    #[error("Connecting already in progress")]
    AlreadyConnecting,
    #[error("Already connected")]
    AlreadyConnected,
    #[error(transparent)]
    FailedToConnect(ConnectRemoteError),
}

#[derive(Debug)]
pub struct RemoteManagerInner {
    remotes: RwLock<HashMap<String, Remote>>,
    workers: RwLock<HashMap<String, Arc<RemoteWorker>>>,
    event_tx: broadcast::Sender<WarpEvent>,
    root_token: CancellationToken,
    authenticator: Arc<Authenticator>,
    protocol_config: ProtocolConfig,
    should_use_compression: Arc<AtomicBool>,
    server_hostname: String,
    server_ip: IpAddr,
    server_fullname: String,
    #[cfg(feature = "power_manager")]
    power_manager: Arc<dyn crate::server::power_manager::PowerManager>,
}

#[derive(Clone, Debug)]
pub struct RemoteManager {
    inner: Arc<RemoteManagerInner>,
    registration_message: ServiceRegistration,
}

#[derive(Clone, Debug)]
pub(crate) struct WeakRemoteManager {
    inner: Weak<RemoteManagerInner>,
}

impl Deref for RemoteManager {
    type Target = RemoteManagerInner;

    fn deref(&self) -> &Self::Target {
        &self.inner
    }
}

impl WeakRemoteManager {
    pub(crate) fn upgrade(&self) -> Option<Arc<RemoteManagerInner>> {
        self.inner.upgrade()
    }
}

impl RemoteManager {
    pub(crate) fn new(
        cancellation_token: CancellationToken,
        authenticator: Arc<Authenticator>,
        protocol_config: ProtocolConfig,
        user_config: UserConfig,
        server_fullname: String,
        #[cfg(feature = "power_manager")] power_manager: Arc<
            dyn crate::server::power_manager::PowerManager,
        >,
    ) -> Option<Self> {
        let (tx, _) = broadcast::channel(256);
        let inner = Arc::new(RemoteManagerInner {
            remotes: RwLock::new(HashMap::new()),
            workers: RwLock::new(HashMap::new()),
            event_tx: tx,
            root_token: cancellation_token,
            authenticator,
            protocol_config,
            should_use_compression: user_config.use_compression.clone(),
            server_hostname: user_config.hostname.clone(),
            server_ip: IpAddr::from(user_config.bind_addr_v4?),
            server_fullname: server_fullname.clone(),
            #[cfg(feature = "power_manager")]
            power_manager,
        });
        Some(Self {
            inner,
            registration_message: ServiceRegistration {
                service_id: server_fullname,
                ip: user_config.bind_addr_v4?.to_string(),
                port: user_config.port as u32,
                hostname: user_config.hostname.clone(),
                api_version: SERVICE_API_VERSION as u32,
                auth_port: user_config.reg_port as u32,
                ipv6: "".to_string(),
            },
        })
    }

    pub(crate) fn downgrade(&self) -> WeakRemoteManager {
        WeakRemoteManager { inner: Arc::downgrade(&self.inner) }
    }

    pub(crate) async fn add_remote(&self, remote: Remote) -> Arc<RemoteWorker> {
        let uuid = remote.uuid.clone();

        let (worker, state_rx) = RemoteWorker::new(
            uuid.clone(),
            self.downgrade(),
            Arc::clone(&self.inner.authenticator),
            &self.inner.root_token,
            self.inner.protocol_config.clone(),
            self.inner.should_use_compression.clone(),
            self.inner.server_hostname.clone(),
            self.inner.server_ip,
            self.inner.server_fullname.clone(),
            #[cfg(feature = "power_manager")]
            self.power_manager.clone(),
        );

        let worker = Arc::new(worker);
        self.inner.workers.write().await.insert(uuid.clone(), worker.clone());
        self.inner.remotes.write().await.insert(uuid.clone(), remote);

        worker.clone().spawn_loop(state_rx);

        let _ = self.inner.event_tx.send(WarpEvent::RemoteAdded(uuid));

        worker
    }

    #[instrument(skip(self), level = "info", err)]
    pub async fn manual_connection(&self, url: &str) -> Result<(), ManualConnectionError> {
        let sep = url.rfind(":").ok_or(ManualConnectionError::InvalidUrl)?;
        let ip = &url[..sep];
        let port = &url[sep + 1..];

        let reg_channel = Channel::from_shared(format!("http://{}:{}", ip, port))
            .map_err(|_| ManualConnectionError::FailedToRegister)?
            .connect_timeout(self.inner.protocol_config.connect_timeout)
            .connect()
            .await
            .map_err(|_| ManualConnectionError::FailedToRegister)?;

        let mut reg_client =
            crate::proto::warp_registration_client::WarpRegistrationClient::new(reg_channel);

        let remote_service = reg_client
            .register_service(self.registration_message.clone())
            .await
            .map_err(|status| match status.code() {
                Code::Unimplemented => ManualConnectionError::RemoteUnimplemented,
                Code::Internal => ManualConnectionError::RemoteInternal,
                Code::Unavailable => ManualConnectionError::Unavailable,
                _ => ManualConnectionError::FailedToRegister,
            })?
            .into_inner();

        if let Some(remote) = self.remote(&remote_service.service_id).await {
            // Check remote info is up to date
            self.update_remote(&remote.uuid, |remote| {
                if let Ok(ip) = IpAddr::from_str(ip) {
                    remote.ip = ip;
                }
                remote.hostname = remote_service.hostname;
                remote.port = remote_service.port as u16;
                remote.auth_port = remote_service.auth_port as u16;
                remote.service_static = true;
            })
            .await
            .expect("Remote already found");

            return match remote.state {
                RemoteState::Error(_) | RemoteState::Disconnected => self
                    .get_worker(&remote.uuid)
                    .await
                    .ok_or(ManualConnectionError::FailedToConnect(
                        ConnectRemoteError::RemoteWorkerNotFound,
                    ))?
                    .connect()
                    .await
                    .map_err(ManualConnectionError::FailedToConnect),
                RemoteState::Connecting | RemoteState::AwaitingDuplex => {
                    Err(ManualConnectionError::AlreadyConnecting)
                }
                RemoteState::Connected => Err(ManualConnectionError::AlreadyConnected),
            };
        }

        let mut remote = Remote::new(
            remote_service.service_id.clone(),
            IpAddr::from_str(ip).map_err(|_| ManualConnectionError::InvalidUrl)?,
            remote_service.port as u16,
            remote_service.auth_port as u16,
            remote_service.service_id,
            remote_service.hostname.clone(),
        );

        remote.service_static = true;

        self.add_remote(remote)
            .await
            .connect()
            .await
            .map_err(ManualConnectionError::FailedToConnect)
    }
}

impl RemoteManagerInner {
    pub(crate) async fn update_remote(
        &self,
        uuid: &str,
        f: impl FnOnce(&mut Remote),
    ) -> Result<(), UpdateError> {
        if let Some(remote) = self.remotes.write().await.get_mut(uuid) {
            f(remote);
            let _ = self.event_tx.send(WarpEvent::RemoteUpdated(uuid.to_string()));
            Ok(())
        } else {
            Err(UpdateError::NotFound)
        }
    }

    pub(crate) async fn update_remote_async<F>(&self, uuid: &str, f: F) -> Result<(), UpdateError>
    where
        F: AsyncFnOnce(&mut Remote),
    {
        if let Some(remote) = self.remotes.write().await.get_mut(uuid) {
            let future = f(remote);
            future.await;
            let _ = self.event_tx.send(WarpEvent::RemoteUpdated(uuid.to_string()));
            Ok(())
        } else {
            Err(UpdateError::NotFound)
        }
    }

    pub(crate) async fn add_transfer(
        &self,
        remote_uuid: &str,
        transfer: Transfer,
    ) -> Result<(), UpdateError> {
        let transfer_uuid = transfer.uuid.clone();
        if let Some(remote) = self.remotes.write().await.get_mut(remote_uuid) {
            remote.transfers.push(transfer);
            let _ = self
                .event_tx
                .send(WarpEvent::TransferAdded(remote_uuid.to_string(), transfer_uuid));
            Ok(())
        } else {
            Err(UpdateError::NotFound)
        }
    }

    pub(crate) async fn update_transfer(
        &self,
        remote_uuid: &str,
        transfer_uuid: &str,
        f: impl FnOnce(&mut Transfer),
    ) -> Result<(), UpdateError> {
        if let Some(remote) = self.remotes.write().await.get_mut(remote_uuid)
            && let Some(transfer) = remote.transfers.iter_mut().find(|t| t.uuid == transfer_uuid)
        {
            let old_state = transfer.state.clone();
            let old_compression = transfer.use_compression;

            f(transfer);

            if self.event_tx.len() < 128
                || std::mem::discriminant(&transfer.state) != std::mem::discriminant(&old_state)
                || transfer.use_compression != old_compression
            {
                let _ = self.event_tx.send(WarpEvent::TransferUpdated(
                    remote_uuid.to_string(),
                    transfer_uuid.to_string(),
                ));
            }

            return Ok(());
        }
        Err(UpdateError::NotFound)
    }

    pub async fn remove_transfer(
        &self,
        remote_uuid: &str,
        transfer_uuid: &str,
    ) -> Result<(), UpdateError> {
        if let Some(remote) = self.remotes.write().await.get_mut(remote_uuid)
            && let Some(pos) = remote.transfers.iter().position(|t| t.uuid == transfer_uuid)
        {
            remote.transfers.remove(pos);
            let _ = self.event_tx.send(WarpEvent::TransferRemoved(
                remote_uuid.to_string(),
                transfer_uuid.to_string(),
            ));
            return Ok(());
        }
        Err(UpdateError::NotFound)
    }

    #[cfg(feature = "messaging")]
    pub(crate) async fn add_message(
        &self,
        remote_uuid: &str,
        message: Message,
    ) -> Result<(), UpdateError> {
        let message_uuid = message.uuid.clone();
        if let Some(remote) = self.remotes.write().await.get_mut(remote_uuid) {
            remote.messages.push(message);
            let _ =
                self.event_tx.send(WarpEvent::MessageAdded(remote_uuid.to_string(), message_uuid));
            Ok(())
        } else {
            Err(UpdateError::NotFound)
        }
    }

    #[cfg(feature = "messaging")]
    pub async fn remove_message(
        &self,
        remote_uuid: &str,
        message_uuid: &str,
    ) -> Result<(), UpdateError> {
        if let Some(remote) = self.remotes.write().await.get_mut(remote_uuid)
            && let Some(pos) = remote.messages.iter().position(|m| m.uuid == message_uuid)
        {
            remote.messages.remove(pos);
            let _ = self
                .event_tx
                .send(WarpEvent::MessageRemoved(remote_uuid.to_string(), message_uuid.to_string()));
            return Ok(());
        }
        Err(UpdateError::NotFound)
    }

    pub fn subscribe(&self) -> broadcast::Receiver<WarpEvent> {
        self.event_tx.subscribe()
    }

    pub async fn get_worker(&self, uuid: &str) -> Option<Arc<RemoteWorker>> {
        self.workers.read().await.get(uuid).cloned()
    }

    pub async fn remote(&self, uuid: &str) -> Option<Remote> {
        self.remotes.read().await.get(uuid).cloned()
    }

    pub async fn remotes(&self) -> Vec<Remote> {
        self.remotes.read().await.values().cloned().collect()
    }

    pub async fn transfer(&self, remote_uuid: &str, transfer_uuid: &str) -> Option<Transfer> {
        self.remotes
            .read()
            .await
            .get(remote_uuid)
            .and_then(|r| r.transfers.iter().find(|t| t.uuid == transfer_uuid).cloned())
    }

    pub async fn transfer_by_protocol_id(&self, remote_uuid: &str, id: u64) -> Option<Transfer> {
        self.remotes
            .read()
            .await
            .get(remote_uuid)
            .and_then(|r| r.transfers.iter().find(|t| t.protocol_id == id).cloned())
    }

    pub async fn transfers(&self, remote_uuid: &str) -> Option<Vec<Transfer>> {
        self.remotes.read().await.get(remote_uuid).map(|r| r.transfers.clone())
    }

    #[cfg(feature = "messaging")]
    pub async fn message(&self, remote_uuid: &str, message_uuid: &str) -> Option<Message> {
        self.remotes
            .read()
            .await
            .get(remote_uuid)
            .and_then(|r| r.messages.iter().find(|m| m.uuid == message_uuid).cloned())
    }

    #[cfg(feature = "messaging")]
    pub async fn messages(&self, remote_uuid: &str) -> Option<Vec<Message>> {
        self.remotes.read().await.get(remote_uuid).map(|r| r.messages.clone())
    }
}
