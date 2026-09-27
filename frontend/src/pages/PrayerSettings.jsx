import { useEffect, useState } from 'react';
import { api } from '../services/api';

const PRAYERS = [
  ['fajrTime', 'Fajr'],
  ['dhuhrTime', 'Dhuhr'],
  ['asrTime', 'Asr'],
  ['maghribTime', 'Maghrib'],
  ['ishaTime', 'Isha'],
];

export default function PrayerSettings({ onDone }) {
  const [times, setTimes] = useState({
    fajrTime: '05:00',
    dhuhrTime: '12:30',
    asrTime: '15:45',
    maghribTime: '18:15',
    ishaTime: '19:30',
  });

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    api.prayerSettings()
      .then((data) => {
        setTimes({
          fajrTime: data.fajrTime?.slice(0, 5) || '05:00',
          dhuhrTime: data.dhuhrTime?.slice(0, 5) || '12:30',
          asrTime: data.asrTime?.slice(0, 5) || '15:45',
          maghribTime: data.maghribTime?.slice(0, 5) || '18:15',
          ishaTime: data.ishaTime?.slice(0, 5) || '19:30',
        });
      })
      .catch(() => {
        setMessage('Set your prayer times below.');
      })
      .finally(() => setLoading(false));
  }, []);

  async function handleSave(event) {
    event.preventDefault();
    setSaving(true);
    setError('');
    setMessage('');

    try {
      await api.savePrayerSettings({
        fajrTime: `${times.fajrTime}:00`,
        dhuhrTime: `${times.dhuhrTime}:00`,
        asrTime: `${times.asrTime}:00`,
        maghribTime: `${times.maghribTime}:00`,
        ishaTime: `${times.ishaTime}:00`,
      });

      setMessage('Prayer times saved.');
    } catch (failure) {
      setError(failure.message);
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <p className="muted">Loading prayer settings…</p>;
  }

  return (
    <section className="create">
      <h1 className="page-title">Prayer Times</h1>

      <p className="muted">
        These times are treated as fixed commitments when your schedule is planned.
      </p>

      <form onSubmit={handleSave}>
        {PRAYERS.map(([key, label]) => (
          <label className="field" key={key}>
            <span>{label}</span>
            <input
              type="time"
              value={times[key]}
              onChange={(event) =>
                setTimes({
                  ...times,
                  [key]: event.target.value,
                })
              }
              required
            />
          </label>
        ))}

        {error && <p className="error" role="alert">{error}</p>}
        {message && <p className="notice notice--soft">{message}</p>}

        <button
          type="submit"
          className="button button--primary"
          disabled={saving}
        >
          {saving ? 'Saving…' : 'Save Prayer Times'}
        </button>

        {onDone && (
          <button
            type="button"
            className="button button--secondary"
            onClick={onDone}
          >
            Back to Schedule
          </button>
        )}
      </form>
    </section>
  );
}