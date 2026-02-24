interface Props {
  port: number;
}

export function Settings({ port }: Props) {
  return (
    <div className="settings">
      <div className="setting-item">
        <span className="setting-label">Server Port</span>
        <span className="setting-value">{port}</span>
      </div>
    </div>
  );
}
