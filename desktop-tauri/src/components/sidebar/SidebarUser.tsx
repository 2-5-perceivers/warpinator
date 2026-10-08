import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  SidebarMenu,
  SidebarMenuButton,
  SidebarMenuItem,
  useSidebar,
} from "@/components/ui/sidebar";
import { HugeiconsIcon } from "@hugeicons/react";
import {
  ArrowReloadHorizontalIcon,
  InformationSquareIcon,
  Link01Icon,
  LogoutSquare01Icon,
  Settings01Icon,
  UserIcon,
} from "@hugeicons/core-free-icons";
import { useEffect, useMemo, useState } from "react";
import { exit } from "@tauri-apps/plugin-process";
import { useSettings } from "@/contexts/SettingsProvider.tsx";
import { WebviewWindow } from "@tauri-apps/api/webviewWindow";
import { invoke } from "@tauri-apps/api/core";
import { toast } from "sonner";

export function SidebarUser({
  setManualConnectionDialogOpen,
}: {
  setManualConnectionDialogOpen: (open: boolean) => void;
}) {
  const { isMobile } = useSidebar();
  const { settings } = useSettings();
  const [isRestarting, setIsRestarting] = useState(false);

  const restartService = async () => {
    if (isRestarting) return;
    setIsRestarting(true);
    const toastId = toast.loading("Restarting Warpinator service...");
    try {
      await invoke("restart_service");
      toast.success("Warpinator service restarted", { id: toastId });
    } catch (err) {
      console.error("Failed to restart service", err);
      toast.error("Failed to restart service", { id: toastId });
    } finally {
      setIsRestarting(false);
    }
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.altKey && e.key.toLowerCase() === "r") {
        e.preventDefault();
        restartService();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isRestarting]);

  const quitApp = () => {
    exit(0).catch(() => {
      console.log("Failed to quit the app");
    });
  };

  const openSettingsWindow = async () => {
    const settingsWindow = await WebviewWindow.getByLabel("settings");
    if (!settingsWindow) return;
    await settingsWindow.show();
    await settingsWindow.setFocus();
  };

  const openAboutWindow = async () => {
    const about = await WebviewWindow.getByLabel("about");
    if (!about) return;
    await about.show();
    await about.setFocus();
  };

  const avatarSrc = useMemo(() => {
    if (settings) return `avatars://self?v=${settings.avatarVersion}`;
    return undefined;
  }, [settings?.avatarVersion]);

  return (
    <>
      <SidebarMenu>
        <SidebarMenuItem>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <SidebarMenuButton
                size="lg"
                className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
              >
                <Avatar className="h-8 w-8 rounded-lg after:border-0 bg-sidebar-primary">
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
                <div className="grid flex-1 text-left text-sm leading-tight">
                  <span className="truncate font-medium">
                    {settings?.display_name}
                  </span>
                  <span className="truncate text-xs">
                    {settings
                      ? `${settings.username}@${settings.hostname}`
                      : ""}
                  </span>
                </div>
                <HugeiconsIcon icon={Settings01Icon} />
              </SidebarMenuButton>
            </DropdownMenuTrigger>
            <DropdownMenuContent
              side={isMobile ? "bottom" : "right"}
              align="end"
              sideOffset={4}
            >
              <DropdownMenuGroup>
                <DropdownMenuItem
                  onSelect={() => setManualConnectionDialogOpen(true)}
                >
                  <HugeiconsIcon icon={Link01Icon} />
                  Manual connection
                </DropdownMenuItem>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuGroup>
                <DropdownMenuItem
                  onSelect={restartService}
                  disabled={isRestarting}
                >
                  <HugeiconsIcon
                    icon={ArrowReloadHorizontalIcon}
                    className={isRestarting ? "animate-spin" : undefined}
                  />
                  Restart
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={openSettingsWindow}>
                  <HugeiconsIcon icon={Settings01Icon} />
                  Settings
                </DropdownMenuItem>
                <DropdownMenuItem onSelect={openAboutWindow}>
                  <HugeiconsIcon icon={InformationSquareIcon} />
                  About
                </DropdownMenuItem>
              </DropdownMenuGroup>
              <DropdownMenuSeparator />
              <DropdownMenuItem onSelect={quitApp}>
                <HugeiconsIcon icon={LogoutSquare01Icon} />
                Quit
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </SidebarMenuItem>
      </SidebarMenu>
    </>
  );
}
