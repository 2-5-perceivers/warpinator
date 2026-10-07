import { Transfer, TransferState } from "@/types/transfer.ts";
import { HugeiconsIcon } from "@hugeicons/react";
import {
  Cancel01Icon,
  Download01Icon,
  Folder01Icon,
  FolderOpenIcon,
  MoreVerticalIcon,
  Remove01Icon,
  StopIcon,
  Tick02Icon,
  Upload01Icon,
} from "@hugeicons/core-free-icons";
import { ButtonGroup } from "@/components/ui/button-group.tsx";
import { Button } from "@/components/ui/button.tsx";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Badge } from "@/components/ui/badge.tsx";
import { Field, FieldLabel } from "@/components/ui/field";
import { Progress } from "@/components/ui/progress.tsx";
import { useThrottle } from "@/hooks/use-throttle.ts";
import {
  Collapsible,
  CollapsibleContent,
} from "@/components/ui/collapsible.tsx";
import {
  acceptTransfer,
  cancelTransfer,
  openTransfer,
  removeTransfer,
  stopTransfer,
} from "@/lib/transfers.ts";
import { useSettings } from "@/contexts/SettingsProvider.tsx";
import { open as openDialog } from "@tauri-apps/plugin-dialog";

export function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(1000));
  const value = bytes / 1000 ** i;
  return `${value % 1 === 0 ? value : value.toFixed(1)} ${units[i]}`;
}

export function formatState(state: TransferState): string {
  switch (state) {
    case "initializing":
      return "Initializing";
    case "waiting_permission":
      return "Waiting for permission";
    case "in_progress":
      return "In progress";
    case "paused":
      return "Paused";
    case "completed":
      return "Completed";
    case "stopped":
      return "Stopped";
    case "canceled":
      return "Canceled";
    case "denied":
      return "Denied";
    default:
      if (typeof state === "object" && "failed" in state) {
        return `Failed: ${state.failed.replace(/_/g, " ")}`;
      }
      return "Unknown state";
  }
}

export function TransferButtons({ transfer }: { transfer: Transfer }) {
  const incoming = "Incoming" in transfer.kind;

  const defaultLocation = useSettings().settings?.defaultDestination;

  const canAccept = transfer.state == "waiting_permission" && incoming;
  const canCancel = transfer.state == "waiting_permission";
  const canStop = transfer.state == "in_progress";
  const canOpen = transfer.state == "completed" && incoming;
  const canRemove =
    transfer.state == "completed" ||
    transfer.state == "denied" ||
    transfer.state == "stopped" ||
    transfer.state == "canceled" ||
    (typeof transfer.state === "object" && "failed" in transfer.state);

  return (
    <>
      <ButtonGroup>
        {canAccept ? (
          <Button
            size="icon-sm"
            variant="default"
            onClick={async () => {
              if (defaultLocation)
                await acceptTransfer(transfer, defaultLocation);
            }}
            disabled={!defaultLocation}
          >
            <HugeiconsIcon icon={Tick02Icon} />
          </Button>
        ) : undefined}
        {canCancel ? (
          <Button
            size="icon-sm"
            variant="destructive"
            onClick={() => cancelTransfer(transfer)}
          >
            <HugeiconsIcon icon={Cancel01Icon} />
          </Button>
        ) : undefined}
        {canStop ? (
          <Button
            size="icon-sm"
            variant="destructive"
            onClick={() => stopTransfer(transfer)}
          >
            <HugeiconsIcon icon={StopIcon} />
          </Button>
        ) : undefined}
        {canOpen ? (
          <Button
            size="icon-sm"
            variant="secondary"
            onClick={() => openTransfer(transfer)}
          >
            <HugeiconsIcon icon={FolderOpenIcon} />
          </Button>
        ) : undefined}
        {canRemove ? (
          <Button
            size="icon-sm"
            variant="secondary"
            onClick={() => removeTransfer(transfer)}
          >
            <HugeiconsIcon icon={Remove01Icon} />
          </Button>
        ) : undefined}
      </ButtonGroup>

      {canAccept ? (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button size="icon-sm" variant="secondary" className="-ml-2">
              <HugeiconsIcon icon={MoreVerticalIcon} />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuGroup>
              <DropdownMenuItem
                onSelect={() => {
                  openDialog({ directory: true, multiple: false }).then(
                    async (path) => {
                      if (path) await acceptTransfer(transfer, path);
                    },
                  );
                }}
              >
                <HugeiconsIcon icon={Folder01Icon} />
                Accept to…
              </DropdownMenuItem>
            </DropdownMenuGroup>
          </DropdownMenuContent>
        </DropdownMenu>
      ) : undefined}
    </>
  );
}

export function TransferCard({ transfer }: { transfer: Transfer }) {
  const throttledSpeed = useThrottle(transfer.bytes_per_second, 1000);
  const throttledTransferred = useThrottle(transfer.bytes_transferred);

  const percentage =
    transfer.total_bytes > 0
      ? Math.round((throttledTransferred / transfer.total_bytes) * 100)
      : 0;

  const secondsLeft =
    throttledSpeed > 0
      ? Math.round(
          (transfer.total_bytes - throttledTransferred) / throttledSpeed,
        )
      : null;

  function formatTimeLeft(seconds: number): string {
    if (seconds < 60) return `~${seconds}s left`;
    if (seconds < 3600) return `~${Math.round(seconds / 60)}m left`;
    return `~${(seconds / 3600).toFixed(1)}h left`;
  }

  return (
    <div className="bg-card border rounded-md px-3.5 py-3 flex flex-col">
      <div className="flex flex-row items-center gap-3">
        {"Outgoing" in transfer.kind ? (
          <HugeiconsIcon icon={Upload01Icon} className="size-4" />
        ) : (
          <HugeiconsIcon icon={Download01Icon} className="size-4" />
        )}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 min-w-0">
            <span className="text-sm truncate min-w-0">
              {transfer.entry_names.join(", ")}
            </span>
            {transfer.file_count > 1 ? (
              <Badge variant="secondary" className="shrink-0">
                {transfer.file_count} files
              </Badge>
            ) : null}
            <Badge variant="outline" className="shrink-0">
              {formatBytes(transfer.total_bytes)}
            </Badge>
            {transfer.use_compression ? (
              <Badge variant="secondary" className="shrink-0">
                Compressed
              </Badge>
            ) : null}
          </div>
          <div className="text-xs truncate text-muted-foreground">
            {formatState(transfer.state)}
          </div>
        </div>
        <TransferButtons transfer={transfer} />
      </div>
      <Collapsible open={transfer.state == "in_progress"}>
        <CollapsibleContent className="transfer-details-collapsible">
          <Field className="w-full pt-3">
            <Progress value={percentage} id={`progress-${transfer.uuid}`} />
            <FieldLabel htmlFor={`progress-${transfer.uuid}`}>
              <span className="text-muted-foreground text-xs">
                {percentage}% • {formatBytes(throttledSpeed)}/s
              </span>
              <span className="ml-auto text-muted-foreground text-xs">
                {secondsLeft !== null ? formatTimeLeft(secondsLeft) : "—"}
              </span>
            </FieldLabel>
          </Field>
        </CollapsibleContent>
      </Collapsible>
    </div>
  );
}
