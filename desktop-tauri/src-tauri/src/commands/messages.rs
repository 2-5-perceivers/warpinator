use tauri::State;
use warpinator_lib::types::message::Message;

use crate::server::RemoteManagerHandle;

#[tauri::command]
pub async fn send_message(
    remotes: State<'_, RemoteManagerHandle>,
    remote_uuid: String,
    content: String,
) -> Result<(), String> {
    let remote = remotes.get().get_worker(remote_uuid.as_str()).await.ok_or("No remote found")?;
    remote.send_message(content.as_str()).await.map_err(|e| e.to_string())?;

    Ok(())
}

#[tauri::command]
pub async fn get_messages(
    remotes: State<'_, RemoteManagerHandle>,
    remote_uuid: String,
) -> Result<Vec<Message>, String> {
    Ok(remotes.get().messages(remote_uuid.as_str()).await.unwrap_or_default())
}

#[tauri::command]
pub async fn remove_message(
    remotes: State<'_, RemoteManagerHandle>,
    remote_uuid: String,
    message_uuid: String,
) -> Result<(), String> {
    remotes
        .get()
        .remove_message(remote_uuid.as_str(), message_uuid.as_str())
        .await
        .map_err(|e| e.to_string())
}
