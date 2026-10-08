use std::sync::{Arc, Mutex, RwLock};

use tauri::async_runtime::JoinHandle;
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_notification::NotificationExt;
use tauri_plugin_store::StoreExt;
use tokio::sync::oneshot;
use warpinator_lib::WarpinatorServer;
use warpinator_lib::config::user::UserConfig;
use warpinator_lib::remote_manager::{RemoteManager, WarpEvent};

use crate::events::HandleEvent;

#[derive(Clone)]
pub struct RemoteManagerHandle(Arc<RwLock<RemoteManager>>);

impl RemoteManagerHandle {
    pub fn new(remote_manager: RemoteManager) -> Self {
        Self(Arc::new(RwLock::new(remote_manager)))
    }

    pub fn get(&self) -> RemoteManager {
        self.0.read().unwrap().clone()
    }

    pub fn set(&self, remote_manager: RemoteManager) {
        *self.0.write().unwrap() = remote_manager;
    }
}

#[derive(Clone)]
pub struct UserConfigHandle(Arc<RwLock<UserConfig>>);

impl UserConfigHandle {
    pub fn new(user_config: UserConfig) -> Self {
        Self(Arc::new(RwLock::new(user_config)))
    }

    pub fn get(&self) -> UserConfig {
        self.0.read().unwrap().clone()
    }

    pub fn set(&self, user_config: UserConfig) {
        *self.0.write().unwrap() = user_config;
    }
}

pub struct ServerState {
    pub remote_manager: RemoteManagerHandle,
    pub user_config: UserConfigHandle,
    pub shutdown_tx: Arc<Mutex<Option<oneshot::Sender<()>>>>,
    pub server_task: Arc<Mutex<Option<JoinHandle<()>>>>,
    pub service_id: String,
}

pub fn build_user_config(app: &AppHandle) -> Result<UserConfig, Box<dyn std::error::Error>> {
    let store = app.store("settings.json")?;

    let group_code = store
        .get("group-code")
        .and_then(|v| v.as_str().map(|s| s.to_string()))
        .unwrap_or_else(|| "Warpinator".to_string());

    let display_name = store
        .get("display-name")
        .and_then(|v| v.as_str().map(|s| s.to_string()))
        .unwrap_or_else(|| whoami::realname().unwrap_or("Warpinator".to_string()));

    let use_compression = store.get("use-compression").and_then(|v| v.as_bool()).unwrap_or(false);

    let profile_picture = store.get("profile-picture").and_then(|v| {
        std::fs::read(app.path().app_data_dir().ok()?.to_path_buf().join(v.as_str().unwrap())).ok()
    });

    let port = store
        .get("port-transfers")
        .and_then(|v| v.as_str().and_then(|s| s.parse::<u16>().ok()))
        .unwrap_or(42000);

    let reg_port = store
        .get("port-registration")
        .and_then(|v| v.as_str().and_then(|s| s.parse::<u16>().ok()))
        .unwrap_or(42001);

    let username = whoami::username().unwrap_or_else(|_| "warpinator".to_string());
    let hostname = whoami::hostname().unwrap_or_else(|_| "warpinator".to_string());

    let mut user_config_builder = UserConfig::builder()
        .port(port)
        .reg_port(reg_port)
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

    Ok(user_config_builder.build())
}

pub fn spawn_server_task<'a>(
    remote_manager: RemoteManager,
    service_app_handle: AppHandle,
    mut warp_events: tokio::sync::broadcast::Receiver<WarpEvent>,
    server: WarpinatorServer,
    shutdown_rx: oneshot::Receiver<()>,
) -> JoinHandle<()> {
    tauri::async_runtime::spawn(async move {
        let remote_manager = remote_manager.clone();
        let event_app_handle = service_app_handle.clone();
        let event_loop = tokio::spawn(async move {
            let store = event_app_handle.store("settings.json").unwrap();
            let path = event_app_handle.path();
            let notification_ext = event_app_handle.notification();

            while let Ok(ev) = warp_events.recv().await {
                let _ = event_app_handle.emit("warp-event", &ev);
                let auto_accepted = ev.try_auto_accept(store.as_ref(), path, &remote_manager).await;

                if let Ok(false) = auto_accepted {
                    let _ = ev.try_notify(store.as_ref(), &notification_ext, &remote_manager).await;
                }
            }
        });

        let serve_res = server
            .serve_with_shutdown(async move {
                let _ = shutdown_rx.await;
            })
            .await;

        event_loop.abort();

        if let Err(e) = serve_res {
            tracing::error!("Server error: {:?}", e);
        }
    })
}

pub fn start_server<'a>(
    app: &AppHandle,
    state: &'a ServerState,
) -> Result<JoinHandle<()>, Box<dyn std::error::Error + 'a>> {
    let user_config = build_user_config(app)?;
    state.user_config.set(user_config.clone());

    let server = WarpinatorServer::builder()
        .user_config(user_config)
        .service_name(&state.service_id)
        .build()?;

    let remote_manager = server.remotes.clone();
    state.remote_manager.set(remote_manager.clone());

    let warp_events = remote_manager.subscribe();
    let (shutdown_tx, shutdown_rx) = oneshot::channel::<()>();
    *state.shutdown_tx.lock()? = Some(shutdown_tx);

    let service_app_handle = app.clone();
    let task =
        spawn_server_task(remote_manager, service_app_handle, warp_events, server, shutdown_rx);

    Ok(task)
}

#[tauri::command]
pub async fn restart_service(
    app: AppHandle,
    state: tauri::State<'_, ServerState>,
) -> Result<(), String> {
    tracing::info!("Restarting Warpinator service...");

    if let Some(tx) = state.shutdown_tx.lock().unwrap().take() {
        let _ = tx.send(());
    }

    let task = state.server_task.lock().unwrap().take();
    if let Some(task) = task {
        let _ = task.await;
    }

    tokio::time::sleep(std::time::Duration::from_millis(200)).await;

    let new_task = start_server(&app, &state).map_err(|e| e.to_string())?;
    *state.server_task.lock().unwrap() = Some(new_task);

    let _ = app.emit("service-restarted", ());

    tracing::info!("Warpinator service restarted successfully");
    Ok(())
}
