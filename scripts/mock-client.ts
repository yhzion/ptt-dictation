// scripts/mock-client.ts
// Usage: npx tsx scripts/mock-client.ts [port]
// Simulates an Android PTT client sending messages via WebSocket

import WebSocket from "ws";

const port = process.argv[2] || "9876";
const ws = new WebSocket(`ws://localhost:${port}`);
const clientId = "mock-phone-01";

ws.on("open", () => {
  console.log("Connected to server");

  // Send HELLO
  ws.send(JSON.stringify({
    type: "HELLO",
    clientId,
    payload: { deviceModel: "Mock Device", engine: "MockSTT", capabilities: ["WS"] },
  }));

  // Simulate PTT session after 1 second
  setTimeout(() => {
    const sessionId = `s-${Date.now()}`;
    ws.send(JSON.stringify({ type: "PTT_START", clientId, payload: { sessionId } }));
    console.log("PTT_START sent");

    // Send partials
    const partials = ["hello", "hello world", "hello world today", "hello world today meeting"];
    partials.forEach((text, i) => {
      setTimeout(() => {
        ws.send(JSON.stringify({
          type: "PARTIAL", clientId, timestamp: Date.now(),
          payload: { sessionId, seq: i + 1, text, confidence: 0.5 + i * 0.1 },
        }));
        console.log(`PARTIAL: ${text}`);
      }, (i + 1) * 300);
    });

    // Send FINAL
    setTimeout(() => {
      ws.send(JSON.stringify({
        type: "FINAL", clientId, timestamp: Date.now(),
        payload: { sessionId, text: "Hello. Today's meeting is at 3 PM.", confidence: 0.95 },
      }));
      console.log("FINAL sent");
    }, 2000);
  }, 1000);

  // Heartbeat every 5s
  setInterval(() => {
    ws.send(JSON.stringify({ type: "HEARTBEAT", clientId }));
  }, 5000);
});

ws.on("message", (data) => { console.log("Received:", data.toString()); });
ws.on("close", () => console.log("Disconnected"));
ws.on("error", (err) => console.error("Error:", err.message));
