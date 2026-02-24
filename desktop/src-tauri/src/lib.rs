pub mod client_registry;
pub mod injection;
pub mod protocol;
pub mod ws_server;

use std::sync::Arc;

use client_registry::ClientRegistry;
use injection::ClipboardPasteInjector;

/// Tauri event emitter — bridges ws_server events to frontend
struct TauriEventEmitter {
    app_handle: tauri::AppHandle,
}

impl ws_server::EventEmitter for TauriEventEmitter {
    fn emit(&self, event: ws_server::ServerEvent) {
        use tauri::Emitter;

        let event_name = match &event {
            ws_server::ServerEvent::ClientConnected { .. } => "client-connected",
            ws_server::ServerEvent::ClientDisconnected { .. } => "client-disconnected",
            ws_server::ServerEvent::PartialText { .. } => "partial-text",
            ws_server::ServerEvent::FinalText { .. } => "final-text",
            ws_server::ServerEvent::PttStarted { .. } => "ptt-started",
        };
        let _ = self.app_handle.emit(event_name, &event);
    }
}

#[tauri::command]
async fn get_server_port() -> u16 {
    9876
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .invoke_handler(tauri::generate_handler![get_server_port])
        .setup(|app| {
            let handle = app.handle().clone();
            let registry = Arc::new(tokio::sync::Mutex::new(ClientRegistry::new(15)));
            let injector: Arc<dyn injection::TextInjector> = Arc::new(ClipboardPasteInjector);
            let emitter: Arc<dyn ws_server::EventEmitter> =
                Arc::new(TauriEventEmitter { app_handle: handle });

            tauri::async_runtime::spawn(async move {
                if let Err(e) = ws_server::start_server(9876, registry, injector, emitter).await {
                    log::error!("WebSocket server error: {e}");
                }
            });

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
