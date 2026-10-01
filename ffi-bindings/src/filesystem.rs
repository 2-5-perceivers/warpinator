#[cfg(feature = "virtual_filesystem")]
mod vfs {
    use warpinator_lib::filesystem::vfs::{VirtualEntry, VirtualFilesystemError, VirtualMetadata};

    #[uniffi::remote(Error)]
    pub enum VirtualFilesystemError {
        #[error("Filesystem already set")]
        AlreadySet,
        #[error("Filesystem not set")]
        NotSet,
        #[error("File not found")]
        FileNotFound,
        #[error("File already exists")]
        FileAlreadyExists,
        #[error("Permission denied")]
        PermissionDenied,
        #[error("Invalid path")]
        InvalidPath,
        #[error("Failed to create file")]
        FileCreateError,
    }

    #[uniffi::remote(Record)]
    pub struct VirtualMetadata {
        pub is_dir: bool,
        pub name: String,
        pub size: u64,
        pub file_count: u64,
    }

    #[uniffi::remote(Record)]
    pub struct VirtualEntry {
        pub is_dir: bool,
        pub path: String,
        pub name: String,
    }

    pub type Result<T> = std::result::Result<T, VirtualFilesystemError>;

    #[uniffi::export(callback_interface)]
    #[async_trait::async_trait]
    pub trait VirtualFilesystem: Send + Sync {
        async fn metadata(&self, path: String) -> Result<VirtualMetadata>;
        async fn read_dir(&self, path: String) -> Result<Vec<VirtualMetadata>>;
        async fn list_dir(&self, path: String) -> Result<Vec<VirtualEntry>>;
        async fn create_dir(&self, path: String, folder: String) -> Result<String>;
        async fn open_file(&self, path: String) -> Result<i32>;
        async fn create_file(&self, path: String, file: String) -> Result<i32>;
    }

    struct VirtualFilesystemWrapper {
        inner: Box<dyn VirtualFilesystem>,
    }
    type InnerResult<T> = std::result::Result<T, VirtualFilesystemError>;

    #[async_trait::async_trait]
    impl warpinator_lib::filesystem::vfs::VirtualFilesystem for VirtualFilesystemWrapper {
        async fn metadata(&self, path: String) -> InnerResult<VirtualMetadata> {
            let meta = self.inner.metadata(path).await?;

            Ok(VirtualMetadata {
                is_dir: meta.is_dir,
                name: meta.name,
                size: meta.size,
                file_count: meta.file_count,
            })
        }

        async fn read_dir(&self, path: String) -> InnerResult<Vec<VirtualMetadata>> {
            let entries = self.inner.read_dir(path).await?;

            Ok(entries.into_iter().map(|meta| meta.into()).collect())
        }

        async fn list_dir(&self, path: String) -> InnerResult<Vec<VirtualEntry>> {
            let entries = self.inner.list_dir(path).await?;
            Ok(entries.into_iter().map(|meta| meta.into()).collect())
        }

        async fn create_dir(&self, path: String, folder: String) -> InnerResult<String> {
            self.inner
                .create_dir(path, folder)
                .await
                .map_err(|e| e.into())
        }

        async fn open_file(&self, path: String) -> InnerResult<i32> {
            self.inner.open_file(path).await.map_err(|e| e.into())
        }

        async fn create_file(&self, path: String, file: String) -> InnerResult<i32> {
            self.inner
                .create_file(path, file)
                .await
                .map_err(|e| e.into())
        }
    }

    #[uniffi::export]
    pub fn set_virtual_filesystem(vfs: Box<dyn VirtualFilesystem>) -> Result<()> {
        warpinator_lib::filesystem::vfs::set_virtual_filesystem(Box::new(
            VirtualFilesystemWrapper { inner: vfs },
        ))
        .map_err(|_| VirtualFilesystemError::AlreadySet)
    }
}
