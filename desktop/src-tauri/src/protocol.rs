use serde::{Deserialize, Serialize};

// --- Payload structs ---

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct HelloPayload {
    #[serde(rename = "deviceModel")]
    pub device_model: String,
    pub engine: String,
    pub capabilities: Vec<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct PttStartPayload {
    #[serde(rename = "sessionId")]
    pub session_id: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct PartialPayload {
    #[serde(rename = "sessionId")]
    pub session_id: String,
    pub seq: u64,
    pub text: String,
    pub confidence: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct FinalPayload {
    #[serde(rename = "sessionId")]
    pub session_id: String,
    pub text: String,
    pub confidence: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct AckPayload {
    #[serde(rename = "ackType")]
    pub ack_type: String,
}

// --- Message enum ---

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(tag = "type")]
pub enum Message {
    #[serde(rename = "HELLO")]
    Hello {
        #[serde(rename = "clientId")]
        client_id: String,
        payload: HelloPayload,
    },
    #[serde(rename = "PTT_START")]
    PttStart {
        #[serde(rename = "clientId")]
        client_id: String,
        payload: PttStartPayload,
    },
    #[serde(rename = "PARTIAL")]
    Partial {
        #[serde(rename = "clientId")]
        client_id: String,
        timestamp: u64,
        payload: PartialPayload,
    },
    #[serde(rename = "FINAL")]
    Final {
        #[serde(rename = "clientId")]
        client_id: String,
        timestamp: u64,
        payload: FinalPayload,
    },
    #[serde(rename = "HEARTBEAT")]
    Heartbeat {
        #[serde(rename = "clientId")]
        client_id: String,
    },
    #[serde(rename = "ACK")]
    Ack {
        #[serde(rename = "clientId")]
        client_id: String,
        payload: AckPayload,
    },
}

// --- Helper functions ---

pub fn parse_message(json: &str) -> Result<Message, serde_json::Error> {
    serde_json::from_str(json)
}

pub fn serialize_message(msg: &Message) -> Result<String, serde_json::Error> {
    serde_json::to_string(msg)
}

// --- Tests ---

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_hello() {
        let json = r#"{"type":"HELLO","clientId":"phone-01","payload":{"deviceModel":"Galaxy S23","engine":"Google","capabilities":["WS"]}}"#;
        let msg = parse_message(json).unwrap();
        match msg {
            Message::Hello { client_id, payload } => {
                assert_eq!(client_id, "phone-01");
                assert_eq!(payload.device_model, "Galaxy S23");
                assert_eq!(payload.engine, "Google");
                assert_eq!(payload.capabilities, vec!["WS"]);
            }
            _ => panic!("expected Hello variant"),
        }
    }

    #[test]
    fn test_parse_ptt_start() {
        let json =
            r#"{"type":"PTT_START","clientId":"phone-01","payload":{"sessionId":"s-abc123"}}"#;
        let msg = parse_message(json).unwrap();
        match msg {
            Message::PttStart { client_id, payload } => {
                assert_eq!(client_id, "phone-01");
                assert_eq!(payload.session_id, "s-abc123");
            }
            _ => panic!("expected PttStart variant"),
        }
    }

    #[test]
    fn test_parse_partial() {
        let json = r#"{"type":"PARTIAL","clientId":"phone-01","timestamp":1670000000000,"payload":{"sessionId":"s-abc123","seq":12,"text":"안녕하세요 오늘","confidence":0.60}}"#;
        let msg = parse_message(json).unwrap();
        match msg {
            Message::Partial {
                client_id,
                timestamp,
                payload,
            } => {
                assert_eq!(client_id, "phone-01");
                assert_eq!(timestamp, 1670000000000);
                assert_eq!(payload.session_id, "s-abc123");
                assert_eq!(payload.seq, 12);
                assert_eq!(payload.text, "안녕하세요 오늘");
                assert!((payload.confidence - 0.60).abs() < f64::EPSILON);
            }
            _ => panic!("expected Partial variant"),
        }
    }

    #[test]
    fn test_parse_final() {
        let json = r#"{"type":"FINAL","clientId":"phone-01","timestamp":1670000000000,"payload":{"sessionId":"s-abc123","text":"안녕하세요. 오늘 회의는 오후 3시입니다.","confidence":0.93}}"#;
        let msg = parse_message(json).unwrap();
        match msg {
            Message::Final {
                client_id,
                timestamp,
                payload,
            } => {
                assert_eq!(client_id, "phone-01");
                assert_eq!(timestamp, 1670000000000);
                assert_eq!(payload.session_id, "s-abc123");
                assert_eq!(payload.text, "안녕하세요. 오늘 회의는 오후 3시입니다.");
                assert!((payload.confidence - 0.93).abs() < f64::EPSILON);
            }
            _ => panic!("expected Final variant"),
        }
    }

    #[test]
    fn test_parse_heartbeat() {
        let json = r#"{"type":"HEARTBEAT","clientId":"phone-01"}"#;
        let msg = parse_message(json).unwrap();
        match msg {
            Message::Heartbeat { client_id } => {
                assert_eq!(client_id, "phone-01");
            }
            _ => panic!("expected Heartbeat variant"),
        }
    }

    #[test]
    fn test_serialize_ack_roundtrip() {
        let msg = Message::Ack {
            client_id: "phone-01".to_string(),
            payload: AckPayload {
                ack_type: "HELLO".to_string(),
            },
        };
        let json = serialize_message(&msg).unwrap();
        let parsed = parse_message(&json).unwrap();
        assert_eq!(msg, parsed);
    }

    #[test]
    fn test_parse_invalid_type() {
        let json = r#"{"type":"UNKNOWN","clientId":"phone-01"}"#;
        assert!(parse_message(json).is_err());
    }

    #[test]
    fn test_parse_invalid_json() {
        let json = r#"{not valid json"#;
        assert!(parse_message(json).is_err());
    }
}
