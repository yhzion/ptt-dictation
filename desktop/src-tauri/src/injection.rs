/// Trait for text injection — enables testing without OS interaction
pub trait TextInjector: Send + Sync {
    fn inject(&self, text: &str) -> Result<(), String>;
}

/// macOS: clipboard backup -> set text -> Cmd+V -> restore clipboard
pub struct ClipboardPasteInjector;

impl TextInjector for ClipboardPasteInjector {
    fn inject(&self, text: &str) -> Result<(), String> {
        use arboard::Clipboard;
        use enigo::{Direction, Enigo, Key, Keyboard, Settings};

        let mut clipboard = Clipboard::new().map_err(|e| format!("Clipboard init: {e}"))?;
        let backup = clipboard.get_text().ok();

        clipboard
            .set_text(text)
            .map_err(|e| format!("Clipboard set: {e}"))?;

        let mut enigo = Enigo::new(&Settings::default()).map_err(|e| format!("Enigo init: {e}"))?;
        enigo
            .key(Key::Meta, Direction::Press)
            .map_err(|e| format!("Key press: {e}"))?;
        enigo
            .key(Key::Unicode('v'), Direction::Click)
            .map_err(|e| format!("Key click: {e}"))?;
        enigo
            .key(Key::Meta, Direction::Release)
            .map_err(|e| format!("Key release: {e}"))?;

        std::thread::sleep(std::time::Duration::from_millis(100));

        if let Some(old) = backup {
            let _ = clipboard.set_text(&old);
        }

        Ok(())
    }
}

#[cfg(test)]
pub mod testing {
    use super::*;
    use std::sync::{Arc, Mutex};

    pub struct MockInjector {
        pub injected: Arc<Mutex<Vec<String>>>,
        pub should_fail: bool,
    }

    impl MockInjector {
        pub fn new() -> (Self, Arc<Mutex<Vec<String>>>) {
            let injected = Arc::new(Mutex::new(Vec::new()));
            (
                Self {
                    injected: Arc::clone(&injected),
                    should_fail: false,
                },
                injected,
            )
        }

        pub fn failing() -> Self {
            Self {
                injected: Arc::new(Mutex::new(Vec::new())),
                should_fail: true,
            }
        }
    }

    impl TextInjector for MockInjector {
        fn inject(&self, text: &str) -> Result<(), String> {
            if self.should_fail {
                return Err("Mock injection failure".to_string());
            }
            self.injected.lock().unwrap().push(text.to_string());
            Ok(())
        }
    }
}

#[cfg(test)]
mod tests {
    use super::testing::MockInjector;
    use super::*;

    #[test]
    fn test_mock_injector_records_text() {
        let (mock, injected) = MockInjector::new();
        mock.inject("hello world").unwrap();
        mock.inject("second text").unwrap();

        let recorded = injected.lock().unwrap();
        assert_eq!(recorded.len(), 2);
        assert_eq!(recorded[0], "hello world");
        assert_eq!(recorded[1], "second text");
    }

    #[test]
    fn test_mock_injector_failure() {
        let mock = MockInjector::failing();
        let result = mock.inject("should fail");
        assert!(result.is_err());
        assert_eq!(result.unwrap_err(), "Mock injection failure");
    }

    #[test]
    fn test_trait_object_dispatch() {
        let (mock, injected) = MockInjector::new();
        let injector: Box<dyn TextInjector> = Box::new(mock);
        injector.inject("via trait object").unwrap();

        let recorded = injected.lock().unwrap();
        assert_eq!(recorded.len(), 1);
        assert_eq!(recorded[0], "via trait object");
    }
}
