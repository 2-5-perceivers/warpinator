#[derive(uniffi::Enum)]
pub enum TransferError {
    ConnectionLost,
    StorageFull,
    FailedToProcessFiles,
    FailedToStartTransfer,
    UnsafePath,
    FilesNotFound,
    PermissionDenied,
    FileTooLarge,
    InvalidFilename,
    OutOfMemory,
    IoError,
    RemoteError,
    #[cfg(feature = "virtual_filesystem")]
    VirtualFilesystemError,
}

#[derive(uniffi::Enum)]
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

#[derive(uniffi::Record)]
pub struct Transfer {
    /// Unique identifier for this transfer
    pub uuid: String,
    /// Unique identifier of the parent remote
    pub remote_uuid: String,

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
    pub use_compression: bool,
}

#[derive(uniffi::Enum)]
pub enum TransferKind {
    Outgoing { source_paths: Vec<String> },
    Incoming { destination: String },
}

impl From<&warpinator_lib::types::transfer::Transfer> for Transfer {
    fn from(value: &warpinator_lib::types::transfer::Transfer) -> Self {
        Self {
            uuid: value.uuid.clone(),
            remote_uuid: value.remote_uuid.clone(),

            state: (&value.state).into(),
            timestamp: value.timestamp,
            total_bytes: value.total_bytes,
            bytes_transferred: value.bytes_transferred,
            bytes_per_second: value.bytes_per_second,
            file_count: value.file_count,
            entry_names: value.entry_names.clone(),
            single_name: value.single_name.clone(),
            single_mime_type: value.single_mime_type.clone(),
            kind: (&value.kind).into(),
            use_compression: value.use_compression,
        }
    }
}

impl From<&warpinator_lib::types::transfer::TransferState> for TransferState {
    fn from(value: &warpinator_lib::types::transfer::TransferState) -> Self {
        match value {
            warpinator_lib::types::transfer::TransferState::Initializing => Self::Initializing,
            warpinator_lib::types::transfer::TransferState::WaitingPermission => {
                Self::WaitingPermission
            }
            warpinator_lib::types::transfer::TransferState::InProgress => Self::InProgress,
            warpinator_lib::types::transfer::TransferState::Paused => Self::Paused,
            warpinator_lib::types::transfer::TransferState::Completed => Self::Completed,
            warpinator_lib::types::transfer::TransferState::Stopped => Self::Stopped,
            warpinator_lib::types::transfer::TransferState::Canceled => Self::Canceled,
            warpinator_lib::types::transfer::TransferState::Denied => Self::Denied,
            warpinator_lib::types::transfer::TransferState::Failed(e) => Self::Failed(match e {
                warpinator_lib::types::transfer::TransferError::ConnectionLost => {
                    TransferError::ConnectionLost
                }
                warpinator_lib::types::transfer::TransferError::StorageFull => {
                    TransferError::StorageFull
                }
                warpinator_lib::types::transfer::TransferError::FailedToProcessFiles => {
                    TransferError::FailedToProcessFiles
                }
                warpinator_lib::types::transfer::TransferError::FailedToStartTransfer(_) => {
                    TransferError::FailedToStartTransfer
                }
                warpinator_lib::types::transfer::TransferError::UnsafePath => {
                    TransferError::UnsafePath
                }
                warpinator_lib::types::transfer::TransferError::FilesNotFound => {
                    TransferError::FilesNotFound
                }
                warpinator_lib::types::transfer::TransferError::PermissionDenied => {
                    TransferError::PermissionDenied
                }
                warpinator_lib::types::transfer::TransferError::FileTooLarge => {
                    TransferError::FileTooLarge
                }
                warpinator_lib::types::transfer::TransferError::InvalidFilename => {
                    TransferError::InvalidFilename
                }
                warpinator_lib::types::transfer::TransferError::OutOfMemory => {
                    TransferError::OutOfMemory
                }
                warpinator_lib::types::transfer::TransferError::IoError(_) => {
                    TransferError::IoError
                }
                warpinator_lib::types::transfer::TransferError::RemoteError => {
                    TransferError::RemoteError
                }
                #[cfg(feature = "virtual_filesystem")]
                warpinator_lib::types::transfer::TransferError::VirtualFilesystemError(_) => {
                    TransferError::VirtualFilesystemError
                }
            }),
        }
    }
}

impl From<&warpinator_lib::types::transfer::TransferKind> for TransferKind {
    fn from(value: &warpinator_lib::types::transfer::TransferKind) -> Self {
        match value {
            warpinator_lib::types::transfer::TransferKind::Outgoing { source_paths, .. } => {
                Self::Outgoing {
                    source_paths: source_paths
                        .iter()
                        .map(|p| p.to_string_lossy().to_string())
                        .collect(),
                }
            }
            warpinator_lib::types::transfer::TransferKind::Incoming { destination, .. } => {
                Self::Incoming {
                    destination: destination.to_string_lossy().to_string(),
                }
            }
        }
    }
}
