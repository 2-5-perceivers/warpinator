use std::time::Duration;

use tokio_stream::wrappers::ReceiverStream;
use tonic::{Request, Response, Status};
use tracing::{Instrument, field, instrument};

use crate::config::protocol::ProtocolConfig;
use crate::config::user::UserConfig;
use crate::proto::warp_server::Warp;
use crate::proto::{
    FileChunk, HaveDuplex, LookupName, OpInfo, RemoteMachineAvatar, RemoteMachineInfo, StopInfo,
    TextMessage, TransferOpRequest, VoidType,
};
use crate::server::remote_manager::{RemoteManager, WarpEvent};
use crate::server::transfers::transfer_sender;
#[cfg(feature = "messaging")]
use crate::types::message::Message;
use crate::types::remote::RemoteState;
use crate::types::transfer::{Transfer, TransferError, TransferKind, TransferState};

const AVATAR_CHUNK_SIZE: usize = 1024 * 64; // 64KB

#[derive(Debug)]
pub struct WarpServer {
    user_config: UserConfig,
    protocol_config: ProtocolConfig,
    remote_manager: RemoteManager,
    #[cfg(feature = "power_manager")]
    power_manager: std::sync::Arc<dyn crate::server::power_manager::PowerManager>,
}

impl WarpServer {
    pub fn new(
        user_config: UserConfig,
        protocol_config: ProtocolConfig,
        remote_manager: RemoteManager,
        #[cfg(feature = "power_manager")] power_manager: std::sync::Arc<
            dyn crate::server::power_manager::PowerManager,
        >,
    ) -> Self {
        Self {
            user_config,
            protocol_config,
            remote_manager,
            #[cfg(feature = "power_manager")]
            power_manager,
        }
    }
}

#[tonic::async_trait]
impl Warp for WarpServer {
    type GetRemoteMachineAvatarStream = ReceiverStream<Result<RemoteMachineAvatar, Status>>;
    type StartTransferStream = ReceiverStream<Result<FileChunk, Status>>;

