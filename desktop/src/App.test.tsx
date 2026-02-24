import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import App from "./App";

// Mock Tauri API
vi.mock("@tauri-apps/api/event", () => ({
  listen: vi.fn(() => Promise.resolve(() => {})),
}));
vi.mock("@tauri-apps/api/core", () => ({
  invoke: vi.fn(() => Promise.resolve(9876)),
}));

describe("App", () => {
  it("renders app title", () => {
    render(<App />);
    expect(screen.getByText(/PTT Dictation/)).toBeInTheDocument();
  });
  it("renders client list area", () => {
    render(<App />);
    expect(
      screen.getByText(/연결된 클라이언트가 없습니다/),
    ).toBeInTheDocument();
  });
  it("renders dictation view area", () => {
    render(<App />);
    expect(screen.getByText(/클라이언트를 선택/)).toBeInTheDocument();
  });
});
