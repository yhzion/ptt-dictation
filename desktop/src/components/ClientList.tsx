import type { ClientState } from "../types/messages";

interface Props {
  clients: Map<string, ClientState>;
  selectedId: string | null;
  onSelect: (clientId: string) => void;
}

export function ClientList({ clients, selectedId, onSelect }: Props) {
  if (clients.size === 0) {
    return (
      <div className="client-list-empty">
        <p>연결된 클라이언트가 없습니다</p>
      </div>
    );
  }

  return (
    <div className="client-list">
      {Array.from(clients.values()).map((client) => (
        <div
          key={client.clientId}
          data-testid={`client-${client.clientId}`}
          className={`client-item ${selectedId === client.clientId ? "selected" : ""}`}
          onClick={() => onSelect(client.clientId)}
        >
          <span
            data-testid="connection-indicator"
            className={`indicator ${client.connected ? "connected" : "disconnected"}`}
          />
          <span className="device-model">{client.deviceModel}</span>
        </div>
      ))}
    </div>
  );
}
