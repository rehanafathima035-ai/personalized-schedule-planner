import { useMemo, useState } from 'react';
import { api } from '../services/api';
import { isoDate, startOfWeek, weekDays } from '../utils/time';
import ProposalReview from '../components/ProposalReview';

const DAY_NAMES = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

export default function CreateHabit({ onDone }) {
  const [text, setText] = useState('');
  const [draft, setDraft] = useState(null);
  const [questions, setQuestions] = useState([]);
  const [preview, setPreview] = useState(null);
  const [error, setError] = useState(null);
  const [busy, setBusy] = useState(false);

  const week = useMemo(() => {
    const today = isoDate(new Date());
    return weekDays(startOfWeek()).map((day) => ({
      iso: isoDate(day),
      short: day.toLocaleDateString(undefined, { weekday: 'short' }),
      date: day.getDate(),
      isToday: isoDate(day) === today,
    }));
  }, []);

  async function run(action) {
    setError(null);
    setBusy(true);
    try {
      await action();
    } catch (failure) {
      setError(failure.message);
    } finally {
      setBusy(false);
    }
  }

  const handleInterpret = () =>
    run(async () => {
      const result = await api.interpret(text);
      const reading = result.interpretation;

      setQuestions(reading.questions ?? []);

      setDraft({
        name: reading.name || text,
        description: '',
        category: '',
        trackingType: 'CHECKBOX',
        durationMinutes: reading.duration_minutes ?? 30,
        occurrences: reading.occurrences ?? 1,
        frequencyType: reading.frequency_type ?? 'WEEKLY',
        preferredDays: reading.preferred_days ?? [],
        excludedDays: [],
        preferredTimeOfDay: reading.preferred_time_of_day ?? 'ANY',
        preferredStartMinute: reading.preferred_start_minute ?? null,
        minSpacingDays: reading.min_spacing_days ?? null,
        maxSpacingDays: reading.max_spacing_days ?? null,
        priority: 'MEDIUM',
        flexibility: 'FLEXIBLE',
        startDate: null,
        endDate: null,
      });

      setPreview(null);
    });

  const update = (key, value) => {
    setDraft((current) => ({ ...current, [key]: value }));
  };

  const handlePreview = () =>
    run(async () => {
      const result = await api.previewHabit({
        draft,
        horizonStart: week[0].iso,
        horizonDays: 7,
      });
      setPreview(result);
    });

  const handleApprove = () =>
    run(async () => {
      await api.approveProposal(preview.proposalId);
      onDone?.();
    });

  const handleReject = () =>
    run(async () => {
      await api.rejectProposal(preview.proposalId);
      setPreview(null);
    });

  return (
    <div className="create">
      <h1 className="page-title">What do you want to make room for?</h1>

      <label className="field">
        <span>Tell me what you want to do</span>
        <input
          type="text"
          value={text}
          placeholder="Study Python 5 times a week at 7 PM for 1 hour"
          onChange={(event) => setText(event.target.value)}
        />
      </label>

      <button
        type="button"
        className="button button--primary"
        onClick={handleInterpret}
        disabled={busy || text.trim().length === 0}
      >
        Understand my plan
      </button>

      {error && <p className="error" role="alert">{error}</p>}

      {draft && !preview && (
        <section className="draft">
          <h2>Let's set it up</h2>

          <label className="field">
            <span>Name</span>
            <input
              value={draft.name}
              onChange={(event) => update('name', event.target.value)}
            />
          </label>

          <div className="field-row">
            <label className="field">
              <span>Frequency</span>
              <select
                value={draft.frequencyType}
                onChange={(event) => update('frequencyType', event.target.value)}
              >
                <option value="DAILY">Daily</option>
                <option value="WEEKLY">Weekly</option>
                <option value="MONTHLY">Monthly</option>
                <option value="YEARLY">Yearly</option>
                <option value="INTERVAL">Every X days</option>
                <option value="CUSTOM">Custom</option>
              </select>
            </label>

            <label className="field">
              <span>How many times?</span>
              <input
                type="number"
                min="1"
                max="100"
                value={draft.occurrences}
                onChange={(event) => update('occurrences', Number(event.target.value))}
              />
            </label>

            <label className="field">
              <span>Duration (minutes)</span>
              <input
                type="number"
                min="1"
                max="1440"
                value={draft.durationMinutes}
                onChange={(event) => update('durationMinutes', Number(event.target.value))}
              />
            </label>
          </div>

          {draft.frequencyType === 'INTERVAL' && (
            <label className="field">
              <span>Repeat every how many days?</span>
              <input
                type="number"
                min="1"
                max="365"
                value={draft.minSpacingDays ?? ''}
                onChange={(event) =>
                  update(
                    'minSpacingDays',
                    event.target.value === '' ? null : Number(event.target.value)
                  )
                }
              />
            </label>
          )}

          <fieldset className="field">
            <legend>Preferred days</legend>
            <div className="daypick">
              {DAY_NAMES.map((day) => {
                const on = draft.preferredDays.includes(day);

                return (
                  <button
                    key={day}
                    type="button"
                    className={`daypick__day ${on ? 'is-on' : ''}`}
                    aria-pressed={on}
                    onClick={() =>
                      update(
                        'preferredDays',
                        on
                          ? draft.preferredDays.filter((d) => d !== day)
                          : [...draft.preferredDays, day]
                      )
                    }
                  >
                    {day.slice(0, 3)}
                  </button>
                );
              })}
            </div>
          </fieldset>

          <div className="field-row">
            <label className="field">
              <span>Time preference</span>
              <select
                value={draft.preferredTimeOfDay}
                onChange={(event) => update('preferredTimeOfDay', event.target.value)}
              >
                <option value="ANY">Any time</option>
                <option value="MORNING">Morning</option>
                <option value="AFTERNOON">Afternoon</option>
                <option value="EVENING">Evening</option>
                <option value="NIGHT">Night</option>
              </select>
            </label>

            <label className="field">
              <span>Specific start time</span>
              <input
                type="time"
                value={
                  draft.preferredStartMinute == null
                    ? ''
                    : `${String(Math.floor(draft.preferredStartMinute / 60)).padStart(2, '0')}:${String(draft.preferredStartMinute % 60).padStart(2, '0')}`
                }
                onChange={(event) => {
                  if (!event.target.value) {
                    update('preferredStartMinute', null);
                    return;
                  }

                  const [hours, minutes] = event.target.value.split(':').map(Number);
                  update('preferredStartMinute', hours * 60 + minutes);
                }}
              />
            </label>
          </div>

          <div className="field-row">
            <label className="field">
              <span>Priority</span>
              <select
                value={draft.priority}
                onChange={(event) => update('priority', event.target.value)}
              >
                <option value="LOW">Low</option>
                <option value="MEDIUM">Medium</option>
                <option value="HIGH">High</option>
                <option value="CRITICAL">Critical</option>
              </select>
            </label>

            <label className="field">
              <span>Scheduling</span>
              <select
                value={draft.flexibility}
                onChange={(event) => update('flexibility', event.target.value)}
              >
                <option value="FIXED">Fixed</option>
                <option value="PREFERRED">Preferred</option>
                <option value="FLEXIBLE">Flexible</option>
              </select>
            </label>
          </div>

          <div className="field-row">
            <label className="field">
              <span>Start date</span>
              <input
                type="date"
                value={draft.startDate ?? ''}
                onChange={(event) => update('startDate', event.target.value || null)}
              />
            </label>

            <label className="field">
              <span>End date</span>
              <input
                type="date"
                value={draft.endDate ?? ''}
                onChange={(event) => update('endDate', event.target.value || null)}
              />
            </label>
          </div>

          {questions.length > 0 && (
            <div className="notice notice--soft">
              <h3>A couple of things I could not tell</h3>
              <ul>
                {questions.map((q, i) => <li key={i}>{q}</li>)}
              </ul>
            </div>
          )}

          <button
            type="button"
            className="button button--primary"
            onClick={handlePreview}
            disabled={busy}
          >
            {busy ? 'Finding the best time…' : 'Find a place for it'}
          </button>
        </section>
      )}

      {preview && (
        <ProposalReview
          proposal={preview.proposal}
          alternatives={preview.alternatives}
          days={week}
          busy={busy}
          onApprove={handleApprove}
          onReject={handleReject}
          onChooseAlternative={(alternative) =>
            setPreview({ ...preview, proposal: alternative })
          }
        />
      )}
    </div>
  );
}
