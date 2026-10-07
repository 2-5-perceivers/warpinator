import { invoke } from "@tauri-apps/api/core";

export interface Configuration {
  group_code: string;
  hostname: string;
  username: string;
  display_name: string;
  use_compression: boolean;
}

export async function getConfiguration() {
  return await invoke<Configuration>("get_user_config");
}
