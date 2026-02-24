import { describe, it, expect } from "vitest";
import {
  createClientState,
  applyEvent,
  type ClientState,
  type ServerEvent,
} from "./messages";

function emptyState(): Map<string, ClientState> {
  return new Map();
}

function stateWith(client: ClientState): Map<string, ClientState> {
  return new Map([[client.clientId, client]]);
}

describe("applyEvent", () => {
  it("adds client on ClientConnected", () => {
    const state = emptyState();
    const event: ServerEvent = {
      kind: "ClientConnected",
      client_id: "c1",
      device_model: "iPhone 15",
    };

    const next = applyEvent(state, event);

    expect(next.size).toBe(1);
    const client = next.get("c1");
    expect(client).toBeDefined();
    expect(client!.clientId).toBe("c1");
    expect(client!.deviceModel).toBe("iPhone 15");
    expect(client!.connected).toBe(true);
    expect(client!.currentSession).toBeNull();
    expect(client!.partialText).toBe("");
    expect(client!.finalTexts).toEqual([]);
  });

  it("marks client disconnected on ClientDisconnected", () => {
    const client = createClientState("c1", "Pixel 8");
    const state = stateWith(client);
    const event: ServerEvent = {
      kind: "ClientDisconnected",
      client_id: "c1",
    };

    const next = applyEvent(state, event);

    expect(next.get("c1")!.connected).toBe(false);
  });

  it("updates partial text", () => {
    const client = createClientState("c1", "Pixel 8");
    client.currentSession = "s1";
    const state = stateWith(client);
    const event: ServerEvent = {
      kind: "PartialText",
      client_id: "c1",
      session_id: "s1",
      text: "hello wor",
      seq: 1,
      confidence: 0.8,
    };

    const next = applyEvent(state, event);

    expect(next.get("c1")!.partialText).toBe("hello wor");
  });

  it("appends final text and clears partial", () => {
    const client = createClientState("c1", "Pixel 8");
    client.currentSession = "s1";
    client.partialText = "hello world";
    const state = stateWith(client);
    const event: ServerEvent = {
      kind: "FinalText",
      client_id: "c1",
      session_id: "s1",
      text: "hello world",
      confidence: 0.95,
    };

    const next = applyEvent(state, event);

    const updated = next.get("c1")!;
    expect(updated.finalTexts).toEqual(["hello world"]);
    expect(updated.partialText).toBe("");
    expect(updated.currentSession).toBeNull();
  });

  it("sets session on PttStarted", () => {
    const client = createClientState("c1", "Pixel 8");
    const state = stateWith(client);
    const event: ServerEvent = {
      kind: "PttStarted",
      client_id: "c1",
      session_id: "s42",
    };

    const next = applyEvent(state, event);

    expect(next.get("c1")!.currentSession).toBe("s42");
  });

  it("ignores events for unknown clients", () => {
    const state = emptyState();
    const events: ServerEvent[] = [
      { kind: "ClientDisconnected", client_id: "unknown" },
      {
        kind: "PartialText",
        client_id: "unknown",
        session_id: "s1",
        text: "hi",
        seq: 1,
        confidence: 0.9,
      },
      {
        kind: "FinalText",
        client_id: "unknown",
        session_id: "s1",
        text: "hi",
        confidence: 0.9,
      },
      { kind: "PttStarted", client_id: "unknown", session_id: "s1" },
    ];

    for (const event of events) {
      const next = applyEvent(state, event);
      expect(next.size).toBe(0);
    }
  });
});
