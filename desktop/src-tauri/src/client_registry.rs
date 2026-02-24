use std::collections::HashMap;
use std::time::{Duration, Instant};

#[derive(Debug, Clone)]
pub struct ClientInfo {
    pub client_id: String,
    pub device_model: String,
    pub engine: String,
    pub connected_at: Instant,
    pub last_heartbeat: Instant,
    pub current_session: Option<String>,
    pub last_partial_text: Option<String>,
}

pub struct ClientRegistry {
    clients: HashMap<String, ClientInfo>,
    heartbeat_timeout: Duration,
}

impl ClientRegistry {
    pub fn new(heartbeat_timeout_secs: u64) -> Self {
        Self {
            clients: HashMap::new(),
            heartbeat_timeout: Duration::from_secs(heartbeat_timeout_secs),
        }
    }

    pub fn register(&mut self, client_id: &str, device_model: &str, engine: &str) {
        let now = Instant::now();
        self.clients.insert(
            client_id.to_string(),
            ClientInfo {
                client_id: client_id.to_string(),
                device_model: device_model.to_string(),
                engine: engine.to_string(),
                connected_at: now,
                last_heartbeat: now,
                current_session: None,
                last_partial_text: None,
            },
        );
    }

    pub fn unregister(&mut self, client_id: &str) -> Option<ClientInfo> {
        self.clients.remove(client_id)
    }

    pub fn heartbeat(&mut self, client_id: &str) -> bool {
        match self.clients.get_mut(client_id) {
            Some(info) => {
                info.last_heartbeat = Instant::now();
                true
            }
            None => false,
        }
    }

    pub fn get(&self, client_id: &str) -> Option<&ClientInfo> {
        self.clients.get(client_id)
    }

    pub fn get_mut(&mut self, client_id: &str) -> Option<&mut ClientInfo> {
        self.clients.get_mut(client_id)
    }

    pub fn set_session(&mut self, client_id: &str, session_id: Option<String>) -> bool {
        match self.clients.get_mut(client_id) {
            Some(info) => {
                info.current_session = session_id;
                true
            }
            None => false,
        }
    }

    pub fn set_partial_text(&mut self, client_id: &str, text: Option<String>) -> bool {
        match self.clients.get_mut(client_id) {
            Some(info) => {
                info.last_partial_text = text;
                true
            }
            None => false,
        }
    }

    pub fn timed_out_clients(&self) -> Vec<String> {
        let now = Instant::now();
        self.clients
            .iter()
            .filter(|(_, info)| now.duration_since(info.last_heartbeat) > self.heartbeat_timeout)
            .map(|(id, _)| id.clone())
            .collect()
    }

    pub fn connected_count(&self) -> usize {
        self.clients.len()
    }

    pub fn all_client_ids(&self) -> Vec<String> {
        self.clients.keys().cloned().collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::thread;

    #[test]
    fn test_client_lifecycle() {
        let mut reg = ClientRegistry::new(30);

        // register
        reg.register("phone-01", "Galaxy S23", "Google");
        assert_eq!(reg.connected_count(), 1);

        // set_session
        assert!(reg.set_session("phone-01", Some("s-abc".to_string())));
        assert_eq!(
            reg.get("phone-01").unwrap().current_session.as_deref(),
            Some("s-abc")
        );

        // set_partial_text
        assert!(reg.set_partial_text("phone-01", Some("hello".to_string())));
        assert_eq!(
            reg.get("phone-01").unwrap().last_partial_text.as_deref(),
            Some("hello")
        );

        // heartbeat
        assert!(reg.heartbeat("phone-01"));

        // unregister
        let removed = reg.unregister("phone-01");
        assert!(removed.is_some());
        assert_eq!(removed.unwrap().client_id, "phone-01");
        assert_eq!(reg.connected_count(), 0);
    }

    #[test]
    fn test_register_new_client() {
        let mut reg = ClientRegistry::new(30);
        reg.register("phone-01", "Galaxy S23", "Google");

        let info = reg.get("phone-01").unwrap();
        assert_eq!(info.client_id, "phone-01");
        assert_eq!(info.device_model, "Galaxy S23");
        assert_eq!(info.engine, "Google");
        assert!(info.current_session.is_none());
        assert!(info.last_partial_text.is_none());
    }

    #[test]
    fn test_heartbeat_unknown_client_returns_false() {
        let mut reg = ClientRegistry::new(30);
        assert!(!reg.heartbeat("nonexistent"));
    }

    #[test]
    fn test_set_session_unknown_client_returns_false() {
        let mut reg = ClientRegistry::new(30);
        assert!(!reg.set_session("nonexistent", Some("s-abc".to_string())));
    }

    #[test]
    fn test_timed_out_clients() {
        let mut reg = ClientRegistry::new(0);
        reg.register("phone-01", "Galaxy S23", "Google");

        thread::sleep(Duration::from_millis(10));

        let timed_out = reg.timed_out_clients();
        assert_eq!(timed_out.len(), 1);
        assert_eq!(timed_out[0], "phone-01");
    }

    #[test]
    fn test_heartbeat_resets_timeout() {
        // Use a 1-second timeout so we can observe the reset
        let mut reg = ClientRegistry::new(1);
        reg.register("phone-01", "Galaxy S23", "Google");

        // Not timed out yet (within 1s)
        assert!(reg.timed_out_clients().is_empty());

        // Heartbeat resets the timer
        thread::sleep(Duration::from_millis(10));
        assert!(reg.heartbeat("phone-01"));

        // Still not timed out because heartbeat just reset the timer
        let timed_out = reg.timed_out_clients();
        assert!(timed_out.is_empty());
    }

    #[test]
    fn test_multiple_clients() {
        let mut reg = ClientRegistry::new(30);
        reg.register("phone-01", "Galaxy S23", "Google");
        reg.register("phone-02", "iPhone 15", "Apple");
        assert_eq!(reg.connected_count(), 2);

        reg.unregister("phone-01");
        assert_eq!(reg.connected_count(), 1);

        assert!(reg.get("phone-01").is_none());
        assert!(reg.get("phone-02").is_some());
    }
}
