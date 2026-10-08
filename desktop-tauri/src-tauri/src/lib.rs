use std::sync::Arc;

use tauri::image::Image;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::TrayIconBuilder;
use tauri::{AppHandle, Manager};
use tauri_plugin_store::StoreExt;
use tokio::sync::oneshot;
use warpinator_lib::WarpinatorServer;

use crate::commands::messages::*;
use crate::commands::remotes::*;
use crate::commands::settings::*;
use crate::commands::transfers::*;
use crate::server::*;

#[macro_use]
mod commands;
mod avatars;
mod events;
mod server;

fn spawn_tray(app: &AppHandle) -> Result<(), Box<dyn std::error::Error>> {
    let icon = include_bytes!("../icons/symbolic.png");

    let show_i = MenuItem::with_id(app, "show", "Show", true, None::<&str>)?;
    let quit_i = MenuItem::with_id(app, "quit", "Quit", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&show_i, &quit_i])?;

    let _ = TrayIconBuilder::new()
        .icon(Image::from_bytes(icon)?)
        .menu(&menu)
        .on_menu_event(|app, event| match event.id.as_ref() {
            "show" => {
                let window = app.get_webview_window("main").expect("no main window");
                let _ = window.show();
                let _ = window.set_focus();
            }
            "quit" => {
                app.exit(0);
            }
            _ => {}
        })
        .build(app)?;
    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            let window = app.get_webview_window("main").expect("no main window");
            let _ = window.show();
            let _ = window.set_focus();
        }))
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_clipboard_manager::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_process::init())
        .plugin(tauri_plugin_store::Builder::new().build())
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_os::init())
        .invoke_handler(tauri::generate_handler![
            // Remotes
            get_remote,
            get_remotes,
            connect_remote,
            manual_connect_remote,
            // Transfers
            new_transfer,
            get_transfers,
            accept_transfer,
            cancel_transfer,
            stop_transfer,
            remove_transfer,
            open_transfer,
            // Messages
            send_message,
            get_messages,
            remove_message,
            // Settings
            get_theme_settings,
            get_registration_info,
            get_user_config,
            update_user_profile_picture,
            select_user_profile_picture,
            clear_user_profile_picture,
            update_user_display_name,
            update_user_compression,
            // Server
            restart_service,
        ])
        .register_asynchronous_uri_scheme_protocol("avatars", avatars::avatars_protocol_handler)
        .setup(|app| {
            let handle = app.handle().clone();

            let store = app.store("settings.json")?;

            // Reading settings
            let theme = store
                .get("ui-theme")
                .and_then(|v| v.as_str().map(|s| s.to_string()))
                .unwrap_or_else(|| "system".to_string());

            let hostname = whoami::hostname().unwrap_or_else(|_| "warpinator".to_string());

            let service_id = store
                .get("service-id")
                .and_then(|v| v.as_str().map(|s| s.to_string()))
                .unwrap_or_else(|| {
                    let clean = hostname.replace(' ', "-").replace('_', "-").to_uppercase();
                    let clean = &clean[..clean.len().min(30)];
                    let id = uuid::Uuid::new_v4().simple().to_string().to_uppercase();
                    let s_uuid = format!("{}-{}", clean, id);
                    store.set("service-id", s_uuid.clone());
                    s_uuid
                });

            let user_config = build_user_config(&handle)?;
            let user_config_handle = UserConfigHandle::new(user_config.clone());

            let server = WarpinatorServer::builder()
                .user_config(user_config)
                .service_name(&service_id)
                .build()
                .expect("failed to build server");

            let remote_manager = server.remotes.clone();
            let remote_manager_handle = RemoteManagerHandle::new(remote_manager.clone());
            let shutdown_tx = Arc::new(std::sync::Mutex::new(None));
            let server_task = Arc::new(std::sync::Mutex::new(None));

            let server_state = ServerState {
                remote_manager: remote_manager_handle.clone(),
                user_config: user_config_handle.clone(),
                shutdown_tx: shutdown_tx.clone(),
                server_task: server_task.clone(),
                service_id,
            };

            let (init_shutdown_tx, init_shutdown_rx) = oneshot::channel::<()>();
            *shutdown_tx.lock().unwrap() = Some(init_shutdown_tx);

            let warp_events = remote_manager.subscribe();
            let service_app_handle = handle.clone();
            let task = spawn_server_task(
                remote_manager,
                service_app_handle,
                warp_events,
                server,
                init_shutdown_rx,
            );

            *server_task.lock().unwrap() = Some(task);

            handle.manage(remote_manager_handle);
            handle.manage(user_config_handle);
            handle.manage(server_state);
            handle.manage(ThemeSettings { theme });

            tauri::async_runtime::spawn_blocking(move || {
                let _ = spawn_tray(&handle);
            });

            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                window.hide().unwrap();
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
