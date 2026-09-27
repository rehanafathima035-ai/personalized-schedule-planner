import { useEffect, useState } from 'react';
import { api } from '../services/api';

export default function Goals() {
  const [goals, setGoals] = useState([]);
  const [selectedGoal, setSelectedGoal] = useState(null);
  const [progress, setProgress] = useState(null);
  const [milestones, setMilestones] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);

  const [form, setForm] = useState({
    title: '',
    description: '',
    category: '',
    targetYear: new Date().getFullYear(),
    targetDate: '',
    targetCount: 1,
  });

  async function loadGoals() {
    try {
      setLoading(true);
      const data = await api.listGoals();
      setGoals(data || []);
    } catch (error) {
      console.error(error);
    } finally {
      setLoading(false);
    }
  }

  async function selectGoal(goal) {
    try {
      setSelectedGoal(goal);

      const [goalProgress, goalMilestones] = await Promise.all([
        api.getGoalProgress(goal.id),
        api.listMilestones(goal.id),
      ]);

      setProgress(goalProgress);
      setMilestones(goalMilestones || []);
    } catch (error) {
      console.error(error);
    }
  }

  useEffect(() => {
    loadGoals();
  }, []);

  async function createGoal(event) {
    event.preventDefault();

    if (!form.title.trim()) return;

    try {
      await api.createGoal({
        ...form,
        targetYear: Number(form.targetYear),
        targetCount: Number(form.targetCount),
      });

      setForm({
        title: '',
        description: '',
        category: '',
        targetYear: new Date().getFullYear(),
        targetDate: '',
        targetCount: 1,
      });

      setShowForm(false);
      await loadGoals();
    } catch (error) {
      console.error(error);
    }
  }

  async function deleteGoal(id) {
    if (!window.confirm('Delete this goal?')) return;

    try {
      await api.deleteGoal(id);

      if (selectedGoal?.id === id) {
        setSelectedGoal(null);
        setProgress(null);
        setMilestones([]);
      }

      await loadGoals();
    } catch (error) {
      console.error(error);
    }
  }

  async function toggleMilestone(milestone) {
    if (!selectedGoal) return;

    try {
      if (milestone.completedAt) {
        await api.reopenMilestone(selectedGoal.id, milestone.id);
      } else {
        await api.completeMilestone(selectedGoal.id, milestone.id);
      }

      await selectGoal(selectedGoal);
    } catch (error) {
      console.error(error);
    }
  }

  function statusClass(status) {
    return `goal-status goal-status--${status?.toLowerCase()}`;
  }

  function getGoalPercentage(goal) {
    if (!goal.targetCount || goal.targetCount <= 0) return 0;

    return Math.min(
      100,
      Math.round((goal.achievedCount / goal.targetCount) * 100)
    );
  }

  return (
    <div className="goals-page">

      <div className="goals-header">
        <div>
          <p className="goals-eyebrow">LONG-TERM PLANNING</p>
          <h1>Goals</h1>
          <p className="goals-subtitle">
            Turn what you want to achieve into clear milestones and measurable progress.
          </p>
        </div>

        <button
          className="goal-primary-button"
          onClick={() => setShowForm(!showForm)}
        >
          {showForm ? 'Cancel' : '+ New goal'}
        </button>
      </div>

      {showForm && (
        <section className="goal-form-card">
          <h2>Create a goal</h2>

          <form onSubmit={createGoal}>

            <div className="goal-form-grid">

              <label>
                Goal title
                <input
                  type="text"
                  placeholder="Example: Become a full-stack developer"
                  value={form.title}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      title: e.target.value,
                    })
                  }
                  required
                />
              </label>

              <label>
                Category
                <input
                  type="text"
                  placeholder="Career, Education, Personal..."
                  value={form.category}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      category: e.target.value,
                    })
                  }
                />
              </label>

              <label>
                Target date
                <input
                  type="date"
                  value={form.targetDate}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      targetDate: e.target.value,
                    })
                  }
                />
              </label>

              <label>
                Target count
                <input
                  type="number"
                  min="1"
                  value={form.targetCount}
                  onChange={(e) =>
                    setForm({
                      ...form,
                      targetCount: e.target.value,
                    })
                  }
                />
              </label>

            </div>

            <label>
              Description
              <textarea
                placeholder="What do you want to accomplish?"
                value={form.description}
                onChange={(e) =>
                  setForm({
                    ...form,
                    description: e.target.value,
                  })
                }
              />
            </label>

            <button className="goal-primary-button" type="submit">
              Create goal
            </button>

          </form>
        </section>
      )}

      {loading ? (
        <div className="goal-empty">
          Loading goals...
        </div>
      ) : goals.length === 0 ? (
        <div className="goal-empty">
          <div className="goal-empty-icon">◎</div>
          <h2>No goals yet</h2>
          <p>
            Create your first goal and start turning it into actionable milestones.
          </p>
          <button
            className="goal-primary-button"
            onClick={() => setShowForm(true)}
          >
            Create your first goal
          </button>
        </div>
      ) : (
        <div className="goals-layout">

          <section className="goals-list">

            <div className="section-heading">
              <div>
                <h2>Your goals</h2>
                <p>{goals.length} active goals</p>
              </div>
            </div>

            <div className="goal-cards">

              {goals.map((goal) => {
                const percentage = getGoalPercentage(goal);

                return (
                  <article
                    key={goal.id}
                    className={`goal-card ${
                      selectedGoal?.id === goal.id
                        ? 'goal-card--selected'
                        : ''
                    }`}
                    onClick={() => selectGoal(goal)}
                  >

                    <div className="goal-card-top">

                      <div>
                        <span className={statusClass(goal.status)}>
                          {goal.status}
                        </span>

                        {goal.category && (
                          <span className="goal-category">
                            {goal.category}
                          </span>
                        )}
                      </div>

                      <button
                        className="goal-delete"
                        onClick={(e) => {
                          e.stopPropagation();
                          deleteGoal(goal.id);
                        }}
                      >
                        ×
                      </button>

                    </div>

                    <h3>{goal.title}</h3>

                    {goal.description && (
                      <p className="goal-description">
                        {goal.description}
                      </p>
                    )}

                    <div className="goal-progress-row">
                      <span>Progress</span>
                      <strong>{percentage}%</strong>
                    </div>

                    <div className="goal-progress-track">
                      <div
                        className="goal-progress-fill"
                        style={{ width: `${percentage}%` }}
                      />
                    </div>

                    <div className="goal-card-footer">
                      <span>
                        {goal.achievedCount} / {goal.targetCount}
                      </span>

                      {goal.targetDate && (
                        <span>
                          Target: {goal.targetDate}
                        </span>
                      )}
                    </div>

                  </article>
                );
              })}

            </div>

          </section>

          {selectedGoal && progress && (
            <aside className="goal-details">

              <div className="goal-details-header">
                <div>
                  <span className={statusClass(selectedGoal.status)}>
                    {selectedGoal.status}
                  </span>

                  <h2>{selectedGoal.title}</h2>
                </div>
              </div>

              <div className="goal-big-progress">
                <div className="goal-big-progress-number">
                  {progress.taskPercentage}%
                </div>

                <div>
                  <strong>Overall task progress</strong>
                  <p>
                    {progress.completedTasks} of{' '}
                    {progress.taskCount} tasks completed
                  </p>
                </div>
              </div>

              <div className="goal-stat-grid">

                <div className="goal-stat">
                  <strong>
                    {progress.completedMilestones}
                  </strong>
                  <span>
                    / {progress.milestoneCount} milestones
                  </span>
                </div>

                <div className="goal-stat">
                  <strong>
                    {progress.completedTasks}
                  </strong>
                  <span>
                    / {progress.taskCount} tasks
                  </span>
                </div>

                <div className="goal-stat">
                  <strong>
                    {progress.habitCount}
                  </strong>
                  <span>habits</span>
                </div>

              </div>

              <div className="goal-detail-section">

                <div className="section-heading">
                  <div>
                    <h3>Milestones</h3>
                    <p>
                      {progress.completedMilestones} completed
                    </p>
                  </div>
                </div>

                {milestones.length === 0 ? (
                  <div className="milestone-empty">
                    No milestones added yet.
                  </div>
                ) : (
                  <div className="milestone-list">

                    {milestones.map((milestone) => (
                      <div
                        key={milestone.id}
                        className={`milestone ${
                          milestone.completedAt
                            ? 'milestone--completed'
                            : ''
                        }`}
                      >

                        <button
                          className="milestone-check"
                          onClick={() =>
                            toggleMilestone(milestone)
                          }
                        >
                          {milestone.completedAt ? '✓' : ''}
                        </button>

                        <div className="milestone-content">
                          <strong>{milestone.title}</strong>

                          {milestone.dueDate && (
                            <span>
                              Due {milestone.dueDate}
                            </span>
                          )}
                        </div>

                      </div>
                    ))}

                  </div>
                )}

              </div>

            </aside>
          )}

        </div>
      )}

    </div>
  );
}