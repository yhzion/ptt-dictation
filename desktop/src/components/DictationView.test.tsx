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
    expect(screen.getByText(/클라이언트를 선택/)).toBeInTheDocument();
  });

  it("shows device info", () => {
    render(<DictationView client={makeClient()} />);
    expect(screen.getByText(/Galaxy S23/)).toBeInTheDocument();
  });

  it("shows partial text with typing indicator", () => {
    render(
      <DictationView
        client={makeClient({
          partialText: "안녕하세요",
          currentSession: "s-1",
        })}
      />,
    );
    expect(screen.getByText(/안녕하세요/)).toBeInTheDocument();
    expect(screen.getByTestId("typing-indicator")).toBeInTheDocument();
  });

  it("shows final texts in history", () => {
    render(
      <DictationView
        client={makeClient({
          finalTexts: ["첫 번째 문장.", "두 번째 문장."],
        })}
      />,
    );
    expect(screen.getByText("첫 번째 문장.")).toBeInTheDocument();
    expect(screen.getByText("두 번째 문장.")).toBeInTheDocument();
  });

  it("shows disconnected badge when not connected", () => {
    render(<DictationView client={makeClient({ connected: false })} />);
    expect(screen.getByText(/연결 끊김/)).toBeInTheDocument();
  });

  it("shows listening state during active session", () => {
    render(<DictationView client={makeClient({ currentSession: "s-1" })} />);
    expect(screen.getByTestId("listening-badge")).toBeInTheDocument();
  });
});
