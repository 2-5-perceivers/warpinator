use bytesize::ByteSize;
use ratatui::Frame;
use ratatui::layout::{Constraint, Direction, Layout, Rect};
use ratatui::style::{Color, Modifier, Style};
use ratatui::text::{Line, Span};
use ratatui::widgets::{Block, Borders, List, ListItem, ListState, Paragraph};
use warpinator_lib::types::message;
use warpinator_lib::types::remote::{RemoteConnectionError, RemoteState};
use warpinator_lib::types::transfer::{TransferKind, TransferState};

use crate::tui::app::{App, Focus, InputMode};

fn fmt_seconds(secs: u64) -> String {
    // format as "1h 2m 3s" or "2m 5s" or "5s"
    let hours = secs / 3600;
    let minutes = (secs % 3600) / 60;
    let seconds = secs % 60;
    let mut parts: Vec<String> = Vec::new();
    if hours > 0 {
        parts.push(format!("{}h", hours));
    }
    if minutes > 0 {
        parts.push(format!("{}m", minutes));
    }
    if seconds > 0 || parts.is_empty() {
        parts.push(format!("{}s", seconds));
    }
    parts.join(" ")
}

pub fn draw(f: &mut Frame, app: &App) {
    let root = Layout::default()
        .direction(Direction::Vertical)
        .constraints([
            Constraint::Min(5),    // top: remotes + transfers/messages
            Constraint::Length(6), // bottom: log
            Constraint::Length(3), // bottom: status/input bar
        ])
        .split(f.area());

    // Split top horizontally: remotes | transfers/messages
    let top = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([
            Constraint::Length(32), // Remotes pane fixed width
            Constraint::Min(10),    // Transfers/messages take the rest
        ])
        .split(root[0]);

    // Split right pane horizontally: transfers | messages (messages smaller)
    let right = Layout::default()
        .direction(Direction::Horizontal)
        .constraints([
            Constraint::Min(30),    // Transfers take most space
            Constraint::Length(32), // Messages pane fixed width
        ])
        .split(top[1]);

    draw_remotes(f, app, top[0]);
    draw_transfers(f, app, right[0]);
    draw_messages(f, app, right[1]);
    draw_log(f, app, root[1]);
    draw_statusbar(f, app, root[2]);
}

