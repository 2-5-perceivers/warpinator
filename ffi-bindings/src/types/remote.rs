use warpinator_lib::types::remote::{RemoteConnectionError, RemoteState};

#[uniffi::remote(Enum)]
pub enum RemoteConnectionError {
    SslError,
    GroupCodeMismatch,
    NoCertificate,
    DuplexError,
}

#[uniffi::remote(Enum)]
pub enum RemoteState {
    Error(RemoteConnectionError),
    Disconnected,
    Connecting,
    AwaitingDuplex,
    Connected,
}

#[derive(uniffi::Record)]
pub struct Remote {
    pub uuid: String,
    pub ip: String,
    pub port: u16,
    pub auth_port: u16,
    pub service_name: String,

    pub display_name: String,
    pub username: String,
    pub hostname: String,
    /// Whether the remote has a picture or not
    pub picture: bool,
    pub picture_version: u8,

    pub state: RemoteState,

    pub service_static: bool,
    pub service_available: bool,
    #[cfg(feature = "messaging")]
    pub message_support: bool,
}

impl From<&warpinator_lib::types::remote::Remote> for Remote {
    fn from(value: &warpinator_lib::types::remote::Remote) -> Self {
        Self {
            uuid: value.uuid.clone(),
            ip: value.ip.to_string(),
            port: value.port,
            auth_port: value.auth_port,
            service_name: value.service_name.clone(),
            display_name: value.display_name.clone(),
            username: value.username.clone(),
            hostname: value.hostname.clone(),
            picture: value.picture.is_some(),
            picture_version: value.picture_version,
            state: value.state.clone(),
            service_static: value.service_static,
            service_available: value.service_available,
            #[cfg(feature = "messaging")]
            message_support: value
                .features
                .contains(warpinator_lib::config::features::ProtocolFeatures::MESSAGE_SUPPORT),
        }
    }
}
