import React, { useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button.tsx";
import {
  Field,
  FieldContent,
  FieldDescription,
  FieldGroup,
  FieldLabel,
  FieldSeparator,
  FieldSet,
} from "@/components/ui/field.tsx";
import { Input } from "@/components/ui/input.tsx";
import {
  Avatar,
  AvatarFallback,
  AvatarImage,
} from "@/components/ui/avatar.tsx";
import { Folder02Icon, UserIcon } from "@hugeicons/core-free-icons";
import { HugeiconsIcon } from "@hugeicons/react";
import { Switch } from "@/components/ui/switch.tsx";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group.tsx";
import { useTheme } from "@/contexts/ThemeProvider.tsx";
import { useSettings } from "@/contexts/SettingsProvider.tsx";
import { invoke } from "@tauri-apps/api/core";
import { open as openDialog } from "@tauri-apps/plugin-dialog";

export function SettingsContent() {
  const { theme, setTheme } = useTheme();
  const {
    settings,
    isLoading,
    setDisplayName,
    setGroupCode,
    setDefaultDestination,
    setNotificationsMessages,
    setNotificationsTransfers,
    setOverwrite,
    setUseCompression,
    setAutoAccept,
    setPortTransfers,
    setPortRegistration,
    bumpAvatarVersion,
  } = useSettings();
  const [displayName, setDisplayNameInput] = useState(
    settings?.display_name ?? "",
  );

  const onThemeChange = (theme: string) => {
    if (theme.trim() == "") return;
    setTheme(theme as "dark" | "light" | "system");
  };

  useEffect(() => {
    if (settings) setDisplayNameInput(settings.display_name);
  }, [settings?.display_name]);

  const onDisplayNameCommit = async () => {
    if (!settings) return;
    if (displayName.trim() === settings.display_name) return;
    await setDisplayName(displayName);
  };

  const onDisplayNameKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === "Enter") {
      e.preventDefault();
      onDisplayNameCommit();
    }
  };

  const changeProfilePicture = async () => {
    await invoke("select_user_profile_picture");
    bumpAvatarVersion();
  };

  const selectDefaultDestination = async () => {
    const selected = await openDialog({ directory: true, multiple: false });
    if (typeof selected === "string") {
      await setDefaultDestination(selected);
    }
  };

  const avatarSrc = useMemo(() => {
    if (settings) return `avatars://self?v=${settings.avatarVersion}`;
    return undefined;
  }, [settings?.avatarVersion]);

  const disabled = isLoading || !settings;

  return (
    <div className="h-full overflow-hidden">
      <div className="no-scrollbar h-full overflow-y-auto px-4 pb-4">
        <FieldGroup>
          <FieldSet>
            <FieldLabel>User</FieldLabel>
            <FieldDescription>
              Customize how you are seen on the network
            </FieldDescription>
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="display_name">Display name</FieldLabel>
                <Input
                  id="display_name"
                  type="text"
                  placeholder="Warpinator User"
                  value={displayName}
                  onChange={(event) => setDisplayNameInput(event.target.value)}
                  onBlur={onDisplayNameCommit}
                  onKeyDown={onDisplayNameKeyDown}
                  disabled={disabled}
                />
              </Field>
              <Field orientation="horizontal">
                <FieldLabel>Profile picture</FieldLabel>
                <Avatar
                  onClick={changeProfilePicture}
                  className="h-8 w-8 rounded-lg after:border-0 bg-sidebar-primary cursor-pointer"
                >
                  {avatarSrc ? (
                    <AvatarImage
                      src={avatarSrc}
                      alt={settings?.display_name}
                      className="rounded-lg"
                    />
                  ) : (
                    <AvatarFallback className="rounded-lg bg-transparent text-sidebar-primary-foreground">
                      <HugeiconsIcon icon={UserIcon} />
                    </AvatarFallback>
                  )}
                </Avatar>
              </Field>
            </FieldGroup>
          </FieldSet>
          <FieldSeparator />
          <FieldSet>
            <FieldLabel>Transfers and messages</FieldLabel>
            <FieldDescription>Change how transfer behave</FieldDescription>
            <FieldGroup>
              <Field orientation="horizontal">
                <FieldContent>
                  <FieldLabel>Default destination</FieldLabel>
                  <FieldDescription>
                    {settings?.defaultDestination ?? ""}
                  </FieldDescription>
                </FieldContent>
                <Button
                  size="icon"
                  variant="outline"
                  onClick={selectDefaultDestination}
                  disabled={disabled}
                >
                  <HugeiconsIcon icon={Folder02Icon} />
                </Button>
              </Field>
              <Field orientation="horizontal">
                <FieldLabel htmlFor="notifications_transfers">
                  Show notifications for transfers
                </FieldLabel>
                <Switch
                  id="notifications_transfers"
                  checked={!!settings?.notificationsTransfers}
                  onCheckedChange={(checked) =>
                    setNotificationsTransfers(Boolean(checked))
                  }
                  disabled={disabled}
                />
              </Field>
              <Field orientation="horizontal">
                <FieldLabel htmlFor="notifications_messages">
                  Show notifications for messages
                </FieldLabel>
                <Switch
                  id="notifications_messages"
                  checked={!!settings?.notificationsMessages}
                  onCheckedChange={(checked) =>
                    setNotificationsMessages(Boolean(checked))
                  }
                  disabled={disabled}
                />
              </Field>
              <Field orientation="horizontal">
                <FieldLabel htmlFor="overwrite">
                  Check and warn if files already exists
                </FieldLabel>
                <Switch
                  id="overwrite"
                  checked={!!settings?.overwrite}
                  onCheckedChange={(checked) => setOverwrite(Boolean(checked))}
                  disabled={disabled}
                />
              </Field>
              <Field orientation="horizontal">
                <FieldLabel htmlFor="use_compression">
                  Try to use compression
                </FieldLabel>
                <Switch
                  id="use_compression"
                  checked={!!settings?.use_compression}
                  onCheckedChange={(checked) =>
                    setUseCompression(Boolean(checked))
                  }
                  disabled={disabled}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="autoaccept">
                  Auto accept transfers from
                </FieldLabel>
                <ToggleGroup
                  type="single"
                  variant="outline"
                  id="autoaccept"
                  value={settings?.autoAccept ?? "none"}
                  onValueChange={(value) => {
                    if (value)
                      setAutoAccept(value as "none" | "favorites" | "all");
                  }}
                  disabled={disabled}
                >
                  <ToggleGroupItem value="none" className="flex-1/3">
                    None
                  </ToggleGroupItem>
                  <ToggleGroupItem value="favorites" className="flex-1/3">
                    Favourites
                  </ToggleGroupItem>
                  <ToggleGroupItem value="all" className="flex-1/3">
                    All
                  </ToggleGroupItem>
                </ToggleGroup>
              </Field>
            </FieldGroup>
          </FieldSet>
          <FieldSeparator />
          <FieldSet>
            <FieldLabel>Network</FieldLabel>
            <FieldDescription>Change network interactions</FieldDescription>
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="group_code">Group code</FieldLabel>
                <Input
                  id="group_code"
                  type="text"
                  placeholder="Warpinator"
                  value={settings?.group_code ?? ""}
                  onChange={(event) => setGroupCode(event.target.value)}
                  disabled={disabled}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="port_transfers">
                  Port for transfers
                </FieldLabel>
                <Input
                  id="port_transfers"
                  type="text"
                  placeholder="42000"
                  value={settings?.portTransfers ?? ""}
                  onChange={(event) => setPortTransfers(event.target.value)}
                  disabled={disabled}
                />
              </Field>
              <Field>
                <FieldLabel htmlFor="port_registration">
                  Port for registration
                </FieldLabel>
                <Input
                  id="port_registration"
                  type="text"
                  placeholder="42001"
                  value={settings?.portRegistration ?? ""}
                  onChange={(event) => setPortRegistration(event.target.value)}
                  disabled={disabled}
                />
              </Field>
            </FieldGroup>
          </FieldSet>
          <FieldSeparator />
          <FieldSet>
            <FieldLabel>Aspect</FieldLabel>
            <FieldGroup>
              <Field>
                <FieldLabel htmlFor="theme">Theme</FieldLabel>
                <ToggleGroup
                  type="single"
                  variant="outline"
                  id="theme"
                  value={theme}
                  onValueChange={onThemeChange}
                >
                  <ToggleGroupItem value="system" className="flex-1/3">
                    System
                  </ToggleGroupItem>
                  <ToggleGroupItem value="light" className="flex-1/3">
                    Light
                  </ToggleGroupItem>
                  <ToggleGroupItem value="dark" className="flex-1/3">
                    Dark
                  </ToggleGroupItem>
                </ToggleGroup>
              </Field>
            </FieldGroup>
          </FieldSet>
        </FieldGroup>
      </div>
    </div>
  );
}
