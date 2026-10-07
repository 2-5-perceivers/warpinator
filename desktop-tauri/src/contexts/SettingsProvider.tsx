import React, {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { load } from "@tauri-apps/plugin-store";
import { invoke } from "@tauri-apps/api/core";
import { getConfiguration, type Configuration } from "@/lib/configuration.ts";
import { path } from "@tauri-apps/api";

type AutoAcceptOption = "none" | "favorites" | "all";

type Settings = Configuration & {
  defaultDestination: string;
  notificationsTransfers: boolean;
  notificationsMessages: boolean;
  overwrite: boolean;
  autoAccept: AutoAcceptOption;
  favorites: string[];
  portTransfers: string;
  portRegistration: string;
  avatarVersion: number;
};

type SettingsContextValue = {
  settings?: Settings;
  isLoading: boolean;
  setDisplayName: (name: string) => Promise<void>;
  setGroupCode: (code: string) => Promise<void>;
  setDefaultDestination: (path: string) => Promise<void>;
  setNotificationsTransfers: (enabled: boolean) => Promise<void>;
  setNotificationsMessages: (enabled: boolean) => Promise<void>;
  setOverwrite: (enabled: boolean) => Promise<void>;
  setUseCompression: (enabled: boolean) => Promise<void>;
  setAutoAccept: (value: AutoAcceptOption) => Promise<void>;
  toggleFavorite: (remoteUuid: string) => Promise<void>;
  isFavorite: (remoteUuid: string) => boolean;
  setPortTransfers: (port: string) => Promise<void>;
  setPortRegistration: (port: string) => Promise<void>;
  bumpAvatarVersion: () => void;
};

const settingsStore = await load("settings.json", {
  autoSave: true,
  defaults: {},
});

const SettingsContext = createContext<SettingsContextValue | undefined>(
  undefined,
);

async function readSetting<T>(key: string, fallback: T): Promise<T> {
  const value = await settingsStore.get<T>(key);
  return (value as T | undefined) ?? fallback;
}

function normalizeFavorites(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return [
    ...new Set(
      value.filter((item): item is string => typeof item === "string"),
    ),
  ];
}

export function SettingsProvider({ children }: { children: React.ReactNode }) {
  const [settings, setSettings] = useState<Settings>();
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let unlistenProfile: (() => void) | undefined;
    let unlistenDisplay: (() => void) | undefined;

    const loadSettings = async () => {
      try {
        const config = await getConfiguration();

        const initial: Settings = {
          ...config,
          display_name: await readSetting("display-name", config.display_name),
          group_code: await readSetting(
            "group-code",
            config.group_code ?? "Warpinator",
          ),
          defaultDestination: await readSetting(
            "default-destination",
            await path
              .downloadDir()
              .then((dir) => path.join(dir, "Warpinator")),
          ),
          notificationsTransfers: await readSetting(
            "notifications-transfers",
            true,
          ),
          notificationsMessages: await readSetting(
            "notifications-messages",
            true,
          ),
          overwrite: await readSetting("overwrite", true),
          use_compression: await readSetting(
            "use-compression",
            config.use_compression ?? false,
          ),
          autoAccept: await readSetting<AutoAcceptOption>(
            "auto-accept",
            "none",
          ),
          favorites: normalizeFavorites(
            await readSetting<unknown>("favorites", []),
          ),
          portTransfers: await readSetting("port-transfers", "42000"),
          portRegistration: await readSetting("port-registration", "42001"),
          avatarVersion: Date.now(),
        };

        setSettings(initial);

        unlistenProfile = await settingsStore.onKeyChange(
          "profile-picture",
          () => {
            setSettings((prev) =>
              prev ? { ...prev, avatarVersion: Date.now() } : prev,
            );
          },
        );

        unlistenDisplay = await settingsStore.onKeyChange(
          "display-name",
          (value) => {
            if (typeof value === "string") {
              setSettings((prev) =>
                prev ? { ...prev, display_name: value } : prev,
              );
            }
          },
        );
      } catch (err) {
        console.error("Failed to load settings", err);
      } finally {
        setIsLoading(false);
      }
    };

    loadSettings();

    return () => {
      unlistenProfile?.();
      unlistenDisplay?.();
    };
  }, []);

  const persist = async <T,>(
    key: string,
    value: T,
    update: (prev: Settings) => Settings,
  ) => {
    await settingsStore.set(key, value);
    setSettings((prev) => (prev ? update(prev) : prev));
  };

  const value = useMemo<SettingsContextValue>(
    () => ({
      settings,
      isLoading,
      setDisplayName: async (name: string) => {
        const trimmed = name.trim();
        if (!trimmed) return;
        await invoke("update_user_display_name", { name: trimmed });
        await settingsStore.set("display-name", trimmed);
        setSettings((prev) =>
          prev ? { ...prev, display_name: trimmed } : prev,
        );
      },
      setGroupCode: async (code: string) => {
        await persist("group-code", code, (prev) => ({
          ...prev,
          group_code: code,
        }));
      },
      setDefaultDestination: async (path: string) => {
        await persist("default-destination", path, (prev) => ({
          ...prev,
          defaultDestination: path,
        }));
      },
      setNotificationsTransfers: async (enabled: boolean) => {
        await persist("notifications-transfers", enabled, (prev) => ({
          ...prev,
          notificationsTransfers: enabled,
        }));
      },
      setNotificationsMessages: async (enabled: boolean) => {
        await persist("notifications-messages", enabled, (prev) => ({
          ...prev,
          notificationsMessages: enabled,
        }));
      },
      setOverwrite: async (enabled: boolean) => {
        await persist("overwrite", enabled, (prev) => ({
          ...prev,
          overwrite: enabled,
        }));
      },
      setUseCompression: async (enabled: boolean) => {
        await invoke("update_user_compression", { enabled });
        await persist("use-compression", enabled, (prev) => ({
          ...prev,
          use_compression: enabled,
        }));
      },
      setAutoAccept: async (value: AutoAcceptOption) => {
        await persist("auto-accept", value, (prev) => ({
          ...prev,
          autoAccept: value,
        }));
      },
      toggleFavorite: async (remoteUuid: string) => {
        const value = remoteUuid.trim();
        if (!value) return;

        const nextFavorites = settings?.favorites.includes(value)
          ? settings.favorites.filter((uuid) => uuid !== value)
          : [...(settings?.favorites ?? []), value];

        const normalizedFavorites = normalizeFavorites(nextFavorites);
        await settingsStore.set("favorites", normalizedFavorites);
        setSettings((prev) =>
          prev ? { ...prev, favorites: normalizedFavorites } : prev,
        );
      },
      isFavorite: (remoteUuid: string) =>
        settings?.favorites.includes(remoteUuid) ?? false,
      setPortTransfers: async (port: string) => {
        await persist("port-transfers", port, (prev) => ({
          ...prev,
          portTransfers: port,
        }));
      },
      setPortRegistration: async (port: string) => {
        await persist("port-registration", port, (prev) => ({
          ...prev,
          portRegistration: port,
        }));
      },
      bumpAvatarVersion: () =>
        setSettings((prev) =>
          prev ? { ...prev, avatarVersion: Date.now() } : prev,
        ),
    }),
    [isLoading, settings],
  );

  return (
    <SettingsContext.Provider value={value}>
      {children}
    </SettingsContext.Provider>
  );
}

export function useSettings() {
  const ctx = useContext(SettingsContext);
  if (!ctx)
    throw new Error("useSettings must be used within a SettingsProvider");
  return ctx;
}
