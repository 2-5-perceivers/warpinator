use std::fs::FileTimes;
use std::io::Write;
use std::path::PathBuf;
use std::sync::Arc;

use tokio::io::AsyncWriteExt;
use tokio_util::sync::CancellationToken;
use tonic::Streaming;
use tracing::instrument;

use crate::proto::{FileChunk, FileTime};
use crate::remote_manager::RemoteManagerInner;
use crate::server::transfers::{FileType, MovingAverageCalculator};
use crate::types::transfer::{TransferError, TransferState};

struct ReceiveState {
    current_path: Option<String>,
    current_file: Option<tokio::fs::File>,
    current_file_mtime: Option<FileTime>,
    speed: MovingAverageCalculator,
    last_chunk_time: std::time::Instant,
}

impl ReceiveState {
    fn new() -> Self {
        Self {
            current_path: None,
            current_file: None,
            current_file_mtime: None,
            speed: MovingAverageCalculator::new(30),
            last_chunk_time: std::time::Instant::now(),
        }
    }

    async fn finalize_current_file(&mut self) {
        if let Some(file) = self.current_file.take() {
            if let Some(time) = self.current_file_mtime.take() {
                let mut std_file = file.into_std().await;
                let system_time = std::time::UNIX_EPOCH
                    + std::time::Duration::new(time.mtime, time.mtime_usec * 1000);
                let times = FileTimes::new().set_modified(system_time);
                let _ = tokio::task::spawn_blocking(move || {
                    let _ = std_file.set_times(times);
                    std_file.flush()
                })
                .await;
            } else {
                let mut file = file;
                let _ = file.flush().await;
            }
        }
        self.current_path = None;
    }
}

pub(crate) async fn receive_stream(
    remote_manager: Arc<RemoteManagerInner>,
    remote_uuid: String,
    transfer_uuid: String,
    mut stream: Streaming<FileChunk>,
    destination: PathBuf,
    cancellation_token: CancellationToken,
    use_compression: bool,
    #[cfg(feature = "power_manager")] power_manager: Arc<
        dyn crate::server::power_manager::PowerManager + Send + Sync,
    >,
) {
    #[cfg(feature = "power_manager")]
    let _wake_lock = crate::server::power_manager::WakeLockGuard::new(power_manager);

    let result = receive_stream_inner(
        &remote_manager,
        &remote_uuid,
        &transfer_uuid,
        &mut stream,
        &destination,
        &cancellation_token,
        use_compression,
    )
    .await;

    let final_state = match result {
        Ok(true) => TransferState::Completed,
        Ok(false) => TransferState::Canceled,
        Err(e) => TransferState::Failed(e),
    };

    remote_manager
        .update_transfer(&remote_uuid, &transfer_uuid, |t| {
            if !matches!(t.state, TransferState::Stopped | TransferState::Canceled) {
                t.state = final_state;
            }
            t.bytes_per_second = 0;
        })
        .await
        .ok();
}

/// Returns Ok(true) it was completed, Ok(false) if it was canceled, and
/// Err(TransferError) it failed
#[instrument(
    skip(remote_manager, stream, cancellation_token),
    level = "debug",
    err(level = "warn")
)]
async fn receive_stream_inner(
    remote_manager: &Arc<RemoteManagerInner>,
    remote_uuid: &str,
    transfer_uuid: &str,
    stream: &mut Streaming<FileChunk>,
    destination: &PathBuf,
    cancellation_token: &CancellationToken,
    use_compression: bool,
) -> Result<bool, TransferError> {
    let mut state = ReceiveState::new();

    loop {
        tokio::select! {
            _ = cancellation_token.cancelled() => {
                tracing::info!("Transfer cancelled via token");
                state.finalize_current_file().await;
                return Ok(false);
            }
            msg_result = stream.message() => {
                match msg_result {
                    Ok(Some(chunk)) => {
                        process_chunk(chunk, &mut state, destination, remote_manager, remote_uuid, transfer_uuid, use_compression).await?;
                    }
                    Ok(None) => {
                        // Stream finished successfully
                        break;
                    }
                    Err(_) => {
                        state.finalize_current_file().await;
                        return Err(TransferError::ConnectionLost);
                    }
                }
            }
        }
    }

    // Done
    state.finalize_current_file().await;
    Ok(true)
}

