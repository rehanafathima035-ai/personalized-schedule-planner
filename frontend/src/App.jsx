import { useState } from 'react';
import { AuthProvider, useAuth } from './context/AuthContext';
import Dashboard from './pages/Dashboard';
import CreateHabit from './pages/CreateHabit';
import CreateTask from './pages/CreateTask';
import PrayerSettings from './pages/PrayerSettings';
import Goals from './pages/Goals';
import SignIn from './pages/SignIn';
import './app.css';

function Shell() {
  const { user, loading, signOut } = useAuth();
  const [view, setView] = useState('dashboard');

  if (loading) return <p className="muted centred">Loading…</p>;
  if (!user) return <SignIn />;

  return (
    <div className="shell">
      <nav className="shell__nav">
        <span className="shell__wordmark">Life Planner</span>

        <div className="shell__links">
          <button
            type="button"
            className={view === 'dashboard' ? 'is-current' : ''}
            onClick={() => setView('dashboard')}
          >
            This week
          </button>

          <button
            type="button"
            className={view === 'create' ? 'is-current' : ''}
            onClick={() => setView('create')}
          >
            Add something
          </button>

          <button
            type="button"
            className={view === 'task' ? 'is-current' : ''}
            onClick={() => setView('task')}
          >
            Add Task
          </button>

          <button
            type="button"
            className={view === 'prayers' ? 'is-current' : ''}
            onClick={() => setView('prayers')}
          >
            Prayer Times
          </button>

          <button
            type="button"
            className={view === 'goals' ? 'is-current' : ''}
            onClick={() => setView('goals')}
          >
            Goals
          </button>

          <button type="button" onClick={signOut}>
            Sign out
          </button>
        </div>
      </nav>

      <main className="shell__main">
        {view === 'dashboard' && (
          <Dashboard
            onCreate={() => setView('create')}
          />
        )}

        {view === 'create' && (
          <CreateHabit
            onDone={() => setView('dashboard')}
          />
        )}

        {view === 'task' && (
          <CreateTask
            onDone={() => setView('dashboard')}
          />
        )}

        {view === 'prayers' && (
          <PrayerSettings
            onDone={() => setView('dashboard')}
          />
        )}

        {view === 'goals' && <Goals />}
      </main>
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      <Shell />
    </AuthProvider>
  );
}