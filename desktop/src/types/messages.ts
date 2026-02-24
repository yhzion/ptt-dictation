export interface ClientConnectedEvent {
  kind: "ClientConnected";
  client_id: string;
  device_model: string;
}

export interface ClientDisconnectedEvent {
  kind: "ClientDisconnected";
  client_id: string;
}

export interface PartialTextEvent {
  kind: "PartialText";
  client_id: string;
  session_id: string;
  text: string;
  seq: number;
  confidence: number;
}

export interface FinalTextEvent {
  kind: "FinalText";
  client_id: string;
  session_id: string;
  text: string;
  confidence: number;
}

export interface PttStartedEvent {
  kind: "PttStarted";
  client_id: string;
  session_id: string;
}

export type ServerEvent =
  | ClientConnectedEvent
  | ClientDisconnectedEvent
  | PartialTextEvent
  | FinalTextEvent
  | PttStartedEvent;

export interface ClientState {
  clientId: string;
  deviceModel: string;
  connected: boolean;
  currentSession: string | null;
  partialText: string;
  finalTexts: string[];
}

export function createClientState(
  clientId: string,
  deviceModel: string,
): ClientState {
  return {
    clientId,
    deviceModel,
    connected: true,
    currentSession: null,
    partialText: "",
    finalTexts: [],
  };
}

export function applyEvent(
  state: Map<string, ClientState>,
  event: ServerEvent,
): Map<string, ClientState> {
  if (event.kind === "ClientConnected") {
    const next = new Map(state);
    next.set(
      event.client_id,
      createClientState(event.client_id, event.device_model),
    );
    return next;
  }

  const existing = state.get(event.client_id);
  if (!existing) return state;

  const next = new Map(state);

  switch (event.kind) {
    case "ClientDisconnected":
      next.set(event.client_id, { ...existing, connected: false });
      break;
    case "PartialText":
      next.set(event.client_id, { ...existing, partialText: event.text });
      break;
    case "FinalText":
      next.set(event.client_id, {
        ...existing,
        finalTexts: [...existing.finalTexts, event.text],
        partialText: "",
        currentSession: null,
      });
      break;
    case "PttStarted":
      next.set(event.client_id, {
        ...existing,
        currentSession: event.session_id,
      });
      break;
  }

  return next;
}
