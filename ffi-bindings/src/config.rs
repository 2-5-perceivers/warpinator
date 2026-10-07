use crate::WarpError;
use std::time::Duration;

#[derive(uniffi::Record)]
pub struct UserConfig {
    pub port: Option<u16>,
    pub reg_port: Option<u16>,
    pub bind_addr_v4: Option<String>,
    pub bind_addr_v6: Option<String>,
    pub group_code: Option<String>,
    pub hostname: Option<String>,
    pub username: Option<String>,
    pub display_name: Option<String>,
    pub picture: Option<Vec<u8>>,
    pub use_compression: Option<bool>,
}

impl UserConfig {
    pub fn to_config(self) -> crate::Result<warpinator_lib::config::user::UserConfig> {
        let mut config_builder = warpinator_lib::config::user::UserConfig::builder();

        if let Some(port) = self.port {
            config_builder = config_builder.port(port);
        }
        if let Some(reg_port) = self.reg_port {
            config_builder = config_builder.reg_port(reg_port);
        }
        if let Some(bind_addr_v4) = self.bind_addr_v4 {
            config_builder = config_builder
                .bind_addr_v4(bind_addr_v4.parse().map_err(|_| WarpError::InvalidIp)?);
        } else {
            config_builder = config_builder.default_bind_addr_v4();
        }

        if let Some(bind_addr_v6) = self.bind_addr_v6 {
            config_builder = config_builder
                .bind_addr_v6(bind_addr_v6.parse().map_err(|_| WarpError::InvalidIp)?);
        } else {
            config_builder = config_builder.default_bind_addr_v6();
        }

        if let Some(group_code) = self.group_code {
            config_builder = config_builder.group_code(&*group_code);
        }
        if let Some(hostname) = self.hostname {
            config_builder = config_builder.hostname(&*hostname);
        } else {
            config_builder = config_builder.default_hostname();
        }

        if let Some(username) = self.username {
            config_builder = config_builder.username(&*username);
        }

        if let Some(display_name) = self.display_name {
            config_builder = config_builder.display_name(&*display_name);
        }

        if let Some(picture) = self.picture {
            config_builder = config_builder.picture(&picture);
        }

        if let Some(use_compression) = self.use_compression {
            config_builder = config_builder.use_compression(use_compression);
        }

        Ok(config_builder.build())
    }
}

#[derive(uniffi::Record)]
pub struct ProtocolConfig {
    // No features as that is best set using cargo features
    // And reexporting constants here would be unnecessary
    pub reconnect_interval: Option<Duration>,
    pub connect_timeout: Option<Duration>,
    pub ping_interval: Option<Duration>,
    pub ping_timeout: Option<Duration>,
}

impl ProtocolConfig {
    pub fn to_config(self) -> warpinator_lib::config::protocol::ProtocolConfig {
        let mut protocol_config_builder =
            warpinator_lib::config::protocol::ProtocolConfig::builder();
        if let Some(reconnect_interval) = self.reconnect_interval {
            protocol_config_builder =
                protocol_config_builder.reconnect_interval(reconnect_interval);
        }
        if let Some(connect_timeout) = self.connect_timeout {
            protocol_config_builder = protocol_config_builder.connect_timeout(connect_timeout);
        }
        if let Some(ping_interval) = self.ping_interval {
            protocol_config_builder = protocol_config_builder.ping_interval(ping_interval)
        }
        if let Some(ping_timeout) = self.ping_timeout {
            protocol_config_builder = protocol_config_builder.ping_timeout(ping_timeout)
        }

        protocol_config_builder.build()
    }
}
