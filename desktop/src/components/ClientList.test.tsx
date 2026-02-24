import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ClientList } from "./ClientList";
import { type ClientState } from "../types/messages";

function makeClients(): Map<string, ClientState> {
  const map = new Map<string, ClientState>();
  map.set("phone-01", {
    clientId: "phone-01",
    deviceModel: "Galaxy S23",
    connected: true,
    currentSession: null,
    partialText: "",
    finalTexts: [],
  });
  map.set("phone-02", {
    clientId: "phone-02",
    deviceModel: "Pixel 7",
    connected: false,
    currentSession: null,
    partialText: "",
    finalTexts: [],
  });
  return map;
}

describe("ClientList", () => {
  it("shows empty state when no clients", () => {
    render(
      <ClientList clients={new Map()} selectedId={null} onSelect={() => {}} />,
    );
    expect(screen.getByText(/No connected clients/)).toBeInTheDocument();
  });

  it("renders client entries with device model", () => {
    render(
      <ClientList
        clients={makeClients()}
        selectedId={null}
        onSelect={() => {}}
      />,
    );
    expect(screen.getByText("Galaxy S23")).toBeInTheDocument();
    expect(screen.getByText("Pixel 7")).toBeInTheDocument();
  });

  it("shows connected/disconnected status", () => {
    render(
      <ClientList
        clients={makeClients()}
        selectedId={null}
        onSelect={() => {}}
      />,
    );
    const indicators = screen.getAllByTestId("connection-indicator");
    expect(indicators).toHaveLength(2);
  });

  it("calls onSelect when client clicked", async () => {
    const onSelect = vi.fn();
    render(
      <ClientList
        clients={makeClients()}
        selectedId={null}
        onSelect={onSelect}
      />,
    );
    await userEvent.click(screen.getByText("Galaxy S23"));
    expect(onSelect).toHaveBeenCalledWith("phone-01");
  });

  it("highlights selected client", () => {
    render(
      <ClientList
        clients={makeClients()}
        selectedId="phone-01"
        onSelect={() => {}}
      />,
    );
    const selected = screen.getByTestId("client-phone-01");
    expect(selected).toHaveClass("selected");
  });
});
