use std::fmt::Debug;
use std::net::IpAddr;
use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::time::Duration;

use base64::Engine;
use base64::engine::general_purpose::STANDARD;
use thiserror::Error;
use tokio::sync::{RwLock, watch};
use tokio::time::error::Elapsed;
use tokio::time::sleep;
use tokio_util::sync::CancellationToken;
use tonic::Status;
use tonic::transport::{Certificate, Channel, ClientTlsConfig};
use tracing::instrument;

use crate::config::features::ProtocolFeatures;
use crate::config::protocol::ProtocolConfig;
use crate::proto::warp_client::WarpClient;
use crate::proto::{LookupName, OpInfo, StopInfo};
use crate::remote_manager::{RemoteManagerInner, UpdateError, WeakRemoteManager};
use crate::server::authenticator::{Authenticator, CertUnboxError};
use crate::server::transfers::transfer_receiver;
#[cfg(feature = "messaging")]
use crate::types::message::{Direction, Message};
use crate::types::remote::{RemoteConnectionError, RemoteState};
use crate::types::transfer::{Transfer, TransferError, TransferKind, TransferState};

#[derive(Error, Debug)]
pub enum ReceiveCertError {
    #[error("Remote worker error")]
    RemoteWorkerError(#[from] RemoteWorkerError),
    #[error("Local remote not found")]
    NoRemote,
    #[error("Invalid connection Uri")]
    InvalidUri(#[from] http::uri::InvalidUri),
    #[error("Request of certificate failed: {0}")]
    RequestFailed(#[from] Status),
    #[error("Failed to decode certificate: {0}")]
    DecodingCertificateFailed(#[from] base64::DecodeError),
    #[error("Received certificate has invalid format")]
    MalformedCertificate,
    #[error("Group code mismatch")]
    WrongGroupCode,
    #[error("Remote registration service is offline")]
    Offline,
}

#[cfg_attr(feature = "serde", derive(serde::Serialize))]
#[cfg_attr(feature = "serde", serde(rename_all = "snake_case"))]
#[derive(Error, Debug)]
pub enum ConnectRemoteError {
    #[error("Failed to receive certificate: {0}")]
    CertificateError(#[cfg_attr(feature = "serde", serde(skip))] ReceiveCertError),
    #[error("Failed to build TLS channel: {0}")]
    TlsError(#[cfg_attr(feature = "serde", serde(skip))] Box<dyn std::error::Error + Send + Sync>),
    #[error("Ping failed: {0}")]
    PingError(#[cfg_attr(feature = "serde", serde(skip))] Box<dyn std::error::Error + Send + Sync>),
    #[error("Duplex failed: {0}")]
    DuplexError(
        #[cfg_attr(feature = "serde", serde(skip))] Box<dyn std::error::Error + Send + Sync>,
    ),
    #[error("Remote worker not found")]
    RemoteWorkerNotFound,
    #[error("Remote worker error")]
    RemoteWorkerError(
        #[cfg_attr(feature = "serde", serde(skip))]
        #[from]
        RemoteWorkerError,
    ),
}

#[derive(Error, Debug)]
pub enum RemoteWorkerError {
    #[error("Manager was destroyed")]
    NoManager,
    #[error("Corresponding remote was not found")]
    RemoteNotFound,
    #[error("Transfer was not found")]
    TransferNotFound,
    #[error("gRPC client was not initialized yet")]
    NoClient,
    #[error("gRPC call failed: {0}")]
    RemoteFailed(#[from] Status),
    #[error("Operation failed: {0}")]
    OperationFailed(
        #[from]
        #[source]
        Box<dyn std::error::Error + Send + Sync>,
    ),
    #[error("Operation timed out: {0}")]
    OperationTimedOut(#[from] Elapsed),
    #[error("Operation is not permitted: {0}")]
    IllegalOperation(String),
    #[error(transparent)]
    UpdateFailed(#[from] UpdateError),
}

type WarpChannel = WarpClient<Channel>;

#[derive(Debug)]
pub struct RemoteWorker {
    uuid: String,
    remote_manager: WeakRemoteManager,
    authenticator: Arc<Authenticator>,
    channel: RwLock<Option<Channel>>,
    client: RwLock<Option<WarpChannel>>,
    cancellation_token: CancellationToken,
    state_tx: watch::Sender<RemoteState>,
    protocol_config: ProtocolConfig,
    server_hostname: String,
    server_ip: IpAddr,
    server_fullname: String,
    should_use_compression: Arc<AtomicBool>,
    #[cfg(feature = "power_manager")]
    power_manager: Arc<dyn crate::server::power_manager::PowerManager>,
}

impl RemoteWorker {
    pub(crate) fn new(
        uuid: String,
        remote_manager: WeakRemoteManager,
        authenticator: Arc<Authenticator>,
        root_token: &CancellationToken,
        protocol_config: ProtocolConfig,
        should_use_compression: Arc<AtomicBool>,
        server_hostname: String,
        server_ip: IpAddr,
        server_fullname: String,
        #[cfg(feature = "power_manager")] power_manager: Arc<
            dyn crate::server::power_manager::PowerManager,
        >,
    ) -> (Self, watch::Receiver<RemoteState>) {
        let (state_tx, state_rx) = watch::channel(RemoteState::Disconnected);

        let worker = Self {
            uuid,
            remote_manager,
            authenticator,
            channel: RwLock::new(None),
            client: RwLock::new(None),
            cancellation_token: root_token.child_token(),
            state_tx,
            protocol_config,
            server_hostname,
            server_ip,
            server_fullname,
            should_use_compression,
            #[cfg(feature = "power_manager")]
            power_manager,
        };

        (worker, state_rx)
    }

    /// Spawns the state loop task. Call once after construction.
    #[instrument(skip_all, level = "debug")]
    pub(crate) fn spawn_loop(self: Arc<Self>, mut state_rx: watch::Receiver<RemoteState>) {
        async fn wait_then_reconnect(
            remote: &RemoteWorker,
            state_rx: &mut watch::Receiver<RemoteState>,
        ) -> bool {
            tokio::select! {
                _ = remote.cancellation_token.cancelled() => {
                     let diconnect = remote.disconnect().await;
                                if diconnect.is_err() {
                                    tracing::debug!(uuid = %remote.uuid, "Failed to disconnect");
                                }
                    false
                },
                _ = sleep(remote.protocol_config.reconnect_interval) => {
                    let _ = remote.connect().await;
                    true
                }
                _ = state_rx.changed() => true,
            }
        }
        tokio::spawn(async move {
            loop {
                let state = state_rx.borrow().clone();

                match state {
                    RemoteState::Connected => {
                        tokio::select! {
                            _ = self.cancellation_token.cancelled() => {
                                 let diconnect = self.disconnect().await;
                                if diconnect.is_err() {
                                    tracing::debug!(uuid = %self.uuid, "Failed to disconnect");
                                }
                                break;
                            }
                            _ = sleep(self.protocol_config.ping_interval) => {
                                if let Err(e) = self.ping().await {
                                    tracing::debug!(uuid = %self.uuid, "Ping failed: {}", e);
                                    let _ = self.set_state(RemoteState::Disconnected).await;
                                    self.clear_channel().await;
                                }
                            }
                            _ = state_rx.changed() => {}
                        }
                    }
                    RemoteState::Disconnected => {
                        if !wait_then_reconnect(&self, &mut state_rx).await {
                            break;
                        }
                    }
                    RemoteState::Error(ref e) => {
                        if matches!(e, RemoteConnectionError::GroupCodeMismatch) {
                            // Group code mismatch is not retryable, stay in
                            // error state until manual intervention
                            break;
                        }
                        if !wait_then_reconnect(&self, &mut state_rx).await {
                            break;
                        }
                    }
                    _ => {
                        tokio::select! {
                            _ = self.cancellation_token.cancelled() => {
                                let diconnect = self.disconnect().await;
                                if diconnect.is_err() {
                                    tracing::debug!(uuid = %self.uuid, "Failed to disconnect");
                                }
                                break;
                            },
                            _ = state_rx.changed() => {}
                        }
                    }
                }
            }
        });
    }

    #[instrument(skip(self), fields(uuid = self.uuid), err(level = "warn"))]
    pub async fn connect(&self) -> Result<(), ConnectRemoteError> {
        #[cfg(feature = "power_manager")]
        let _wake_lock =
            crate::server::power_manager::WakeLockGuard::new(self.power_manager.clone());

        self.set_state(RemoteState::Connecting).await?;

        let cert_pem = match self.receive_certificate().await {
            Ok(cert) => cert,
            Err(e) => {
                let state = match e {
                    ReceiveCertError::WrongGroupCode => {
                        RemoteState::Error(RemoteConnectionError::GroupCodeMismatch)
                    }
                    ReceiveCertError::Offline => RemoteState::Disconnected,
                    _ => RemoteState::Error(RemoteConnectionError::NoCertificate),
                };

                self.set_state(state).await?;
                return Err(ConnectRemoteError::CertificateError(e));
            }
        };

        let channel = match self.build_channel(&cert_pem).await {
            Ok(ch) => ch,
            Err(e) => {
                self.set_state(RemoteState::Error(RemoteConnectionError::SslError)).await?;
                return Err(ConnectRemoteError::TlsError(Box::new(e)));
            }
        };

        let client = WarpClient::new(channel.clone());
        *self.channel.write().await = Some(channel);
        *self.client.write().await = Some(client);

        if let Err(e) = self.ping().await {
            self.clear_channel().await;
            self.set_state(RemoteState::Error(RemoteConnectionError::SslError)).await?;
            return Err(ConnectRemoteError::PingError(Box::new(e)));
        }

        self.set_state(RemoteState::AwaitingDuplex).await?;
        if let Err(e) = self.wait_for_duplex().await {
            self.clear_channel().await;
            self.set_state(RemoteState::Error(RemoteConnectionError::DuplexError)).await?;
            return Err(ConnectRemoteError::DuplexError(Box::new(e)));
        }

        self.set_state(RemoteState::Connected).await?;

        let _ = self.fetch_machine_info().await;
        let _ = self.fetch_avatar().await;

        tracing::info!("Connection established");
        Ok(())
    }

    pub(crate) async fn disconnect(&self) -> Result<(), RemoteWorkerError> {
        self.clear_channel().await;
        self.set_state(RemoteState::Disconnected).await
    }

    pub fn subscribe_state(&self) -> watch::Receiver<RemoteState> {
        self.state_tx.subscribe()
    }

    #[instrument(skip_all, err(level = "debug"))]
    async fn receive_certificate(&self) -> Result<Vec<u8>, ReceiveCertError> {
        let remote = self.manager()?.remote(&self.uuid).await.ok_or(ReceiveCertError::NoRemote)?;

        let addr = match remote.ip {
            IpAddr::V4(ip) => {
                format!("http://{}:{}", ip.to_string(), remote.auth_port)
            }
            IpAddr::V6(ipv6) => {
                format!("http://[{}]:{}", ipv6.to_string(), remote.auth_port)
            }
        };
        let reg_channel = Channel::from_shared(addr)?
            .connect_timeout(self.protocol_config.connect_timeout)
            .connect()
            .await
            .map_err(|_| ReceiveCertError::Offline)?;

        let mut reg_client =
            crate::proto::warp_registration_client::WarpRegistrationClient::new(reg_channel);

        let response = reg_client
            .request_certificate(crate::proto::RegRequest {
                hostname: self.server_hostname.to_string(),
                ip: self.server_ip.to_string(),
                ipv6: "".to_string(),
                // TODO: add ipv6 support
            })
            .await?
            .into_inner();

        let cleaned_cert = response.locked_cert.replace(&['\n', '\r'][..], "");

        let decoded = STANDARD.decode(cleaned_cert)?;
        let cert_pem = self.authenticator.unbox_cert(&decoded).map_err(|e| match e {
            CertUnboxError::BoxTooShort => ReceiveCertError::MalformedCertificate,
            CertUnboxError::DecryptionFailed => ReceiveCertError::WrongGroupCode,
        })?;

        self.manager()?
            .update_remote(&self.uuid, |r| {
                r.cert_pem = Some(cert_pem.clone());
            })
            .await
            .map_err(|_| ReceiveCertError::NoRemote)?;

        Ok(cert_pem)
    }

    #[instrument(skip_all, err(level = "debug"))]
    async fn build_channel(&self, cert_pem: &[u8]) -> Result<Channel, RemoteWorkerError> {
        let remote =
            self.manager()?.remote(&self.uuid).await.ok_or(RemoteWorkerError::RemoteNotFound)?;

        let cert = Certificate::from_pem(cert_pem);
        let tls = ClientTlsConfig::new().ca_certificate(cert).domain_name(remote.ip.to_string());

        let addr = match remote.ip {
            IpAddr::V4(ip) => {
                format!("https://{}:{}", ip.to_string(), remote.port)
            }
            IpAddr::V6(ipv6) => {
                format!("https://[{}]:{}", ipv6.to_string(), remote.port)
            }
        };
        let channel = Channel::from_shared(addr)
            .map_err(Box::from)?
            .tls_config(tls)
            .map_err(Box::from)?
            .connect_timeout(self.protocol_config.connect_timeout)
            .connect()
            .await
            .map_err(Box::from)?;

        Ok(channel)
    }

    #[instrument(skip_all, err(level = "debug"))]
    async fn ping(&self) -> Result<(), RemoteWorkerError> {
        let client = self.client.read().await;
        let client = client.as_ref().ok_or(RemoteWorkerError::NoClient)?;
        let mut client = client.clone();

        tokio::time::timeout(
            self.protocol_config.ping_timeout,
            client.ping(LookupName {
                id: self.uuid.clone(),
                readable_name: self.server_hostname.to_string(),
            }),
        )
        .await??;

        Ok(())
    }

    #[instrument(skip_all, err(level = "debug"))]
    async fn wait_for_duplex(&self) -> Result<(), RemoteWorkerError> {
        let client = self.client.read().await;
        let client = client.as_ref().ok_or(RemoteWorkerError::NoClient)?;
        let mut client = client.clone();

        let response = tokio::time::timeout(
            Duration::from_secs(60),
            client.waiting_for_duplex(LookupName {
                id: self.server_fullname.clone(),
                readable_name: self.server_hostname.to_string(),
            }),
        )
        .await??;

        if !response.into_inner().response {
            return Err(RemoteWorkerError::OperationFailed("Duplex not established".into()));
        }

        Ok(())
    }

    #[instrument(skip_all, err(level = "debug"))]
    async fn fetch_machine_info(&self) -> Result<(), RemoteWorkerError> {
        let client = self.client.read().await;
        let client = client.as_ref().ok_or(RemoteWorkerError::NoClient)?;
        let mut client = client.clone();

        let info = client.get_remote_machine_info(LookupName::default()).await?.into_inner();

        self.manager()?
            .update_remote(&self.uuid, |r| {
                r.display_name = info.display_name.clone();
                r.username = info.user_name.clone();
                r.features = ProtocolFeatures::from_bits_truncate(info.feature_flags);
            })
            .await?;

        Ok(())
    }

    #[instrument(skip_all, err(level = "debug"))]
    async fn fetch_avatar(&self) -> Result<(), RemoteWorkerError> {
        let client = self.client.read().await;
        let client = client.as_ref().ok_or(RemoteWorkerError::NoClient)?;
        let mut client = client.clone();

        let mut stream =
            client.get_remote_machine_avatar(LookupName::default()).await?.into_inner();

        let mut bytes = Vec::new();
        while let Some(chunk) = stream.message().await? {
            bytes.extend_from_slice(&chunk.avatar_chunk);
        }

        let picture;
        if !bytes.is_empty() {
            picture = Some(bytes);
        } else {
            picture = None;
        }

        self.manager()?
            .update_remote_async(&self.uuid, async |r| {
                match (r.picture.as_ref(), picture) {
                    (Some(arc), Some(pic)) => *arc.write().await = pic,
                    (None, Some(pic)) => r.picture = Some(Arc::new(RwLock::new(pic))),
                    _ => r.picture = None,
                }
                r.picture_version = r.picture_version.wrapping_add(1);
            })
            .await?;

        Ok(())
    }

    #[instrument(skip_all, err(level = "warn"))]
    pub async fn send_transfer_request(
        &self,
        source_paths: Vec<PathBuf>,
    ) -> Result<(), RemoteWorkerError> {
        let client = self.client.read().await;
        let client = client.as_ref().ok_or(RemoteWorkerError::NoClient)?;
        let mut client = client.clone();

        let transfer_token = self.cancellation_token.child_token();
        let use_compression =
            self.should_use_compression.load(std::sync::atomic::Ordering::Relaxed);

        let mut transfer =
            Transfer::new_outgoing(self.uuid.clone(), source_paths.clone(), transfer_token).await;

        // Add transfer in initializing state before processing paths, so it
        // appears in UI immediately
        self.manager()?.add_transfer(&self.uuid, transfer.clone()).await?;

        let processing_result = transfer.process_paths(&source_paths).await;

        match processing_result {
            Ok(_) => {
                client
                    .process_transfer_op_request(
                        transfer.as_proto(self.server_fullname.as_str(), use_compression),
                    )
                    .await?;
                self.manager()?
                    .update_transfer(&self.uuid, &transfer.uuid, |t| {
                        t.total_bytes = transfer.total_bytes;
                        t.file_count = transfer.file_count;
                        t.entry_names = transfer.entry_names.clone();
                        t.single_name = transfer.single_name.clone();
                        t.single_mime_type = transfer.single_mime_type.clone();
                        t.state = TransferState::WaitingPermission;
                    })
                    .await?;
                Ok(())
            }
            Err(e) => {
                self.manager()?
                    .update_transfer(&self.uuid, &transfer.uuid, |t| {
                        t.state = TransferState::Failed(TransferError::FailedToProcessFiles);
                    })
                    .await?;
                Err(RemoteWorkerError::OperationFailed(Box::new(e)))
            }
        }
    }

    #[cfg(feature = "messaging")]
    #[instrument(skip_all, err(level = "warn"))]
    pub async fn send_message(&self, message: &str) -> Result<(), RemoteWorkerError> {
        let client = self.client.read().await;
        let client = client.as_ref().ok_or(RemoteWorkerError::NoClient)?;
        let mut client = client.clone();

        let message = Message::new(self.uuid.clone(), Direction::Sent, message.to_string());

        client.send_text_message(message.as_proto(self.server_fullname.as_str())).await?;

        self.manager()?.add_message(&self.uuid, message).await?;

        Ok(())
    }

    #[instrument(skip_all, err(level = "warn"))]
    pub async fn accept_transfer<P: AsRef<Path>>(
        &self,
        transfer_uuid: &str,
        destination: P,
    ) -> Result<(), RemoteWorkerError> {
        let client = self.client.read().await;
        let client = client.as_ref().ok_or(RemoteWorkerError::NoClient)?;
        let mut client = client.clone();

        let transfer = self
            .manager()?
            .transfer(self.uuid.as_str(), transfer_uuid)
            .await
            .ok_or(RemoteWorkerError::TransferNotFound)?;

        if let TransferKind::Outgoing { .. } = &transfer.kind {
            return Err(RemoteWorkerError::IllegalOperation(
                "Cannot accept an outgoing transfer".into(),
            ));
        }

        let use_compression = transfer.use_compression
            && self.should_use_compression.load(std::sync::atomic::Ordering::Relaxed);

        let stream = client
            .start_transfer(OpInfo {
                ident: self.server_fullname.clone(),
                timestamp: transfer.protocol_id,
                use_compression,
                readable_name: String::default(),
            })
            .await?
            .into_inner();

        let destination = destination.as_ref().to_path_buf();

        self.manager()?
            .update_transfer(&self.uuid, transfer_uuid, |t| {
                t.state = TransferState::InProgress;
                t.use_compression = use_compression;
                t.kind = TransferKind::Incoming { destination: destination.clone() };
            })
            .await?;

        tokio::spawn(transfer_receiver::receive_stream(
            self.manager()?.clone(),
            self.uuid.clone(),
            transfer_uuid.to_string(),
            stream,
            destination,
            self.cancellation_token.child_token(),
            use_compression,
            #[cfg(feature = "power_manager")]
            self.power_manager.clone(),
        ));

        Ok(())
    }

    /// Stop an in-progress transfer. Don't use on transfers that are not in
    /// progress
    #[instrument(skip_all, err(level = "warn"))]
    pub async fn stop_transfer(
        &self,
        transfer_uuid: &str,
        error: bool,
    ) -> Result<(), RemoteWorkerError> {
        let client = self.client.read().await;
        let client = client.as_ref().ok_or(RemoteWorkerError::NoClient)?;
        let mut client = client.clone();

        let transfer = self
            .manager()?
            .transfer(self.uuid.as_str(), transfer_uuid)
            .await
            .ok_or(RemoteWorkerError::TransferNotFound)?;

        if let TransferKind::Outgoing { cancellation_token, .. } = &transfer.kind {
            cancellation_token.cancel();
        }

        client
            .stop_transfer(StopInfo {
                info: Some(OpInfo {
                    ident: self.server_fullname.clone(),
                    timestamp: transfer.protocol_id,
                    readable_name: String::new(),
                    use_compression: false,
                }),
                error,
            })
            .await?;

        self.manager()?
            .update_transfer(&self.uuid, transfer_uuid, |t| {
                t.state = TransferState::Stopped;
            })
            .await?;

        Ok(())
    }

    /// Reject an incoming transfer or cancel an outgoing one. Use only on
    /// transfers waiting for permission
    #[instrument(skip_all, err(level = "warn"))]
    pub async fn cancel_transfer(&self, transfer_uuid: &str) -> Result<(), RemoteWorkerError> {
        let client = self.client.read().await;
        let client = client.as_ref().ok_or(RemoteWorkerError::NoClient)?;
        let mut client = client.clone();

        let transfer = self
            .manager()?
            .transfer(self.uuid.as_str(), transfer_uuid)
            .await
            .ok_or(RemoteWorkerError::TransferNotFound)?;

        let new_state = match transfer.kind {
            TransferKind::Incoming { .. } => TransferState::Denied,
            TransferKind::Outgoing { cancellation_token, .. } => {
                cancellation_token.cancel();
                TransferState::Canceled
            }
        };

        client
            .cancel_transfer_op_request(OpInfo {
                ident: self.server_fullname.clone(),
                timestamp: transfer.protocol_id,
                readable_name: String::new(),
                use_compression: false,
            })
            .await?;

        self.manager()?
            .update_transfer(&self.uuid, transfer_uuid, |t| {
                t.state = new_state;
            })
            .await?;

        Ok(())
    }

    async fn set_state(&self, state: RemoteState) -> Result<(), RemoteWorkerError> {
        let _ = self.state_tx.send(state.clone());
        let _ = self
            .manager()?
            .update_remote(&self.uuid, |r| {
                r.state = state;
            })
            .await;
        Ok(())
    }

    async fn clear_channel(&self) {
        *self.channel.write().await = None;
        *self.client.write().await = None;
    }

    fn manager(&self) -> Result<Arc<RemoteManagerInner>, RemoteWorkerError> {
        Ok(self.remote_manager.upgrade().ok_or(RemoteWorkerError::NoManager)?)
    }
}
