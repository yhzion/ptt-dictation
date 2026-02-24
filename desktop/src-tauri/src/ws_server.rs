use std::sync::Arc;

use futures_util::{SinkExt, StreamExt};
use tokio::net::TcpListener;
use tokio::sync::Mutex;

use crate::client_registry::ClientRegistry;
use crate::injection::TextInjector;
use crate::protocol;

/// Event emitted to the frontend
#[derive(Debug, Clone, serde::Serialize)]
#[serde(tag = "kind")]
pub enum ServerEvent {
    ClientConnected {
        client_id: String,
        device_model: String,
    },
    ClientDisconnected {
        client_id: String,
    },
    PartialText {
        client_id: String,
        session_id: String,
        text: String,
        seq: u64,
        confidence: f64,
    },
    FinalText {
        client_id: String,
        session_id: String,
        text: String,
        confidence: f64,
    },
    PttStarted {
        client_id: String,
        session_id: String,
    },
}

/// Callback trait for server events (enables testing without Tauri)
pub trait EventEmitter: Send + Sync {
    fn emit(&self, event: ServerEvent);
}

/// Handle a single parsed protocol message. Returns an optional response to send back.
async fn handle_message(
    message: protocol::Message,
    registry: &Arc<Mutex<ClientRegistry>>,
    injector: &Arc<dyn TextInjector>,
    emitter: &Arc<dyn EventEmitter>,
    client_id_slot: &mut Option<String>,
) -> Option<tokio_tungstenite::tungstenite::Message> {
    match message {
        protocol::Message::Hello { client_id, payload } => {
            {
                let mut reg = registry.lock().await;
                reg.register(&client_id, &payload.device_model, &payload.engine);
            }
            *client_id_slot = Some(client_id.clone());
            emitter.emit(ServerEvent::ClientConnected {
                client_id: client_id.clone(),
                device_model: payload.device_model,
            });
            let ack = protocol::Message::Ack {
                client_id,
                payload: protocol::AckPayload {
                    ack_type: "HELLO".to_string(),
                },
            };
            let json = protocol::serialize_message(&ack).ok()?;
            Some(tokio_tungstenite::tungstenite::Message::Text(json))
        }
        protocol::Message::PttStart { client_id, payload } => {
            {
                let mut reg = registry.lock().await;
                reg.set_session(&client_id, Some(payload.session_id.clone()));
                reg.set_partial_text(&client_id, None);
            }
            emitter.emit(ServerEvent::PttStarted {
                client_id,
                session_id: payload.session_id,
            });
            None
        }
        protocol::Message::Partial {
            client_id, payload, ..
        } => {
            {
                let mut reg = registry.lock().await;
                reg.set_partial_text(&client_id, Some(payload.text.clone()));
            }
            emitter.emit(ServerEvent::PartialText {
                client_id,
                session_id: payload.session_id,
                text: payload.text,
                seq: payload.seq,
                confidence: payload.confidence,
            });
            None
        }
        protocol::Message::Final {
            client_id, payload, ..
        } => {
            {
                let mut reg = registry.lock().await;
                reg.set_session(&client_id, None);
                reg.set_partial_text(&client_id, None);
            }
            let _ = injector.inject(&payload.text);
            emitter.emit(ServerEvent::FinalText {
                client_id: client_id.clone(),
                session_id: payload.session_id,
                text: payload.text,
                confidence: payload.confidence,
            });
            let ack = protocol::Message::Ack {
                client_id,
                payload: protocol::AckPayload {
                    ack_type: "FINAL".to_string(),
                },
            };
            let json = protocol::serialize_message(&ack).ok()?;
            Some(tokio_tungstenite::tungstenite::Message::Text(json))
        }
        protocol::Message::Heartbeat { client_id } => {
            let mut reg = registry.lock().await;
            reg.heartbeat(&client_id);
            None
        }
        protocol::Message::Ack { .. } => None,
    }
}