#[cfg(feature = "real_filesystem")]
async fn process_chunk(
    chunk: FileChunk,
    state: &mut ReceiveState,
    destination: &PathBuf,
    remote_manager: &Arc<RemoteManagerInner>,
    remote_uuid: &str,
    transfer_uuid: &str,
    use_compression: bool,
) -> Result<(), TransferError> {
    let file_type = FileType::try_from(chunk.file_type).unwrap_or(FileType::File);

    if file_type == FileType::Symlink {
        return Ok(());
    }

    let sanitized = sanitize_path_component(&chunk.relative_path);
    let target_path = destination.join(&sanitized);

    // Check that the target path is within the destination directory
    if sanitized.contains("..") && !target_path.starts_with(&destination) {
        return Err(TransferError::UnsafePath);
    }

    if state.current_path.as_deref() != Some(&sanitized) {
        state.finalize_current_file().await;
        state.current_path = Some(sanitized.clone());
        state.current_file_mtime = chunk.time.map(|t| t);

        if file_type == FileType::Directory {
            tokio::fs::create_dir_all(&target_path).await.ok();
            return Ok(());
        }

        if let Some(parent) = target_path.parent() {
            tokio::fs::create_dir_all(parent).await.ok();
        }

        match tokio::fs::File::create(&target_path).await {
            Ok(file) => state.current_file = Some(file),
            Err(e) => return Err(e.kind().into()),
        }
    }

    let chunk_data = if use_compression {
        crate::server::transfers::compression::decompress_chunk(&chunk.chunk)
            .map_err(|e| TransferError::from(e.kind()))?
    } else {
        chunk.chunk.to_vec()
    };

    write_chunk(&chunk_data, state, remote_manager, remote_uuid, transfer_uuid).await?;

    Ok(())
}

#[cfg(feature = "virtual_filesystem")]
async fn process_chunk(
    chunk: FileChunk,
    state: &mut ReceiveState,
    destination: &PathBuf,
    remote_manager: &Arc<RemoteManagerInner>,
    remote_uuid: &str,
    transfer_uuid: &str,
    use_compression: bool,
) -> Result<(), TransferError> {
    let file_type = FileType::try_from(chunk.file_type).unwrap_or(FileType::File);

    if file_type == FileType::Symlink {
        return Ok(());
    }

    let sanitized = sanitize_path_component(&chunk.relative_path);

    // Check that the target path is within the destination directory
    if sanitized.contains("..") {
        return Err(TransferError::UnsafePath);
    }

    if state.current_path.as_deref() != Some(&sanitized) {
        state.finalize_current_file().await;
        state.current_path = Some(sanitized.clone());
        state.current_file_mtime = chunk.time.map(|t| t);

        if file_type == FileType::Directory {
            crate::filesystem::vfs::create_dir(
                destination.to_str().ok_or(TransferError::UnsafePath)?,
                &*sanitized,
            )
            .await?;
            return Ok(());
        }

        match crate::filesystem::vfs::create_file(
            destination.to_str().ok_or(TransferError::UnsafePath)?,
            &*sanitized,
        )
        .await
        {
            Ok(file) => state.current_file = Some(file),
            Err(e) => return Err(e.into()),
        }
    }

    let chunk_data = if use_compression {
        crate::server::transfers::compression::decompress_chunk(&chunk.chunk)
            .map_err(|e| TransferError::from(e.kind()))?
    } else {
        chunk.chunk.to_vec()
    };

    write_chunk(&chunk_data, state, remote_manager, remote_uuid, transfer_uuid).await?;

    Ok(())
}

async fn write_chunk(
    data: &[u8],
    state: &mut ReceiveState,
    remote_manager: &Arc<RemoteManagerInner>,
    remote_uuid: &str,
    transfer_uuid: &str,
) -> Result<(), TransferError> {
    let file = match state.current_file.as_mut() {
        None => return Ok(()),
        Some(file) => file,
    };

    let chunk_len = data.len() as u64;

    if let Err(e) = file.write_all(&data).await {
        return Err(e.kind().into());
    }

    // Progress
    let elapsed = state.last_chunk_time.elapsed().as_secs_f64().max(0.001);
    let avg_bps = state.speed.push(chunk_len, elapsed);
    state.last_chunk_time = std::time::Instant::now();

    remote_manager
        .update_transfer(&remote_uuid, &transfer_uuid, |t| {
            t.bytes_transferred += chunk_len;
            t.bytes_per_second = avg_bps;
        })
        .await
        .ok();

    Ok(())
}

fn sanitize_path_component(name: &str) -> String {
    name.replace(['\\', '<', '>', '*', '|', '?', ':', '"'], "_")
}
