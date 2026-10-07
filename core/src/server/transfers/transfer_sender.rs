use std::fs::Metadata;
#[cfg(feature = "real_filesystem")]
use std::path::Path;
use std::path::PathBuf;

use tokio::fs::File;
use tokio::io::AsyncReadExt;
use tokio::sync::mpsc::Sender;
#[cfg(feature = "real_filesystem")]
use tokio_stream::StreamExt;
use tokio_util::sync::CancellationToken;
use tonic::Status;

use crate::proto::{FileChunk, FileTime};
use crate::remote_manager::RemoteManager;
use crate::server::transfers::{FileType, MovingAverageCalculator};
use crate::types::transfer::{TransferError, TransferState};

const CHUNK_SIZE: usize = 64 * 1024;

pub(crate) async fn send_stream(
    remote_manager: RemoteManager,
    remote_uuid: String,
    transfer_uuid: String,
    source_paths: Vec<PathBuf>,
    tx: Sender<Result<FileChunk, Status>>,
    cancellation_token: CancellationToken,
    use_compression: bool,
    #[cfg(feature = "power_manager")] power_manager: std::sync::Arc<
        dyn crate::server::power_manager::PowerManager,
    >,
) {
    #[cfg(feature = "power_manager")]
    let _wake_lock = crate::server::power_manager::WakeLockGuard::new(power_manager);

    let result = send_stream_inner(
        &remote_manager,
        &remote_uuid,
        &transfer_uuid,
        &source_paths,
        &tx,
        &cancellation_token,
        use_compression,
    )
    .await;

    let final_state = match result {
        Ok(true) => TransferState::Completed,
        Err(e) if !cancellation_token.is_cancelled() => TransferState::Failed(e),
        _ => TransferState::Canceled,
    };

    remote_manager
        .update_transfer(&remote_uuid, &transfer_uuid, |t| {
            t.state = final_state;
            t.bytes_per_second = 0;
        })
        .await
        .ok();
}

/// Returns Ok(true) if completed, Ok(false) if cancelled, Err on failure
async fn send_stream_inner(
    remote_manager: &RemoteManager,
    remote_uuid: &str,
    transfer_uuid: &str,
    source_paths: &[PathBuf],
    tx: &Sender<Result<FileChunk, Status>>,
    cancellation_token: &CancellationToken,
    use_compression: bool,
) -> Result<bool, TransferError> {
    let mut speed = MovingAverageCalculator::new(30);

    for source in source_paths {
        if cancellation_token.is_cancelled() {
            return Ok(false);
        }

        #[cfg(feature = "real_filesystem")]
        let base = source.parent().ok_or(TransferError::FailedToProcessFiles)?;

        #[cfg(feature = "real_filesystem")]
        let source_metadata =
            tokio::fs::metadata(source).await.map_err(|e| TransferError::from(e.kind()))?;

        #[cfg(feature = "virtual_filesystem")]
        let source_metadata =
            crate::filesystem::vfs::metadata(source.to_str().ok_or(TransferError::UnsafePath)?)
                .await?;

        if source_metadata.is_file() {
            #[cfg(feature = "real_filesystem")]
            send_file(
                source,
                base,
                remote_manager,
                remote_uuid,
                transfer_uuid,
                source_metadata,
                &mut speed,
                tx,
                cancellation_token,
                use_compression,
            )
            .await?;

            #[cfg(feature = "virtual_filesystem")]
            send_file(
                source.to_str().ok_or(TransferError::UnsafePath)?,
                source_metadata.name.clone(),
                remote_manager,
                remote_uuid,
                transfer_uuid,
                &mut speed,
                tx,
                cancellation_token,
                use_compression,
            )
            .await?;
        } else if source_metadata.is_dir() {
            // Walk contents
            #[cfg(feature = "real_filesystem")]
            let mut walker = async_walkdir::WalkDir::new(source);
            #[cfg(feature = "virtual_filesystem")]
            let mut walker = crate::filesystem::vfs::walkdir::VirtualWalkDir::new(
                source.to_str().ok_or(TransferError::UnsafePath)?.to_string(),
                source_metadata.name.clone(),
            );

            while let Some(entry) = {
                #[cfg(feature = "real_filesystem")]
                {
                    walker.next().await
                }
                #[cfg(feature = "virtual_filesystem")]
                {
                    walker.next().await?
                }
            } {
                if cancellation_token.is_cancelled() {
                    return Ok(false);
                }
                #[cfg(feature = "real_filesystem")]
                let entry = entry.map_err(|_| TransferError::FailedToProcessFiles)?;
                #[cfg(feature = "real_filesystem")]
                let path = entry.path();
                #[cfg(feature = "real_filesystem")]
                let entry = entry.metadata().await.map_err(|e| TransferError::from(e.kind()))?;

                if entry.is_dir() {
                    #[cfg(feature = "real_filesystem")]
                    let rel =
                        path.strip_prefix(base).unwrap_or(&path).to_string_lossy().to_string();
                    #[cfg(feature = "virtual_filesystem")]
                    let rel = entry.relative_path();

                    let chunk = FileChunk {
                        relative_path: rel,
                        file_type: FileType::Directory.into(),
                        chunk: vec![].into(),
                        file_mode: 0o755,
                        time: None,
                        symlink_target: String::new(),
                    };
                    if tx.send(Ok(chunk)).await.is_err() {
                        return Err(TransferError::ConnectionLost);
                    }
                } else if entry.is_file() {
                    #[cfg(feature = "real_filesystem")]
                    send_file(
                        &path,
                        base,
                        remote_manager,
                        remote_uuid,
                        transfer_uuid,
                        entry,
                        &mut speed,
                        tx,
                        cancellation_token,
                        use_compression,
                    )
                    .await?;

                    #[cfg(feature = "virtual_filesystem")]
                    send_file(
                        entry.path.as_str(),
                        entry.relative_path(),
                        remote_manager,
                        remote_uuid,
                        transfer_uuid,
                        &mut speed,
                        tx,
                        cancellation_token,
                        use_compression,
                    )
                    .await?;
                }
                // symlinks skipped
            }
        }
    }

    Ok(true)
}

