export type TransferError =
  | "connection_lost"
  | "storage_full"
  | "failed_to_process_files"
  | "failed_to_start_transfer"
  | "unsafe_path"
  | "files_not_found"
  | "permission_denied"
  | "file_too_large"
  | "invalid_filename"
  | "out_of_memory"
  | "io_error"
  | "remote_error";

export type TransferState =
  | "initializing"
  | "waiting_permission"
  | "in_progress"
  | "paused"
  | "completed"
  | "stopped"
  | "canceled"
  | "denied"
  | { failed: TransferError };

export type TransferKind =
  | { Outgoing: { source_paths: string[] } }
  | { Incoming: { destination: string } };

export interface Transfer {
  uuid: string;
  remote_uuid: string;
  state: TransferState;
  timestamp: Date;
  total_bytes: number;
  bytes_transferred: number;
  bytes_per_second: number;
  file_count: number;
  entry_names: string[];
  single_name: string | null;
  single_mime_type: string | null;
  kind: TransferKind;
  use_compression: boolean;
}
