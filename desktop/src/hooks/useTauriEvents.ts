import { useEffect, useCallback, useState } from "react";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";
import {
  applyEvent,
  type ClientState,
  type ServerEvent,
} from "../types/messages";

export function useClientStates() {
  const [clients, setClients] = useState<Map<string, ClientState>>(new Map());

  const handleEvent = useCallback((event: ServerEvent) => {
    setClients((prev) => applyEvent(prev, event));
  }, []);

  useEffect(() => {
    const unlisteners: Promise<UnlistenFn>[] = [];
    const eventNames = [
      "client-connected",
      "client-disconnected",
      "partial-text",
      "final-text",
      "ptt-started",
    ];
    for (const name of eventNames) {
      unlisteners.push(
        listen<ServerEvent>(name, (e) => handleEvent(e.payload)),
      );
    }
    return () => {
      unlisteners.forEach((p) => p.then((fn) => fn()));
    };
  }, [handleEvent]);

  return clients;
}
