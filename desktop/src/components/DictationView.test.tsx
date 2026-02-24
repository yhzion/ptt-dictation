import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { DictationView } from "./DictationView";
import { type ClientState } from "../types/messages";

function makeClient(overrides: Partial<ClientState> = {}): ClientState {
  return {
    clientId: "phone-01",
    deviceModel: "Galaxy S23",
    connected: true,
    currentSession: null,
    partialText: "",
    finalTexts: [],
    ...overrides,
  };
}

describe("DictationView", () => {
  it("shows empty state when no client selected", () => {
    render(<DictationView client={null} />);
    expect(screen.getByText(/Select a client/)).toBeInTheDocument();
  });

  it("shows device info", () => {
    render(<DictationView client={makeClient()} />);
    expect(screen.getByText(/Galaxy S23/)).toBeInTheDocument();
  });

  it("shows partial text with typing indicator", () => {
    render(
      <DictationView
        client={makeClient({
          partialText: "hello",
          currentSession: "s-1",
        })}
      />,
    );
    expect(screen.getByText(/hello/)).toBeInTheDocument();
    expect(screen.getByTestId("typing-indicator")).toBeInTheDocument();
  });

  it("shows final texts in history", () => {
    render(
      <DictationView
        client={makeClient({
          finalTexts: ["First sentence.", "Second sentence."],
        })}
      />,
    );
    expect(screen.getByText("First sentence.")).toBeInTheDocument();
    expect(screen.getByText("Second sentence.")).toBeInTheDocument();
  });

  it("shows disconnected badge when not connected", () => {
    render(<DictationView client={makeClient({ connected: false })} />);
    expect(screen.getByText(/Disconnected/)).toBeInTheDocument();
  });

  it("shows listening state during active session", () => {
    render(<DictationView client={makeClient({ currentSession: "s-1" })} />);
    expect(screen.getByTestId("listening-badge")).toBeInTheDocument();
  });
});