pub async fn start_server(
    port: u16,
    registry: Arc<Mutex<ClientRegistry>>,
    injector: Arc<dyn TextInjector>,
    emitter: Arc<dyn EventEmitter>,
) -> Result<(), String> {
    let addr = format!("0.0.0.0:{}", port);
    let listener = TcpListener::bind(&addr)
        .await
        .map_err(|e| format!("Failed to bind to {}: {}", addr, e))?;

    log::info!("WebSocket server listening on {}", addr);

    loop {
        let (stream, _peer) = listener
            .accept()
            .await
            .map_err(|e| format!("Accept failed: {}", e))?;

        let registry = Arc::clone(&registry);
        let injector = Arc::clone(&injector);
        let emitter = Arc::clone(&emitter);

        tokio::spawn(async move {
            let ws_stream = match tokio_tungstenite::accept_async(stream).await {
                Ok(ws) => ws,
                Err(e) => {
                    log::error!("WebSocket handshake failed: {}", e);
                    return;
                }
            };

            let (mut sink, mut stream) = ws_stream.split();
            let mut client_id_slot: Option<String> = None;

            while let Some(msg_result) = stream.next().await {
                let msg = match msg_result {
                    Ok(m) => m,
                    Err(e) => {
                        log::error!("WebSocket read error: {}", e);
                        break;
                    }
                };

                let text = match &msg {
                    tokio_tungstenite::tungstenite::Message::Text(t) => t.to_string(),
                    tokio_tungstenite::tungstenite::Message::Close(_) => break,
                    _ => continue,
                };

                let parsed = match protocol::parse_message(&text) {
                    Ok(p) => p,
                    Err(e) => {
                        log::warn!("Invalid message: {}", e);
                        continue;
                    }
                };

                if let Some(response) =
                    handle_message(parsed, &registry, &injector, &emitter, &mut client_id_slot)
                        .await
                {
                    if let Err(e) = sink.send(response).await {
                        log::error!("Failed to send response: {}", e);
                        break;
                    }
                }
            }

            // Client disconnected — clean up
            if let Some(cid) = &client_id_slot {
                let mut reg = registry.lock().await;
                reg.unregister(cid);
                emitter.emit(ServerEvent::ClientDisconnected {
                    client_id: cid.clone(),
                });
            }
        });
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    struct TestEmitter {
        events: Arc<std::sync::Mutex<Vec<ServerEvent>>>,
    }

    impl TestEmitter {
        fn new() -> (Self, Arc<std::sync::Mutex<Vec<ServerEvent>>>) {
            let events = Arc::new(std::sync::Mutex::new(Vec::new()));
            (
                Self {
                    events: Arc::clone(&events),
                },
                events,
            )
        }
    }

    impl EventEmitter for TestEmitter {
        fn emit(&self, event: ServerEvent) {
            self.events.lock().unwrap().push(event);
        }
    }

    struct MockInjector {
        injected: Arc<std::sync::Mutex<Vec<String>>>,
    }

    impl MockInjector {
        fn new() -> (Self, Arc<std::sync::Mutex<Vec<String>>>) {
            let injected = Arc::new(std::sync::Mutex::new(Vec::new()));
            (
                Self {
                    injected: Arc::clone(&injected),
                },
                injected,
            )
        }
    }

    impl TextInjector for MockInjector {
        fn inject(&self, text: &str) -> Result<(), String> {
            self.injected.lock().unwrap().push(text.to_string());
            Ok(())
        }
    }

    /// Helper: find a free port by binding to :0, recording the port, then dropping.
    fn free_port() -> u16 {
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        listener.local_addr().unwrap().port()
    }

    #[tokio::test]
    async fn test_hello_ack_roundtrip() {
        let port = free_port();

        let registry = Arc::new(Mutex::new(ClientRegistry::new(30)));
        let (emitter, events) = TestEmitter::new();
        let (injector, _injected) = MockInjector::new();

        let reg_clone = Arc::clone(&registry);

        // Start server in background
        tokio::spawn(start_server(
            port,
            reg_clone,
            Arc::new(injector),
            Arc::new(emitter),
        ));

        // Give server time to bind
        tokio::time::sleep(std::time::Duration::from_millis(100)).await;

        // Connect client
        let url = format!("ws://127.0.0.1:{}", port);
        let (mut ws, _) = tokio_tungstenite::connect_async(&url).await.unwrap();

        // Send HELLO
        let hello_json = r#"{"type":"HELLO","clientId":"phone-01","payload":{"deviceModel":"Galaxy S23","engine":"Google","capabilities":["WS"]}}"#;
        ws.send(tokio_tungstenite::tungstenite::Message::Text(
            hello_json.into(),
        ))
        .await
        .unwrap();

        // Receive ACK
        let ack_msg = ws.next().await.unwrap().unwrap();
        let ack_text = ack_msg.into_text().unwrap();
        let ack: protocol::Message = serde_json::from_str(&ack_text).unwrap();
        match ack {
            protocol::Message::Ack { payload, .. } => {
                assert_eq!(payload.ack_type, "HELLO");
            }
            _ => panic!("expected ACK message"),
        }

        // Verify registry has 1 client
        {
            let reg = registry.lock().await;
            assert_eq!(reg.connected_count(), 1);
            assert!(reg.get("phone-01").is_some());
        }

        // Verify event was emitted
        {
            let evts = events.lock().unwrap();
            assert_eq!(evts.len(), 1);
            match &evts[0] {
                ServerEvent::ClientConnected {
                    client_id,
                    device_model,
                } => {
                    assert_eq!(client_id, "phone-01");
                    assert_eq!(device_model, "Galaxy S23");
                }
                other => panic!("expected ClientConnected, got {:?}", other),
            }
        }

        ws.close(None).await.unwrap();
    }

    #[tokio::test]
    async fn test_final_triggers_injection() {
        let port = free_port();

        let registry = Arc::new(Mutex::new(ClientRegistry::new(30)));
        let (emitter, _events) = TestEmitter::new();
        let (injector, injected) = MockInjector::new();

        let reg_clone = Arc::clone(&registry);

        tokio::spawn(start_server(
            port,
            reg_clone,
            Arc::new(injector),
            Arc::new(emitter),
        ));

        tokio::time::sleep(std::time::Duration::from_millis(100)).await;

        let url = format!("ws://127.0.0.1:{}", port);
        let (mut ws, _) = tokio_tungstenite::connect_async(&url).await.unwrap();

        // Send HELLO and consume ACK
        let hello_json = r#"{"type":"HELLO","clientId":"phone-01","payload":{"deviceModel":"Galaxy S23","engine":"Google","capabilities":["WS"]}}"#;
        ws.send(tokio_tungstenite::tungstenite::Message::Text(
            hello_json.into(),
        ))
        .await
        .unwrap();
        let _ = ws.next().await.unwrap().unwrap(); // consume HELLO ACK

        // Send FINAL
        let final_json = r#"{"type":"FINAL","clientId":"phone-01","timestamp":1670000000000,"payload":{"sessionId":"s-abc123","text":"Hello world","confidence":0.95}}"#;
        ws.send(tokio_tungstenite::tungstenite::Message::Text(
            final_json.into(),
        ))
        .await
        .unwrap();

        // Receive FINAL ACK
        let ack_msg = ws.next().await.unwrap().unwrap();
        let ack_text = ack_msg.into_text().unwrap();
        let ack: protocol::Message = serde_json::from_str(&ack_text).unwrap();
        match ack {
            protocol::Message::Ack { payload, .. } => {
                assert_eq!(payload.ack_type, "FINAL");
            }
            _ => panic!("expected ACK message"),
        }

        // Verify injection happened
        {
            let texts = injected.lock().unwrap();
            assert_eq!(texts.len(), 1);
            assert_eq!(texts[0], "Hello world");
        }

        ws.close(None).await.unwrap();
    }
}