// Send file implementation

#[cfg(feature = "real_filesystem")]
async fn send_file(
    path: &Path,
    base: &Path,
    remote_manager: &RemoteManager,
    remote_uuid: &str,
    transfer_uuid: &str,
    metadata: Metadata,
    speed: &mut MovingAverageCalculator,
    tx: &Sender<Result<FileChunk, Status>>,
    cancellation_token: &CancellationToken,
    use_compression: bool,
) -> Result<(), TransferError> {
    let rel = path.strip_prefix(base).unwrap_or(path);
    let rel_str = rel.to_string_lossy().to_string();

    let file = File::open(path).await.map_err(|e| TransferError::from(e.kind()))?;

    send_file_inner(
        file,
        rel_str,
        remote_manager,
        remote_uuid,
        transfer_uuid,
        metadata,
        speed,
        tx,
        cancellation_token,
        use_compression,
    )
    .await
}

#[cfg(feature = "virtual_filesystem")]
async fn send_file(
    path: &str,
    rel: String,
    remote_manager: &RemoteManager,
    remote_uuid: &str,
    transfer_uuid: &str,
    speed: &mut MovingAverageCalculator,
    tx: &Sender<Result<FileChunk, Status>>,
    cancellation_token: &CancellationToken,
    use_compression: bool,
) -> Result<(), TransferError> {
    let file = crate::filesystem::vfs::open_file(path).await?;
    let metadata = file.metadata().await.map_err(|e| e.kind())?;

    send_file_inner(
        file,
        rel,
        remote_manager,
        remote_uuid,
        transfer_uuid,
        metadata,
        speed,
        tx,
        cancellation_token,
        use_compression,
    )
    .await
}

async fn send_file_inner(
    mut file: File,
    relative_path: String,
    remote_manager: &RemoteManager,
    remote_uuid: &str,
    transfer_uuid: &str,
    metadata: Metadata,
    speed: &mut MovingAverageCalculator,
    tx: &Sender<Result<FileChunk, Status>>,
    cancellation_token: &CancellationToken,
    use_compression: bool,
) -> Result<(), TransferError> {
    let file_time = metadata.modified().ok().map(|t| {
        let duration = t.duration_since(std::time::UNIX_EPOCH).unwrap_or_default();
        FileTime { mtime: duration.as_secs(), mtime_usec: duration.subsec_millis() * 1000 }
    });

    let mut buffer = vec![0u8; CHUNK_SIZE];
    let mut first_chunk = true;
    let mut last_chunk_time = std::time::Instant::now();

    loop {
        if cancellation_token.is_cancelled() {
            return Ok(());
        }
        let n = file.read(&mut buffer).await.map_err(|e| TransferError::from(e.kind()))?;
        if n == 0 {
            break; // EOF
        }

        let chunk_data: Vec<u8> = if use_compression {
            crate::server::transfers::compression::compress_chunk(&buffer[..n])
                .map_err(|e| TransferError::from(e.kind()))?
        } else {
            buffer[..n].to_vec()
        };

        let chunk = FileChunk {
            relative_path: relative_path.clone(),
            file_type: FileType::File.into(),
            chunk: chunk_data,
            file_mode: 0o644,
            time: if first_chunk { file_time.clone() } else { None },
            symlink_target: String::new(),
        };

        first_chunk = false;

        if tx.send(Ok(chunk)).await.is_err() {
            return Err(TransferError::ConnectionLost);
        }

        // Progress
        let elapsed = last_chunk_time.elapsed().as_secs_f64().max(0.001);
        let avg_bps = speed.push(n as u64, elapsed);
        last_chunk_time = std::time::Instant::now();

        remote_manager
            .update_transfer(remote_uuid, transfer_uuid, |t| {
                t.bytes_transferred += n as u64;
                t.bytes_per_second = avg_bps;
            })
            .await
            .ok();
    }

    Ok(())
}
