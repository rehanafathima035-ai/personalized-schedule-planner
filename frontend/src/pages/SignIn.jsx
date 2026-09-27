import { useState } from 'react';
import { useAuth } from '../context/AuthContext';

export default function SignIn() {
  const { signIn, signUp } = useAuth();
  const [mode, setMode] = useState('signin');
  const [form, setForm] = useState({ email: '', password: '', displayName: '' });
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const update = (field) => (event) =>
    setForm({ ...form, [field]: event.target.value });

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === 'signin') {
        await signIn({ email: form.email, password: form.password });
      } else {
        await signUp(form);
      }
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="signin">
      <div className="signin__panel">
        <h1 className="signin__wordmark">Life Planner</h1>
        <p className="signin__pitch">
          Tell it the life you want to build. It works out a week that holds it.
        </p>

        <form onSubmit={handleSubmit}>
          {mode === 'signup' && (
            <label className="field">
              <span>What should I call you?</span>
              <input value={form.displayName} onChange={update('displayName')} required />
            </label>
          )}
          <label className="field">
            <span>Email</span>
            <input type="email" value={form.email} onChange={update('email')} required />
          </label>
          <label className="field">
            <span>Password</span>
            <input type="password" value={form.password} onChange={update('password')}
                   minLength={8} required />
          </label>

          {error && <p className="error" role="alert">{error}</p>}

          <button type="submit" className="button button--primary" disabled={busy}>
            {busy ? 'One moment…' : mode === 'signin' ? 'Sign in' : 'Create account'}
          </button>
        </form>

        <button type="button" className="button button--quiet"
                onClick={() => { setMode(mode === 'signin' ? 'signup' : 'signin'); setError(null); }}>
          {mode === 'signin' ? 'I need an account' : 'I already have an account'}
        </button>
      </div>
    </div>
  );
}
