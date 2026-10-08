use tauri::http::{Request, Response, header};
use tauri::{Manager, Runtime, UriSchemeContext, UriSchemeResponder};

use crate::server::{RemoteManagerHandle, UserConfigHandle};

pub fn avatars_protocol_handler<'a, R: Runtime>(
    ctx: UriSchemeContext<R>,
    request: Request<Vec<u8>>,
    responder: UriSchemeResponder,
) {
    let remote_manger = ctx.app_handle().state::<RemoteManagerHandle>().get();
    let configuration = ctx.app_handle().state::<UserConfigHandle>().get();
    let remote_uuid = request.uri().authority().map(|a| a.as_str()).unwrap_or_default().to_string();

    tauri::async_runtime::spawn(async move {
        let response: Response<Vec<u8>>;

        if remote_uuid == "self" {
            response = match configuration.picture.read().await.as_deref() {
                Some(picture) => Response::builder()
                    .header(header::CONTENT_TYPE, "image/png")
                    .body(picture.to_vec())
                    .unwrap(),
                None => Response::builder().status(404).body(Vec::new()).unwrap(),
            }
        } else {
            let remote = remote_manger.remote(remote_uuid.as_str()).await;

            let picture = match remote {
                Some(remote) if remote.picture.is_some() => {
                    Some(remote.picture.unwrap().read().await.clone())
                }
                _ => None,
            };

            response = match picture {
                Some(picture) => Response::builder()
                    .header(header::CONTENT_TYPE, "image/png")
                    .body(picture)
                    .unwrap(),
                None => Response::builder().status(404).body(Vec::new()).unwrap(),
            };
        }

        responder.respond(response);
    });
}
