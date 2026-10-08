use std::io::Cursor;
use std::path::Path;

use image::{ImageFormat, ImageReader};
use serde::Serialize;
use tauri::{Manager, State};
use tauri_plugin_dialog::DialogExt;
use tauri_plugin_store::StoreExt;

use crate::server::UserConfigHandle;

#[derive(Serialize, Clone)]
pub struct ThemeSettings {
    pub theme: String,
}

#[derive(Serialize, Clone)]
pub struct Configuration {
    pub group_code: String,
    pub hostname: String,
    pub username: String,
    pub display_name: String,
    pub use_compression: bool,
}

#[derive(Serialize, Clone)]
pub struct RegistrationInfo {
    pub ip: String,
    pub port: u16,
}

#[tauri::command]
pub fn get_theme_settings(settings: State<'_, ThemeSettings>) -> ThemeSettings {
    settings.inner().clone()
}

#[tauri::command]
pub async fn get_registration_info(
    config: State<'_, UserConfigHandle>,
) -> Result<RegistrationInfo, String> {
    let config = config.get();
    Ok(RegistrationInfo {
        ip: config.bind_addr_v4.map(|a| a.to_string()).ok_or("IPv4 bind address not found")?,
        port: config.reg_port,
    })
}

#[tauri::command]
pub async fn get_user_config(config: State<'_, UserConfigHandle>) -> Result<Configuration, String> {
    let config = config.get();
    Ok(Configuration {
        group_code: config.group_code.clone(),
        hostname: config.hostname.clone(),
        username: config.username.clone(),
        display_name: config.display_name.read().await.clone(),
        use_compression: config.use_compression(),
    })
}

#[tauri::command]
pub async fn update_user_profile_picture(
    app_handle: tauri::AppHandle,
    config: State<'_, UserConfigHandle>,
    path: &Path,
) -> Result<(), String> {
    let avatar_path =
        app_handle.path().app_data_dir().map_err(|e| e.to_string())?.join("avatar.png");

    let path = path.to_owned();

    let image = tokio::task::spawn_blocking(move || {
        let img = ImageReader::open(path).map_err(|e| e.to_string())?.decode().map_err(|e| e.to_string())?;
        let resized = img.resize_to_fill(256, 256,  image::imageops::FilterType::Lanczos3);

        let mut png_bytes: Vec<u8> = Vec::new();

        resized.write_to(&mut Cursor::new(&mut png_bytes), ImageFormat::Png)
            .map_err(|e| e.to_string())?;

        std::fs::write(avatar_path, &png_bytes).map_err(|e| e.to_string())?;

        Ok::<_, String>(png_bytes)
    }).await.map_err(|e| format!("Task panicked: {}", e))? // Handle JoinError
        ?;
    config.get().set_picture(Some(&*image)).await;

    app_handle
        .store("settings.json")
        .map_err(|_| "Failed to get store")?
        .set("profile-picture", "avatar.png");

    Ok(())
}

#[tauri::command]
pub async fn select_user_profile_picture(
    app_handle: tauri::AppHandle,
    config: State<'_, UserConfigHandle>,
) -> Result<(), String> {
    let app = app_handle.clone();

    let picked_image = tokio::task::spawn_blocking(move || {
        app_handle
            .dialog()
            .file()
            .set_title("Select Profile Picture")
            .add_filter("Image Files", &["png", "jpg", "jpeg"])
            .blocking_pick_file()
    })
    .await
    .map_err(|e| e.to_string())?;

    if let Some(path) = picked_image {
        update_user_profile_picture(
            app,
            config,
            path.into_path().map_err(|_| "Invalid path")?.as_ref(),
        )
        .await?;
    }

    Ok(())
}

#[tauri::command]
pub async fn clear_user_profile_picture(
    app_handle: tauri::AppHandle,
    config: State<'_, UserConfigHandle>,
) -> Result<(), String> {
    let avatar_path =
        app_handle.path().app_data_dir().map_err(|e| e.to_string())?.join("avatar.png");

    app_handle.store("settings.json").map_err(|_| "Failed to get store")?.delete("profile-picture");
    tokio::fs::remove_file(avatar_path).await.map_err(|e| e.to_string())?;
    config.get().set_picture(None).await;

    Ok(())
}

#[tauri::command]
pub async fn update_user_display_name(
    app_handle: tauri::AppHandle,
    config: State<'_, UserConfigHandle>,
    name: String,
) -> Result<(), String> {
    config.get().set_display_name(&*name).await;
    app_handle.store("settings.json").map_err(|_| "Failed to get store")?.set("display-name", name);

    Ok(())
}

#[tauri::command]
pub async fn update_user_compression(
    app_handle: tauri::AppHandle,
    config: State<'_, UserConfigHandle>,
    enabled: bool,
) -> Result<(), String> {
    config.get().set_use_compression(enabled);
    app_handle
        .store("settings.json")
        .map_err(|_| "Failed to get store")?
        .set("use-compression", enabled);

    Ok(())
}