fn remote_state_span(state: &'_ RemoteState) -> Span<'_> {
    match state {
        RemoteState::Disconnected => {
            Span::styled("[disconnected]", Style::default().fg(Color::DarkGray))
        }
        RemoteState::Connecting => Span::styled("[connecting]", Style::default().fg(Color::Yellow)),
        RemoteState::AwaitingDuplex => {
            Span::styled("[awaiting]", Style::default().fg(Color::Yellow))
        }
        RemoteState::Connected => Span::styled("[connected]", Style::default().fg(Color::Green)),
        RemoteState::Error(err) => {
            let msg = match err {
                RemoteConnectionError::SslError => "[ssl error]",
                RemoteConnectionError::GroupCodeMismatch => "[group mismatch]",
                RemoteConnectionError::NoCertificate => "[no cert]",
                RemoteConnectionError::DuplexError => "[duplex err]",
            };
            Span::styled(msg, Style::default().fg(Color::Red))
        }
    }
}

fn transfer_state_span(state: &'_ TransferState) -> Span<'_> {
    match state {
        TransferState::Initializing => Span::styled("[init]", Style::default().fg(Color::DarkGray)),
        TransferState::WaitingPermission => {
            Span::styled("[waiting]", Style::default().fg(Color::Yellow))
        }
        TransferState::InProgress => {
            Span::styled("[in progress]", Style::default().fg(Color::Green))
        }
        TransferState::Paused => Span::styled("[paused]", Style::default().fg(Color::Yellow)),
        TransferState::Completed => Span::styled("[done]", Style::default().fg(Color::Green)),
        TransferState::Canceled => Span::styled("[canceled]", Style::default().fg(Color::DarkGray)),
        TransferState::Denied => Span::styled("[denied]", Style::default().fg(Color::Red)),
        TransferState::Failed(_) => Span::styled("[failed]", Style::default().fg(Color::Red)),
        TransferState::Stopped => Span::styled("[stopped]", Style::default().fg(Color::DarkGray)),
    }
}

fn draw_remotes(f: &mut Frame, app: &App, area: Rect) {
    let focused = app.focus == Focus::Remotes;
    let block = Block::default()
        .title(" Remotes ")
        .borders(Borders::ALL)
        .border_style(focus_style(focused));

    let items: Vec<ListItem> = app
        .remotes
        .iter()
        .map(|remote| {
            let state_span = remote_state_span(&remote.state);
            ListItem::new(Line::from(vec![
                state_span,
                Span::raw(" "),
                Span::styled(remote.display_name.as_str(), Style::default()),
            ]))
        })
        .collect();

    let mut state = ListState::default();
    if !app.remotes.is_empty() {
        state.select(Some(app.selected_remote));
    }

    let list =
        List::new(items).block(block).highlight_style(highlight_style()).highlight_symbol("> ");

    f.render_stateful_widget(list, area, &mut state);
}

fn draw_transfers(f: &mut Frame, app: &App, area: Rect) {
    let focused = app.focus == Focus::Transfers;
    let title = match app.current_remote() {
        Some(remote) => format!(" Transfers — {} ", remote.display_name),
        None => " Transfers ".to_string(),
    };

    let block =
        Block::default().title(title).borders(Borders::ALL).border_style(focus_style(focused));

    let transfers = app.current_transfers();

    // Render list into the whole area; progress is inline per item
    let items: Vec<ListItem> = transfers
        .iter()
        .map(|transfer| {
            // Basic description (name or joined entries)
            let base = if let Some(name) = &transfer.single_name {
                name.clone()
            } else {
                let mut names = transfer.entry_names.iter().take(3).cloned().collect::<Vec<_>>();
                if transfer.entry_names.len() > 3 {
                    names.push("...".to_string());
                }
                format!("{} {} files", names.join(", "), transfer.file_count)
            };

            // Use throttled display values when available to reduce update rate
            let (disp_bytes_per_sec, disp_bytes_transferred) = app
                .transfer_display
                .get(&transfer.uuid)
                .map(|d| (d.displayed_bytes_per_second, d.displayed_bytes_transferred))
                .unwrap_or((transfer.bytes_per_second, transfer.bytes_transferred));

            // Stats: keep parentheses for size; for in-progress show (speed /s,
            // X remaining)
            let stats = match transfer.state {
                TransferState::InProgress => {
                    let speed = format!("{}/s", ByteSize(disp_bytes_per_sec));

                    let remaining_str = if disp_bytes_per_sec > 0 {
                        let remaining = transfer.total_bytes.saturating_sub(disp_bytes_transferred);
                        let secs = remaining / disp_bytes_per_sec.max(1);
                        fmt_seconds(secs)
                    } else {
                        "--s".to_string()
                    };
                    format!("({}, {} remaining)", speed, remaining_str)
                }
                _ => format!("({})", ByteSize(transfer.total_bytes).to_string()),
            };

            // Inline progress bar (10 segments) and percentage — only for
            // InProgress
            let bar = if matches!(transfer.state, TransferState::InProgress)
                && transfer.total_bytes > 0
            {
                let ratio = (disp_bytes_transferred as f64) / (transfer.total_bytes as f64);
                let pct = (ratio * 100.0).round() as u64;
                let bar_len = 10usize;
                // Use floor to avoid back-and-forth jitter when ratio hovers on
                // a boundary
                let filled = ((ratio * (bar_len as f64)).floor() as usize).min(bar_len);
                let filled_str: String = std::iter::repeat('█').take(filled).collect();
                let empty_str: String = std::iter::repeat('░').take(bar_len - filled).collect();
                format!(" [{}{}] {}%", filled_str, empty_str, pct)
            } else {
                "".to_string()
            };

            let state_span = transfer_state_span(&transfer.state);

            // Build the ListItem with styled parts
            ListItem::new(Line::from(vec![
                Span::raw(" "),
                state_span,
                Span::raw(" "),
                Span::styled(base, Style::default()),
                Span::raw(" "),
                Span::raw(stats),
                Span::raw(" "),
                Span::raw(bar),
            ]))
        })
        .collect();

    let mut state = ListState::default();
    if !transfers.is_empty() {
        state.select(Some(app.selected_transfer));
    }

    let list =
        List::new(items).block(block).highlight_style(highlight_style()).highlight_symbol("> ");

    f.render_stateful_widget(list, area, &mut state);
}

fn draw_messages(f: &mut Frame, app: &App, area: Rect) {
    let focused = app.focus == Focus::Messages;
    let title = match app.current_remote() {
        Some(remote) => format!(" Messages — {} ", remote.display_name),
        None => " Messages ".to_string(),
    };
    let block =
        Block::default().title(title).borders(Borders::ALL).border_style(focus_style(focused));

    let messages = app.current_messages();
    let items: Vec<ListItem> = messages
        .iter()
        .map(|msg| {
            let dir = match msg.direction {
                message::Direction::Sent => "→",
                message::Direction::Received => "←",
            };
            let line = Line::from(vec![
                Span::styled(dir, Style::default().fg(Color::Yellow)),
                Span::raw(" "),
                Span::raw(&msg.content),
            ]);
            ListItem::new(line)
        })
        .collect();

    // Always show at least one empty item if no messages
    let items = if items.is_empty() { vec![ListItem::new("")] } else { items };

    let mut state = ListState::default();
    if !messages.is_empty() {
        state.select(Some(app.selected_message));
    }

    let list =
        List::new(items).block(block).highlight_style(highlight_style()).highlight_symbol("> ");

    f.render_stateful_widget(list, area, &mut state);
}

fn draw_log(f: &mut Frame, app: &App, area: Rect) {
    let block = Block::default().title(" Log ").borders(Borders::ALL);
    let inner_height = area.height.saturating_sub(2) as usize;

    let lines: Vec<Line> =
        app.log.iter().rev().take(inner_height).rev().map(|s| Line::from(s.as_str())).collect();

    let para = Paragraph::new(lines).block(block);
    f.render_widget(para, area);
}

fn draw_statusbar(f: &mut Frame, app: &App, area: Rect) {
    let content = match &app.input_mode {
        Some(InputMode::FilePath) => {
            format!(" Send file: {}_", app.input_buf)
        }
        Some(InputMode::AcceptDestination) => {
            format!(" Accept to: {}_  (Enter to confirm, Esc to cancel)", app.input_buf)
        }
        Some(InputMode::Message) => {
            format!(" Message: {}_", app.input_buf)
        }
        None => {
            let mut keys = vec!["j/k: nav", "Tab: pane", "q: quit", "s: send file", "m: message"];
            match app.focus {
                Focus::Remotes => {
                    if let Some(remote) = app.current_remote() {
                        if matches!(remote.state, RemoteState::Disconnected | RemoteState::Error(_))
                        {
                            keys.push("c: connect");
                        }
                    }
                }
                Focus::Transfers => {
                    if let Some(transfer) = app.current_transfer() {
                        match transfer.state {
                            TransferState::WaitingPermission
                                if matches!(transfer.kind, TransferKind::Incoming { .. }) =>
                            {
                                keys.push("a: accept");
                                keys.push("r: reject");
                            }
                            TransferState::WaitingPermission => {
                                keys.push("r: cancel");
                            }
                            TransferState::InProgress => {
                                keys.push("x: stop");
                            }
                            TransferState::Failed(_)
                            | TransferState::Completed
                            | TransferState::Stopped
                            | TransferState::Denied
                            | TransferState::Canceled => {
                                keys.push("del: delete");
                            }
                            _ => {}
                        }
                    }
                }
                Focus::Messages => {
                    if app.current_message().is_some() {
                        keys.push("del: delete");
                    }
                }
            }
            keys.join("  ")
        }
    };

    let style = match app.input_mode {
        Some(_) => Style::default().fg(Color::Yellow),
        None => Style::default().fg(Color::DarkGray),
    };

    let bar = Paragraph::new(content).style(style).block(Block::default().borders(Borders::ALL));

    f.render_widget(bar, area);
}

fn focus_style(focused: bool) -> Style {
    if focused { Style::default().fg(Color::Cyan) } else { Style::default().fg(Color::DarkGray) }
}

fn highlight_style() -> Style {
    Style::default().fg(Color::Black).bg(Color::Cyan).add_modifier(Modifier::BOLD)
}
