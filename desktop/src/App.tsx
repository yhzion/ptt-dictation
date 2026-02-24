import { useState } from "react";
import { ClientList } from "./components/ClientList";
import { DictationView } from "./components/DictationView";
import { Settings } from "./components/Settings";
import { useClientStates } from "./hooks/useTauriEvents";

function App() {
  const clients = useClientStates();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selectedClient = selectedId ? (clients.get(selectedId) ?? null) : null;

  return (
    <div className="app">
      <header className="app-header">
        <h1>PTT Dictation</h1>
      </header>
      <div className="app-body">
        <aside className="sidebar">
          <ClientList
            clients={clients}
            selectedId={selectedId}
            onSelect={setSelectedId}
          />
          <Settings port={9876} />
        </aside>
        <main className="main-content">
          <DictationView client={selectedClient} />
        </main>
      </div>
    </div>
  );
}

export default App;
