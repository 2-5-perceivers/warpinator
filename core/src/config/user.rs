use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};
use std::sync::Arc;
use std::sync::atomic::{AtomicBool, Ordering};

use tokio::sync::RwLock;

const DEFAULT_WARP_PORT: u16 = 42000;
const DEFAULT_REG_PORT: u16 = 42001;
const DEFAULT_GROUP_CODE: &str = "Warpinator";
const DEFAULT_HOSTNAME: &str = "warpinator";
const DEFAULT_DISPLAY_NAME: &str = "Warpinator RS";
const DEFAULT_USERNAME: &str = "warpinator-rs";

pub struct UserConfigBuilder {
    port: Option<u16>,
    reg_port: Option<u16>,
    bind_addr_v4: Option<Ipv4Addr>,
    bind_addr_v6: Option<Ipv6Addr>,
    group_code: Option<String>,
    hostname: Option<String>,
    username: Option<String>,
    display_name: Option<String>,
    picture: Option<Vec<u8>>,
    use_compression: Option<bool>,
}

impl UserConfigBuilder {
    fn new() -> Self {
        Self {
            port: None,
            reg_port: None,
            bind_addr_v4: None,
            bind_addr_v6: None,
            group_code: None,
            hostname: None,
            username: None,
            display_name: None,
            picture: None,
            use_compression: None,
        }
    }

    pub fn port(mut self, port: u16) -> Self {
        self.port = Some(port);
        self
    }

    pub fn reg_port(mut self, reg_port: u16) -> Self {
        self.reg_port = Some(reg_port);
        self
    }

    pub fn bind_addr_v4(mut self, addr: Ipv4Addr) -> Self {
        self.bind_addr_v4 = Some(addr);
        self
    }

    pub fn default_bind_addr_v4(mut self) -> Self {
        self.bind_addr_v4 = local_ip_address::local_ip()
            .ok()
            .map(|ip| if let IpAddr::V4(ipv4) = ip { ipv4 } else { Ipv4Addr::UNSPECIFIED });
        self
    }

    pub fn bind_addr_v6(mut self, addr: Ipv6Addr) -> Self {
        self.bind_addr_v6 = Some(addr);
        self
    }

    pub fn default_bind_addr_v6(mut self) -> Self {
        self.bind_addr_v6 = local_ip_address::local_ipv6()
            .ok()
            .map(|ip| if let IpAddr::V6(ipv6) = ip { ipv6 } else { Ipv6Addr::UNSPECIFIED });
        self
    }

    pub fn group_code(mut self, group_code: &str) -> Self {
        self.group_code = Some(group_code.into());
        self
    }

    pub fn hostname(mut self, hostname: &str) -> Self {
        self.hostname = Some(hostname.into());
        self
    }

    pub fn default_hostname(mut self) -> Self {
        self.hostname = hostname::get().ok().map(|h| h.to_string_lossy().to_string());
        self
    }

    pub fn username(mut self, username: &str) -> Self {
        self.username = Some(username.into());
        self
    }

    pub fn display_name(mut self, display_name: &str) -> Self {
        self.display_name = Some(display_name.into());
        self
    }

    pub fn picture(mut self, picture: &[u8]) -> Self {
        self.picture = Some(picture.to_vec());
        self
    }

    pub fn use_compression(mut self, enabled: bool) -> Self {
        self.use_compression = Some(enabled);
        self
    }

    pub fn build(mut self) -> UserConfig {
        if self.bind_addr_v4.is_none() && self.bind_addr_v6.is_none() {
            self.bind_addr_v4 = Some(Ipv4Addr::UNSPECIFIED);
            tracing::warn!("No bind address specified")
        }

        UserConfig {
            port: self.port.unwrap_or(DEFAULT_WARP_PORT),
            reg_port: self.reg_port.unwrap_or(DEFAULT_REG_PORT),
            bind_addr_v4: self.bind_addr_v4,
            bind_addr_v6: self.bind_addr_v6,
            group_code: self.group_code.unwrap_or_else(|| DEFAULT_GROUP_CODE.to_string()),
            hostname: self.hostname.unwrap_or_else(|| DEFAULT_HOSTNAME.to_string()),
            username: self.username.unwrap_or_else(|| DEFAULT_USERNAME.to_string()),
            display_name: Arc::new(RwLock::new(
                self.display_name.unwrap_or_else(|| DEFAULT_DISPLAY_NAME.to_string()),
            )),
            picture: Arc::new(RwLock::new(self.picture)),
            use_compression: Arc::new(AtomicBool::new(self.use_compression.unwrap_or(false))),
        }
    }
}

#[derive(Debug, Clone)]
pub struct UserConfig {
    pub port: u16,
    pub reg_port: u16,
    pub bind_addr_v4: Option<Ipv4Addr>,
    pub bind_addr_v6: Option<Ipv6Addr>,
    pub group_code: String,
    pub hostname: String,
    pub username: String,
    pub display_name: Arc<RwLock<String>>,
    /// The user's picture as a byte vector. The image format is PNG. This is
    /// optional and can be None if the user does not want to set a picture.
    pub picture: Arc<RwLock<Option<Vec<u8>>>>,
    pub use_compression: Arc<AtomicBool>,
}

impl UserConfig {
    pub fn builder() -> UserConfigBuilder {
        UserConfigBuilder::new()
    }

    pub async fn set_display_name(&self, display_name: &str) {
        let mut display_name_guard = self.display_name.write().await;
        *display_name_guard = display_name.to_string();
    }

    pub async fn set_picture(&self, picture: Option<&[u8]>) {
        let mut picture_guard = self.picture.write().await;
        *picture_guard = picture.map(|p| p.to_vec());
    }

    pub fn use_compression(&self) -> bool {
        self.use_compression.load(Ordering::Relaxed)
    }

    pub fn set_use_compression(&self, enabled: bool) {
        self.use_compression.store(enabled, Ordering::Relaxed);
    }
}

impl Default for UserConfig {
    fn default() -> Self {
        UserConfig {
            port: DEFAULT_WARP_PORT,
            reg_port: DEFAULT_REG_PORT,
            bind_addr_v4: Ipv4Addr::UNSPECIFIED.into(),
            bind_addr_v6: Ipv6Addr::UNSPECIFIED.into(),
            group_code: DEFAULT_GROUP_CODE.to_string(),
            hostname: DEFAULT_HOSTNAME.to_string(),
            username: DEFAULT_USERNAME.to_string(),
            display_name: Arc::new(RwLock::new(DEFAULT_DISPLAY_NAME.to_string())),
            picture: Arc::new(RwLock::new(None)),
            use_compression: Arc::new(AtomicBool::new(false)),
        }
    }
}
