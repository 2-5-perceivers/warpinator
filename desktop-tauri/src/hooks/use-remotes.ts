import { listen } from "@tauri-apps/api/event";
import { invoke } from "@tauri-apps/api/core";
import { useEffect, useState } from "react";
import { WarpEvent } from "@/types/warp-event";
import { Remote } from "@/types/remote";

export function useRemotes() {
  const [remotes, setRemotes] = useState<Remote[]>([]);

  useEffect(() => {
    const fetchRemotes = async () => {
      const list = await invoke<Remote[]>("get_remotes");
      setRemotes(
        list.filter((remote) => {
          return !(
            remote.state.type == "error" &&
            remote.state.content == "group_code_mismatch"
          );
        }),
      );
    };

    fetchRemotes();

    const unlistenWarp = listen<WarpEvent>("warp-event", async (event) => {
      const ev = event.payload;
      if ("RemoteAdded" in ev || "RemoteUpdated" in ev) {
        await fetchRemotes();
      }
    });

    const unlistenRestart = listen("service-restarted", async () => {
      await fetchRemotes();
    });

    return () => {
      unlistenWarp.then((f) => f());
      unlistenRestart.then((f) => f());
    };
  }, []);

  return remotes;
}
