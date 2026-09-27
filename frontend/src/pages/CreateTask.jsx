import { useEffect, useState } from 'react';
import { api } from '../services/api';

export default function CreateTask({ onDone }) {
  const [goals, setGoals] = useState([]);
  const [milestones, setMilestones] = useState([]);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [dueDate, setDueDate] = useState('');
  const [durationMinutes, setDurationMinutes] = useState(30);
  const [priority, setPriority] = useState('MEDIUM');
  const [flexibility, setFlexibility] = useState('FLEXIBLE');
  const [goalId, setGoalId] = useState('');
  const [milestoneId, setMilestoneId] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.listGoals()
      .then(setGoals)
      .catch((failure) => setError(failure.message));
  }, []);

  useEffect(() => {
    setMilestoneId('');

    if (!goalId) {
      setMilestones([]);
      return;
    }

    api.listMilestones(goalId)
      .then(setMilestones)
      .catch((failure) => setError(failure.message));
  }, [goalId]);

  async function handleSubmit(event) {
    event.preventDefault();

    if (!title.trim()) {
      setError('Task title is required.');
      return;
    }

    setLoading(true);
    setError(null);

    try {
      await api.createTask({
        title: title.trim(),
        description: description.trim() || null,
        dueDate: dueDate || null,
        durationMinutes: Number(durationMinutes),
        priority,
        flexibility,
        goalId: goalId ? Number(goalId) : null,
        milestoneId: milestoneId ? Number(milestoneId) : null,
      });

      onDone?.();
    } catch (failure) {
      setError(failure.message);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="create">
      <h1 className="page-title">Create a task</h1>

      <p className="muted">
        Add a task and connect it to one of your goals.
      </p>

      <form className="draft" onSubmit={handleSubmit}>
        <label className="field">
          <span>Task title</span>
          <input
            type="text"
            value={title}
            placeholder="Complete DSA practice"
            onChange={(event) => setTitle(event.target.value)}
          />
        </label>

        <label className="field">
          <span>Description</span>
          <textarea
            value={description}
            placeholder="What needs to be completed?"
            onChange={(event) => setDescription(event.target.value)}
            rows="3"
          />
        </label>

        <div className="field-row">
          <label className="field">
            <span>Due date</span>
            <input
              type="date"
              value={dueDate}
              onChange={(event) => setDueDate(event.target.value)}
            />
          </label>

          <label className="field">
            <span>Duration (minutes)</span>
            <input
              type="number"
              min="1"
              max="1440"
              value={durationMinutes}
              onChange={(event) =>
                setDurationMinutes(event.target.value)
              }
            />
          </label>
        </div>

        <div className="field-row">
          <label className="field">
            <span>Priority</span>
            <select
              value={priority}
              onChange={(event) => setPriority(event.target.value)}
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
              value={flexibility}
              onChange={(event) =>
                setFlexibility(event.target.value)
              }
            >
              <option value="FIXED">Fixed</option>
              <option value="PREFERRED">Preferred</option>
              <option value="FLEXIBLE">Flexible</option>
            </select>
          </label>
        </div>

        <label className="field">
          <span>Goal</span>
          <select
            value={goalId}
            onChange={(event) => setGoalId(event.target.value)}
          >
            <option value="">No goal</option>

            {goals.map((goal) => (
              <option key={goal.id} value={goal.id}>
                {goal.title}
              </option>
            ))}
          </select>
        </label>

        {goalId && (
          <label className="field">
            <span>Milestone</span>
            <select
              value={milestoneId}
              onChange={(event) =>
                setMilestoneId(event.target.value)
              }
            >
              <option value="">No milestone</option>

              {milestones.map((milestone) => (
                <option
                  key={milestone.id}
                  value={milestone.id}
                >
                  {milestone.title}
                </option>
              ))}
            </select>
          </label>
        )}

        {error && (
          <p className="error" role="alert">
            {error}
          </p>
        )}

        <div>
          <button
            type="submit"
            className="button button--primary"
            disabled={loading}
          >
            {loading ? 'Creating…' : 'Create task'}
          </button>

          <button
            type="button"
            className="button button--secondary"
            onClick={onDone}
            disabled={loading}
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}