interface Props {
  port: number;
}

export function Settings({ port }: Props) {
  return (
    <div className="settings">
      <div className="setting-item">
        <span className="setting-label">서버 포트</span>
        <span className="setting-value">{port}</span>
      </div>
    </div>
  );
}
