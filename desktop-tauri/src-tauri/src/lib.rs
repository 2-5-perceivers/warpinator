use std::fs;
use std::sync::Arc;

use tauri::image::Image;
use tauri::menu::{Menu, MenuItem};
use tauri::tray::TrayIconBuilder;
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_notification::NotificationExt;
use tauri_plugin_store::StoreExt;
use tokio::sync::oneshot;
use warpinator_lib::WarpinatorServer;
use warpinator_lib::config::user::UserConfig;

use crate::commands::messages::*;
use crate::commands::remotes::*;
use crate::commands::settings::*;
use crate::commands::transfers::*;
use crate::events::HandleEvent;

#[macro_use]
mod commands;
mod avatars;
mod events;

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

            let group_code = store
                .get("group-code")
                .and_then(|v| v.as_str().map(|s| s.to_string()))
                .unwrap_or_else(|| "Warpinator".to_string());

            let display_name = store
                .get("display-name")
                .and_then(|v| v.as_str().map(|s| s.to_string()))
                .unwrap_or_else(|| whoami::realname().unwrap_or("Warpinator".to_string()));

            let use_compression =
                store.get("use-compression").and_then(|v| v.as_bool()).unwrap_or(false);

            let profile_picture = store.get("profile-picture").and_then(|v| {
                fs::read(handle.path().app_data_dir().ok()?.to_path_buf().join(v.as_str().unwrap()))
                    .ok()
            });

            let username = whoami::username().unwrap_or_else(|_| "warpinator".to_string());
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

            let mut user_config_builder = UserConfig::builder()
                .default_bind_addr_v4()
                .default_bind_addr_v6()
                .hostname(&hostname)
                .username(&username)
                .display_name(&display_name)
                .group_code(&group_code)
                .use_compression(use_compression);

            if let Some(picture) = profile_picture {
                user_config_builder = user_config_builder.picture(&*picture);
            }

            let user_config = user_config_builder.build();

            let server = WarpinatorServer::builder()
                .user_config(user_config.clone())
                .service_name(&service_id)
                .build()
                .expect("failed to build server");

            let remote_manager = server.remotes.clone();
            let mut warp_events = server.remotes.subscribe();
            let (shutdown_tx, shutdown_rx) = oneshot::channel::<()>();

            handle.manage(Arc::new(shutdown_tx));
            handle.manage(remote_manager);
            handle.manage(user_config);
            handle.manage(ThemeSettings { theme });

            let service_app_handle = handle.clone();

            tauri::async_runtime::spawn(async move {
                let remote_manager = server.remotes.clone();
                // Handle events
                tokio::spawn(async move {
                    let store = service_app_handle.store("settings.json").unwrap();
                    let path = service_app_handle.path();
                    let notification_ext = service_app_handle.notification();

                    while let Ok(ev) = warp_events.recv().await {
                        let _ = service_app_handle.emit("warp-event", &ev);
                        let auto_accepted =
                            ev.try_auto_accept(store.as_ref(), path, &remote_manager).await;

                        if let Ok(false) = auto_accepted {
                            let _ = ev
                                .try_notify(store.as_ref(), &notification_ext, &remote_manager)
                                .await;
                        }
                    }
                });

                server
                    .serve_with_shutdown(async move {
                        let _ = shutdown_rx.await;
                    })
                    .await
                    .expect("server error");
            });

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