    #[instrument(
        skip_all,
        fields(
            id = field::Empty,
            name = field::Empty,
        ),
        level = "debug",
        err(level = "warn")
    )]
    async fn waiting_for_duplex(
        &self,
        request: Request<LookupName>,
    ) -> Result<Response<HaveDuplex>, Status> {
        let req = request.into_inner();
        let id = req.id.as_str();

        let span = tracing::Span::current();
        span.record("id", id);
        span.record("name", req.readable_name.as_str());

        let mut events = self.remote_manager.subscribe();
        let remote = self.remote_manager.remote(id).await;

        if let Some(remote) = remote {
            match remote.state {
                RemoteState::Error(_) | RemoteState::Disconnected => {
                    tracing::debug!("Remote is waiting for duplex connection");
                    let worker = self.remote_manager.get_worker(id).await;
                    if let Some(worker) = worker {
                        tokio::spawn({
                            async move {
                                let _ = worker.connect().await;
                            }
                        });
                    }
                }
                RemoteState::AwaitingDuplex | RemoteState::Connected => {
                    return Ok(Response::new(HaveDuplex { response: true }));
                }
                _ => {}
            }
        }

        let duplex_timeout = Duration::from_secs(10);
        let deadline = tokio::time::Instant::now() + duplex_timeout;

        loop {
            tokio::select! {
                _ = tokio::time::sleep_until(deadline) => {
                    return Ok(Response::new(HaveDuplex { response: false }));
                }
                Ok(event) = events.recv() => {
                    match event {
                        WarpEvent::RemoteAdded(uuid) | WarpEvent::RemoteUpdated(uuid) if uuid == id => {
                            // remote appeared or updated, check state
                            let remote = self.remote_manager.remote(&uuid).await;
                            if let Some(r) = remote && matches!(r.state, RemoteState::AwaitingDuplex | RemoteState::Connected) {
                                    return Ok(Response::new(HaveDuplex { response: true }));

                            }
                        }
                        _ => {}
                    }
                }
            }
        }
    }

    #[instrument(skip_all, level = "debug", err(level = "warn"))]
    async fn get_remote_machine_info(
        &self,
        _request: Request<LookupName>,
    ) -> Result<Response<RemoteMachineInfo>, Status> {
        Ok(Response::new(RemoteMachineInfo {
            display_name: self.user_config.display_name.read().await.clone(),
            user_name: self.user_config.username.clone(),
            feature_flags: self.protocol_config.features.bits(),
        }))
    }

    #[instrument(skip_all, level = "debug", err(level = "warn"))]
    async fn get_remote_machine_avatar(
        &self,
        _request: Request<LookupName>,
    ) -> Result<Response<Self::GetRemoteMachineAvatarStream>, Status> {
        let (tx, rx) = tokio::sync::mpsc::channel(4);

        let picture = self.user_config.picture.clone();

        if picture.read().await.is_none() {
            return Ok(Response::new(ReceiverStream::new(rx)));
        }

        tokio::spawn(
            async move {
                if let Some(bytes) = picture.read().await.clone() {
                    for chunk in bytes.chunks(AVATAR_CHUNK_SIZE) {
                        if tx
                            .send(Ok(RemoteMachineAvatar { avatar_chunk: chunk.to_vec() }))
                            .await
                            .is_err()
                        {
                            break; // receiver dropped, client disconnected
                        }
                    }
                }
            }
            .instrument(tracing::debug_span!("send_avatar")),
        );

        Ok(Response::new(ReceiverStream::new(rx)))
    }

    #[instrument(
        skip_all,
        fields(
            id = field::Empty,
        ),
        level = "debug",
        err(level = "warn")
    )]
    async fn process_transfer_op_request(
        &self,
        request: Request<TransferOpRequest>,
    ) -> Result<Response<VoidType>, Status> {
        let req = request.into_inner();
        let ident =
            req.info.as_ref().ok_or(Status::invalid_argument("Missing OpInfo"))?.ident.to_string();

        let span = tracing::Span::current();
        span.record("id", ident.as_str());

        let mut transfer = Transfer::from(req);
        transfer.use_compression = transfer.use_compression && self.user_config.use_compression();
        self.remote_manager
            .add_transfer(ident.as_str(), transfer)
            .await
            .map_err(|e| Status::internal(format!("Failed to add transfer op request: {e:?}")))?;

        Ok(Response::new(VoidType::default()))
    }

    #[instrument(skip_all, level = "debug", err(level = "warn"))]
    async fn start_transfer(
        &self,
        request: Request<OpInfo>,
    ) -> Result<Response<Self::StartTransferStream>, Status> {
        let req = request.into_inner();

        let transfer = self
            .remote_manager
            .transfer_by_protocol_id(&req.ident, req.timestamp)
            .await
            .ok_or_else(|| Status::not_found("Transfer not found"))?;

        let (source_paths, cancellation_token) = match &transfer.kind {
            TransferKind::Outgoing { source_paths, cancellation_token } => {
                (source_paths.clone(), cancellation_token.clone())
            }
            TransferKind::Incoming { .. } => {
                return Err(Status::invalid_argument("Cannot start an incoming transfer"));
            }
        };

        let use_compression = self.user_config.use_compression() && req.use_compression;

        self.remote_manager
            .update_transfer(&transfer.remote_uuid, &transfer.uuid, |t| {
                t.state = TransferState::InProgress;
                t.use_compression = use_compression;
            })
            .await
            .map_err(|_| Status::internal("Failed to update transfer state"))?;

        let (tx, rx) = tokio::sync::mpsc::channel(4);

        tokio::spawn(transfer_sender::send_stream(
            self.remote_manager.clone(),
            transfer.remote_uuid.clone(),
            transfer.uuid.clone(),
            source_paths,
            tx,
            cancellation_token,
            use_compression,
            #[cfg(feature = "power_manager")]
            self.power_manager.clone(),
        ));

        Ok(Response::new(ReceiverStream::new(rx)))
    }

    #[instrument(skip_all, level = "debug", err(level = "warn"))]
    async fn pause_transfer_op(&self, _: Request<OpInfo>) -> Result<Response<VoidType>, Status> {
        Err(Status::unimplemented("PauseTransferOp is not implemented"))
    }

    #[instrument(skip_all, level = "debug", err(level = "warn"))]
    async fn stop_transfer(
        &self,
        request: Request<StopInfo>,
    ) -> Result<Response<VoidType>, Status> {
        let req = request.into_inner();
        let info = req.info.ok_or(Status::invalid_argument("Missing OpInfo"))?;

        let transfer = self
            .remote_manager
            .transfer_by_protocol_id(&info.ident, info.timestamp)
            .await
            .ok_or_else(|| Status::not_found("Transfer not found"))?;

        let new_state = match &transfer.kind {
            TransferKind::Outgoing { cancellation_token, .. } => {
                cancellation_token.cancel();
                if req.error {
                    TransferState::Failed(TransferError::RemoteError)
                } else {
                    TransferState::Stopped
                }
            }
            TransferKind::Incoming { .. } => {
                if req.error {
                    TransferState::Failed(TransferError::RemoteError)
                } else {
                    TransferState::Canceled
                }
            }
        };

        self.remote_manager
            .update_transfer(&transfer.remote_uuid, &transfer.uuid, |t| {
                t.state = new_state;
            })
            .await
            .ok();

        Ok(Response::new(VoidType::default()))
    }

    #[instrument(skip_all, level = "debug", err(level = "warn"))]
    async fn cancel_transfer_op_request(
        &self,
        request: Request<OpInfo>,
    ) -> Result<Response<VoidType>, Status> {
        let req = request.into_inner();

        let transfer = self
            .remote_manager
            .transfer_by_protocol_id(&req.ident, req.timestamp)
            .await
            .ok_or_else(|| Status::not_found("Transfer not found"))?;

        if matches!(transfer.state, TransferState::WaitingPermission) {
            let new_state = match transfer.kind {
                TransferKind::Outgoing { .. } => TransferState::Denied,
                TransferKind::Incoming { .. } => TransferState::Canceled,
            };
            self.remote_manager
                .update_transfer(&transfer.remote_uuid, &transfer.uuid, |t| {
                    t.state = new_state;
                })
                .await
                .ok();
        } else {
            return Err(Status::failed_precondition(
                "Can only cancel transfers that are waiting for permission",
            ));
        }

        Ok(Response::new(VoidType::default()))
    }

    #[instrument(skip_all, level = "debug", err(level = "warn"))]
    async fn send_text_message(
        &self,
        #[cfg(feature = "messaging")] request: Request<TextMessage>,
        #[cfg(not(feature = "messaging"))] _: Request<TextMessage>,
    ) -> Result<Response<VoidType>, Status> {
        #[cfg(feature = "messaging")]
        {
            if !self
                .protocol_config
                .features
                .contains(crate::config::features::ProtocolFeatures::MESSAGE_SUPPORT)
            {
                return Err(Status::unimplemented("Messaging feature is disabled"));
            }

            let req = request.into_inner();
            let message = Message::from(&req);
            self.remote_manager
                .add_message(req.ident.as_str(), message)
                .await
                .map_err(|e| Status::internal(format!("Failed to add message: {e:?}")))?;
            Ok(Response::new(VoidType::default()))
        }
        #[cfg(not(feature = "messaging"))]
        {
            Err(Status::unimplemented("Messaging feature is disabled"))
        }
    }

    #[instrument(skip_all, level = "debug", err(level = "warn"))]
    async fn ping(&self, _: Request<LookupName>) -> Result<Response<VoidType>, Status> {
        Ok(Response::new(VoidType::default()))
    }
}
