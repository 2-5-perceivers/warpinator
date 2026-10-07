use std::io::ErrorKind as IoErrorKind;
use std::path::{Path, PathBuf};

use thiserror::Error;
use tokio_util::sync::CancellationToken;
use tracing::instrument;

use crate::proto::{OpInfo, TransferOpRequest};

#[cfg_attr(feature = "serde", derive(serde::Serialize))]
#[cfg_attr(feature = "serde", serde(rename_all = "snake_case"))]
#[derive(Error, Clone, Debug)]
pub enum TransferError {
    #[error("Connection to remote was lost")]
    ConnectionLost,
    #[error("Not enough storage space")]
    StorageFull,
    #[error("Failed to process source files")]
    FailedToProcessFiles,
    #[error("Failed to start transfer: {0}")]
    FailedToStartTransfer(#[cfg_attr(feature = "serde", serde(skip))] tonic::Status),
    #[error("Received an unsafe file path from remote")]
    UnsafePath,
    #[error("Source files not found")]
    FilesNotFound,
    #[error("Permission denied writing to destination")]
    PermissionDenied,
    #[error("File too large for destination filesystem")]
    FileTooLarge,
    #[error("Filename is invalid for the destination filesystem")]
    InvalidFilename,
    #[error("Out of memory")]
    OutOfMemory,
    #[error("IO error during transfer: {0}")]
    IoError(#[cfg_attr(feature = "serde", serde(skip))] IoErrorKind),
    #[error("Transfer failed due an error on the other side")]
    RemoteError,
    #[cfg(feature = "virtual_filesystem")]
    #[error(transparent)]
    VirtualFilesystemError(#[from] crate::filesystem::vfs::VirtualFilesystemError),
}

impl From<IoErrorKind> for TransferError {
    fn from(value: IoErrorKind) -> Self {
        match value {
            IoErrorKind::NotFound => TransferError::FilesNotFound,
            IoErrorKind::PermissionDenied | IoErrorKind::ReadOnlyFilesystem => {
                TransferError::PermissionDenied
            }
            IoErrorKind::StorageFull => TransferError::StorageFull,
            IoErrorKind::FileTooLarge => TransferError::FileTooLarge,
            IoErrorKind::InvalidFilename => TransferError::InvalidFilename,
            IoErrorKind::OutOfMemory => TransferError::OutOfMemory,
            e @ _ => TransferError::IoError(e),
        }
    }
}

#[cfg_attr(feature = "serde", derive(serde::Serialize))]
#[cfg_attr(feature = "serde", serde(rename_all = "snake_case"))]
#[derive(Clone, Debug)]
pub enum TransferState {
    /// New outgoing transfer
    Initializing,
    /// Waiting for the other party to accept the transfer
    WaitingPermission,
    /// Transfer is in progress
    InProgress,
    /// Transfer is paused
    Paused,
    /// Transfer is completed
    Completed,
    /// Transfer was stopped
    Stopped,
    /// Transfer was canceled by the sender
    Canceled,
    /// Transfer was denied by the other party
    Denied,
    /// Transfer failed due to an error
    Failed(TransferError),
}

#[cfg_attr(feature = "serde", derive(serde::Serialize))]
#[derive(Clone, Debug)]
pub struct Transfer {
    /// Unique identifier for this transfer
    pub uuid: String,
    /// Unique identifier of the parent remote
    pub remote_uuid: String,
    /// The id of the transfer used in the protocol
    #[cfg_attr(feature = "serde", serde(skip))]
    pub(crate) protocol_id: u64,

    /// Current state of the transfer
    pub state: TransferState,
    /// Timestamp of the time when the transfer was created(sent/received) in
    /// milliseconds
    pub timestamp: u64,

    /// Total size of the transfer in bytes
    pub total_bytes: u64,
    /// Number of bytes transferred so far
    pub bytes_transferred: u64,
    /// Current transfer speed in bytes per second. Moving average
    pub bytes_per_second: u64,

    /// Number of total files in the transfer
    pub file_count: u64,
    /// Names of the top dir entries in the transfer
    pub entry_names: Vec<String>,
    /// Utilized only if the transfer contains a single file. Name of the file
    /// being transferred
    pub single_name: Option<String>,
    /// Utilized only if the transfer contains a single file. MIME type of the
    /// file being transferred
    pub single_mime_type: Option<String>,

    /// Kind of transfer - incoming or outgoing. Contains additional data
    /// relevant to the kind
    pub kind: TransferKind,
    /// Whether this transfer uses compression for file chunks
    pub use_compression: bool,
}

#[cfg_attr(feature = "serde", derive(serde::Serialize))]
#[derive(Clone, Debug)]
pub enum TransferKind {
    Outgoing {
        source_paths: Vec<PathBuf>,
        #[cfg_attr(feature = "serde", serde(skip))]
        cancellation_token: CancellationToken,
    },
    Incoming {
        destination: PathBuf,
    },
}

#[derive(Error, Debug)]
pub enum SourcePathError {
    #[cfg(feature = "virtual_filesystem")]
    #[error(transparent)]
    VirtualFilesystemError(#[from] crate::filesystem::vfs::VirtualFilesystemError),
    #[error("IO error while processing source paths: {0}")]
    IoError(std::io::Error),
    #[error("Unsupported path type: {0}")]
    UnsupportedPathType(PathBuf),
}

impl Transfer {
    pub async fn new_outgoing(
        remote_uuid: String,
        source_paths: Vec<PathBuf>,
        cancellation_token: CancellationToken,
    ) -> Self {
        let id = uuid::Uuid::new_v4();
        let (id_h, id_l) = id.as_u64_pair();

        Transfer {
            uuid: id.to_string(),
            remote_uuid,
            protocol_id: id_h.wrapping_add(id_l), /* simple way to generate a protocol id from
                                                   * the uuid */
            state: TransferState::Initializing,
            timestamp: chrono::Utc::now().timestamp_millis() as u64,
            total_bytes: 0,
            bytes_transferred: 0,
            bytes_per_second: 0,
            file_count: 0,
            entry_names: vec![],
            single_name: None,
            single_mime_type: None,
            kind: TransferKind::Outgoing { source_paths, cancellation_token },
            use_compression: false,
        }
    }

    pub fn as_proto(&self, service_id: &str, use_compression: bool) -> TransferOpRequest {
        TransferOpRequest {
            info: Some(OpInfo {
                ident: service_id.to_string(),
                timestamp: self.protocol_id,
                readable_name: String::default(),
                use_compression,
            }),
            sender_name: String::default(),
            receiver_name: String::default(),
            receiver: service_id.to_string(),
            size: self.total_bytes,
            count: self.file_count,
            name_if_single: self.single_name.clone().unwrap_or_default(),
            mime_if_single: self.single_mime_type.clone().unwrap_or_default(),
            top_dir_basenames: self.entry_names.clone(),
        }
    }

    #[cfg(feature = "real_filesystem")]
    #[instrument(skip_all, level = "debug", err(level = "warn"))]
    pub async fn process_paths<P: AsRef<Path>>(
        &mut self,
        paths: &[P],
    ) -> Result<(), SourcePathError> {
        use async_walkdir::WalkDir;
        use tokio_stream::StreamExt;

        let mut total_size = 0;
        let mut file_count = 0;
        let mut entry_names = Vec::new();

        if paths.len() == 1 {
            let metadata =
                tokio::fs::metadata(&paths[0]).await.map_err(SourcePathError::IoError)?;
            if metadata.is_file() {
                total_size = metadata.len();
                file_count = 1;

                let file_name = paths[0]
                    .as_ref()
                    .file_name()
                    .ok_or(SourcePathError::UnsupportedPathType(paths[0].as_ref().into()))?
                    .to_string_lossy()
                    .to_string();

                entry_names.push(file_name.clone());
                let single_name = Some(file_name);
                let single_mime_type = Some(
                    mime_guess::from_path(&paths[0])
                        .first_or_octet_stream()
                        .essence_str()
                        .to_string(),
                );

                self.single_name = single_name;
                self.single_mime_type = single_mime_type;
                self.total_bytes = total_size;
                self.entry_names = entry_names;
                self.file_count = file_count;
                return Ok(());
            }
        }

        for p in paths {
            let path_metadata = tokio::fs::metadata(p).await.map_err(SourcePathError::IoError)?;

            if path_metadata.is_file() {
                total_size += path_metadata.len();
                file_count += 1;
            } else if path_metadata.is_dir() {
                let mut entries = WalkDir::new(p);
                while let Some(entry) = entries.next().await {
                    match entry {
                        Ok(entry) => {
                            if let Ok(metadata) = entry.metadata().await {
                                if metadata.is_file() {
                                    total_size += metadata.len();
                                    file_count += 1;
                                }
                            }
                        }
                        Err(e) => {
                            tracing::warn!(
                                "Failed to read entry in directory {}: {}",
                                p.as_ref().display(),
                                e
                            );
                        }
                    }
                }
            }

            if let Some(file_name) = p.as_ref().file_name() {
                entry_names.push(file_name.to_string_lossy().to_string());
            }
        }
        self.single_name = None;
        self.single_mime_type = None;
        self.total_bytes = total_size;
        self.entry_names = entry_names;
        self.file_count = file_count;
        Ok(())
    }

    #[cfg(feature = "virtual_filesystem")]
    #[instrument(skip_all, level = "debug", err(level = "warn"))]
    pub async fn process_paths<P: AsRef<Path>>(
        &mut self,
        paths: &[P],
    ) -> Result<(), SourcePathError> {
        let mut total_size = 0;
        let mut file_count = 0;
        let mut entry_names = Vec::new();

        if paths.len() == 1 {
            let metadata =
                crate::filesystem::vfs::metadata(paths[0].as_ref().to_string_lossy().as_ref())
                    .await?;

            if !metadata.is_dir {
                total_size = metadata.size;
                file_count = 1;
                let file_name = metadata.name;

                entry_names.push(file_name.clone());
                let single_name = Some(file_name.clone());
                let single_mime_type = Some(
                    mime_guess::from_ext(
                        &file_name.split_once('.').map(|s| s.1).unwrap_or_default(),
                    )
                    .first_or_octet_stream()
                    .essence_str()
                    .to_string(),
                );

                self.single_name = single_name;
                self.single_mime_type = single_mime_type;
                self.total_bytes = total_size;
                self.entry_names = entry_names;
                self.file_count = file_count;
                return Ok(());
            }
        }

        for p in paths {
            let path_metadata =
                crate::filesystem::vfs::metadata(p.as_ref().to_string_lossy().as_ref()).await?;

            if path_metadata.is_dir {
                let entries =
                    crate::filesystem::vfs::read_dir(p.as_ref().to_string_lossy().as_ref()).await?;
                for entry in entries {
                    total_size += entry.size;
                    file_count += entry.file_count;
                }
            } else {
                total_size += path_metadata.size;
                file_count += 1;
            }
            entry_names.push(path_metadata.name);
        }
        self.single_name = None;
        self.single_mime_type = None;
        self.total_bytes = total_size;
        self.entry_names = entry_names;
        self.file_count = file_count;
        Ok(())
    }
}

impl From<TransferOpRequest> for Transfer {
    fn from(value: TransferOpRequest) -> Self {
        let info = value.info.as_ref().expect("TransferOpRequest must have info");
        Transfer {
            uuid: uuid::Uuid::new_v4().to_string(),
            remote_uuid: info.ident.clone(),
            protocol_id: info.timestamp,
            state: TransferState::WaitingPermission,
            timestamp: chrono::Utc::now().timestamp_millis() as u64,
            total_bytes: value.size,
            bytes_transferred: 0,
            bytes_per_second: 0,
            file_count: value.count,
            entry_names: value.top_dir_basenames,
            single_name: if value.count == 1 && !value.name_if_single.is_empty() {
                value.name_if_single.into()
            } else {
                None
            },
            single_mime_type: if value.count == 1 && !value.mime_if_single.is_empty() {
                value.mime_if_single.into()
            } else {
                None
            },
            kind: TransferKind::Incoming {
                // default destination, should be updated when the transfer is accepted
                destination: PathBuf::from("/"),
            },
            use_compression: info.use_compression,
        }
    }
}
