import type { ClientState } from "../types/messages";

interface Props {
  client: ClientState | null;
}

export function DictationView({ client }: Props) {
  if (!client) {
    return (
      <div className="dictation-empty">
        <p>클라이언트를 선택해 주세요</p>
      </div>
    );
  }

  return (
    <div className="dictation-view">
      <div className="dictation-header">
        <span className="device-name">{client.deviceModel}</span>
        {!client.connected && (
          <span className="badge badge-disconnected">연결 끊김</span>
        )}
        {client.currentSession && (
          <span className="badge badge-listening" data-testid="listening-badge">
            듣는 중...
          </span>
        )}
      </div>
      {client.partialText && (
        <div className="partial-text">
          <p>{client.partialText}</p>
          <span data-testid="typing-indicator" className="typing-indicator">
            ...
          </span>
        </div>
      )}
      <div className="final-texts">
        {client.finalTexts.map((text, i) => (
          <div key={i} className="final-text-item">
            {text}
          </div>
        ))}
      </div>
    </div>
  );
}
