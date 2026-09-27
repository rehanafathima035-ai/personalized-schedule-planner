import { useEffect, useMemo, useState } from 'react';
import { api } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { isoDate, startOfWeek, weekDays } from '../utils/time';
import WeekRibbon from '../components/WeekRibbon';
import {
  cacheData,
  loadCachedData,
  isOffline,
} from '../offline/sync';
import {
  queueAction,
  startOfflineSync,
} from '../offline/actions';

const PRAYER_NAMES = {
  Fajr: 'FAJR',
  Dhuhr: 'DHUHR',
  Asr: 'ASR',
  Maghrib: 'MAGHRIB',
  Isha: 'ISHA',
};

export default function Dashboard({ onCreate }) {
  const { user } = useAuth();

  const [items, setItems] = useState([]);
  const [habits, setHabits] = useState([]);
  const [completions, setCompletions] = useState([]);
  const [prayerCompletions, setPrayerCompletions] = useState([]);
  const [analytics, setAnalytics] = useState(null);

  const [tasks, setTasks] = useState([]);
  const [taskLoading, setTaskLoading] = useState(false);
  const [taskError, setTaskError] = useState(null);
  const [taskActionId, setTaskActionId] = useState(null);

  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  const [deletingId, setDeletingId] = useState(null);
  const [checkingId, setCheckingId] = useState(null);
  const [checkingPrayer, setCheckingPrayer] = useState(null);
  const [view, setView] = useState('week');
  const [selectedDate, setSelectedDate] = useState(new Date());
  const [goals, setGoals] = useState([]);
  const [milestones, setMilestones] = useState([]);

  const [dueReminders, setDueReminders] = useState([]);

  const today = isoDate(new Date());

  const ranges = useMemo(() => {
    const date = new Date(selectedDate);

    if (view === 'month') {
      return {
        from: isoDate(
          new Date(
            date.getFullYear(),
            date.getMonth(),
            1,
          ),
        ),
        to: isoDate(
          new Date(
            date.getFullYear(),
            date.getMonth() + 1,
            0,
          ),
        ),
      };
    }

    if (view === 'year') {
      return {
        from: isoDate(
          new Date(date.getFullYear(), 0, 1),
        ),
        to: isoDate(
          new Date(date.getFullYear(), 11, 31),
        ),
      };
    }

    const start = startOfWeek(date);
    const days = weekDays(start);

    return {
      from: isoDate(days[0]),
      to: isoDate(days[6]),
    };
  }, [selectedDate, view]);

  const week = useMemo(() => {
    const start = startOfWeek(selectedDate);

    return weekDays(start).map((day) => ({
      iso: isoDate(day),
      short: day.toLocaleDateString(undefined, {
        weekday: 'short',
      }),
      date: day.getDate(),
      isToday: isoDate(day) === today,
    }));
  }, [selectedDate, today]);

  useEffect(() => {
    let active = true;

    async function loadDashboard() {
      setLoading(true);
      setError(null);

      if (isOffline()) {
        try {
          const cached = await loadCachedData();

          if (!active) return;

          setItems(cached.schedule);
          setHabits(cached.habits);
          setGoals(cached.goals);
          setTasks(cached.tasks);
          setCompletions(cached.completions);
          setPrayerCompletions(
            cached.prayerCompletions,
          );

          setLoading(false);
        } catch {
          if (active) {
            setError(
              'Unable to load offline data.',
            );
            setLoading(false);
          }
        }

        return;
      }

      try {
        const [
          schedule,
          habitList,
          goalList,
          completionList,
          prayerList,
          analyticsData,
          taskList,
        ] = await Promise.all([
          api.scheduleRange(
            ranges.from,
            ranges.to,
          ),
          api.listHabits(),
          api.listGoals(),
          api.habitCompletions(
            ranges.from,
            ranges.to,
          ),
          api.prayerCompletions(
            ranges.from,
            ranges.to,
          ),
          api.analytics(
            ranges.from,
            ranges.to,
          ),
          api.listTasks(),
        ]);

        if (!active) return;

        setItems(schedule);
        setHabits(habitList);
        setGoals(goalList);
        setCompletions(completionList);
        setPrayerCompletions(prayerList);
        setAnalytics(analyticsData);
        setTasks(taskList);

        await cacheData({
          schedule,
          habits: habitList,
          goals: goalList,
          tasks: taskList,
          completions: completionList,
          prayerCompletions: prayerList,
        });
      } catch (failure) {
        if (!active) return;

        try {
          const cached = await loadCachedData();

          setItems(cached.schedule);
          setHabits(cached.habits);
          setGoals(cached.goals);
          setTasks(cached.tasks);
          setCompletions(cached.completions);
          setPrayerCompletions(
            cached.prayerCompletions,
          );

          setError(
            'Server unavailable. Showing cached data.',
          );
        } catch {
          setError(failure.message);
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    }

    loadDashboard();

    return () => {
      active = false;
    };
  }, [ranges]);

  useEffect(() => {
    let active = true;

    async function checkReminders() {
      const now = new Date();

      const date = isoDate(now);

      const currentMinute =
        now.getHours() * 60 +
        now.getMinutes();

      try {
        const result =
          await api.dueReminders(
            date,
            currentMinute,
          );

        if (active) {
          setDueReminders(result);
        }
      } catch {
        // Reminder polling should not break the dashboard.
      }
    }

    checkReminders();

    const interval = setInterval(
      checkReminders,
      60 * 1000,
    );

    return () => {
      active = false;
      clearInterval(interval);
    };
  }, []);

  useEffect(() => {
    return startOfflineSync();
  }, []);

  async function loadTasks() {
    setTaskLoading(true);
    setTaskError(null);

    try {
      const result = await api.listTasks();
      setTasks(result);
    } catch (failure) {
      setTaskError(failure.message);
    } finally {
      setTaskLoading(false);
    }
  }

  async function completeTask(id) {
    setTaskActionId(id);
    setTaskError(null);

    if (!navigator.onLine) {
      await queueAction(
        'COMPLETE_TASK',
        { id },
      );

      setTasks((current) =>
        current.map((task) =>
          task.id === id
            ? {
                ...task,
                status: 'COMPLETED',
              }
            : task,
        ),
      );

      setTaskActionId(null);
      return;
    }

    try {
      const updated =
        await api.completeTask(id);

      setTasks((current) =>
        current.map((task) =>
          task.id === id ? updated : task,
        ),
      );
    } catch (failure) {
      setTaskError(failure.message);
    } finally {
      setTaskActionId(null);
    }
  }

  async function skipTask(id) {
    setTaskActionId(id);
    setTaskError(null);

    if (!navigator.onLine) {
      await queueAction(
        'SKIP_TASK',
        { id },
      );

      setTasks((current) =>
        current.map((task) =>
          task.id === id
            ? {
                ...task,
                status: 'SKIPPED',
              }
            : task,
        ),
      );

      setTaskActionId(null);
      return;
    }

    try {
      const updated =
        await api.skipTask(id);

      setTasks((current) =>
        current.map((task) =>
          task.id === id ? updated : task,
        ),
      );
    } catch (failure) {
      setTaskError(failure.message);
    } finally {
      setTaskActionId(null);
    }
  }

  async function deleteTask(id) {
    setTaskActionId(id);
    setTaskError(null);

    if (!navigator.onLine) {
      await queueAction(
        'DELETE_TASK',
        { id },
      );

      setTasks((current) =>
        current.filter(
          (task) => task.id !== id,
        ),
      );

      setTaskActionId(null);
      return;
    }

    try {
      await api.deleteTask(id);

      setTasks((current) =>
        current.filter(
          (task) => task.id !== id,
        ),
      );
    } catch (failure) {
      setTaskError(failure.message);
    } finally {
      setTaskActionId(null);
    }
  }

  async function handleDelete(id, sourceType) {
    if (sourceType === 'PRAYER') return;

    setError(null);
    setDeletingId(id);

    try {
      await api.deleteScheduledItem(id);

      setItems((current) =>
        current.filter((item) => item.id !== id),
      );
    } catch (failure) {
      setError(failure.message);
    } finally {
      setDeletingId(null);
    }
  }

  async function toggleHabit(habitId) {
    setError(null);
    setCheckingId(habitId);

    const completed = completions.some(
      (item) =>
        Number(item.habitId) === Number(habitId) &&
        item.occurrenceDate === today &&
        item.status === 'COMPLETED',
    );

    try {
      if (completed) {
        if (!navigator.onLine) {
          await queueAction(
            'UNCOMPLETE_HABIT',
            {
              habitId,
              date: today,
            },
          );
        } else {
          await api.uncompleteHabit(
            habitId,
            today,
          );
        }

        setCompletions((current) =>
          current.filter(
            (item) =>
              !(
                Number(item.habitId) ===
                  Number(habitId) &&
                item.occurrenceDate === today
              ),
          ),
        );
      } else {
        let result;

        if (!navigator.onLine) {
          await queueAction(
            'COMPLETE_HABIT',
            {
              habitId,
              date: today,
            },
          );

          result = {
            id: `offline-habit-${habitId}-${today}`,
            habitId,
            occurrenceDate: today,
            status: 'COMPLETED',
          };
        } else {
          result =
            await api.completeHabit(
              habitId,
              today,
            );
        }

        setCompletions((current) => [
          ...current.filter(
            (item) =>
              !(
                Number(item.habitId) ===
                  Number(habitId) &&
                item.occurrenceDate === today
              ),
          ),
          result,
        ]);
      }
    } catch (failure) {
      setError(failure.message);
    } finally {
      setCheckingId(null);
    }
  }

  async function togglePrayer(prayerName) {
    setError(null);
    setCheckingPrayer(prayerName);

    const completed = prayerCompletions.some(
      (item) =>
        item.prayerName === prayerName &&
        item.prayerDate === today &&
        item.status === 'COMPLETED',
    );

    try {
      if (completed) {
        if (!navigator.onLine) {
          await queueAction(
            'UNCOMPLETE_PRAYER',
            {
              prayerName,
              date: today,
            },
          );
        } else {
          await api.uncompletePrayer(
            prayerName,
            today,
          );
        }

        setPrayerCompletions((current) =>
          current.filter(
            (item) =>
              !(
                item.prayerName ===
                  prayerName &&
                item.prayerDate === today
              ),
          ),
        );
      } else {
        let result;

        if (!navigator.onLine) {
          await queueAction(
            'COMPLETE_PRAYER',
            {
              prayerName,
              date: today,
            },
          );

          result = {
            id: `offline-prayer-${prayerName}-${today}`,
            prayerName,
            prayerDate: today,
            status: 'COMPLETED',
          };
        } else {
          result =
            await api.completePrayer(
              prayerName,
              today,
            );
        }

        setPrayerCompletions((current) => [
          ...current.filter(
            (item) =>
              !(
                item.prayerName ===
                  prayerName &&
                item.prayerDate === today
              ),
          ),
          result,
        ]);
      }
    } catch (failure) {
      setError(failure.message);
    } finally {
      setCheckingPrayer(null);
    }
  }

  function movePeriod(direction) {
    const next = new Date(selectedDate);

    if (view === 'week') {
      next.setDate(
        next.getDate() + direction * 7,
      );
    } else if (view === 'month') {
      next.setMonth(
        next.getMonth() + direction,
      );
    } else {
      next.setFullYear(
        next.getFullYear() + direction,
      );
    }

    setSelectedDate(next);
  }

  function goToday() {
    setSelectedDate(new Date());
  }

  const todayHabitIds = new Set(
    items
      .filter(
        (item) =>
          item.scheduledDate === today &&
          item.sourceType === 'HABIT',
      )
      .map((item) => Number(item.sourceId)),
  );

  const todayHabits = habits.filter(
    (habit) =>
      habit.status === 'ACTIVE' &&
      todayHabitIds.has(Number(habit.id)),
  );

  const todayPrayers = items
    .filter(
      (item) =>
        item.scheduledDate === today &&
        item.sourceType === 'PRAYER',
    )
    .sort(
      (a, b) =>
        a.startMinute - b.startMinute,
    );

  const completedPrayers = todayPrayers.filter(
    (prayer) => {
      const prayerName =
        PRAYER_NAMES[prayer.displayName];

      return prayerCompletions.some(
        (completion) =>
          completion.prayerName === prayerName &&
          completion.prayerDate === today &&
          completion.status === 'COMPLETED',
      );
    },
  ).length;

  const todayCompleted = todayHabits.filter(
    (habit) =>
      completions.some(
        (completion) =>
          Number(completion.habitId) ===
            Number(habit.id) &&
          completion.occurrenceDate === today &&
          completion.status === 'COMPLETED',
      ),
  ).length;

  const todayTotal =
    todayHabits.length + todayPrayers.length;

  const todayDone =
    todayCompleted + completedPrayers;

  const todayProgress =
    todayTotal === 0
      ? 0
      : Math.round(
          (todayDone / todayTotal) * 100,
        );

  const weekCompleted = completions.filter(
    (completion) =>
      completion.status === 'COMPLETED' &&
      completion.occurrenceDate >= ranges.from &&
      completion.occurrenceDate <= ranges.to,
  ).length;

  const weekPrayerCompleted =
    prayerCompletions.filter(
      (completion) =>
        completion.status === 'COMPLETED' &&
        completion.prayerDate >= ranges.from &&
        completion.prayerDate <= ranges.to,
    ).length;

  const weekExpectedHabits = items.filter(
    (item) =>
      item.sourceType === 'HABIT' &&
      item.scheduledDate >= ranges.from &&
      item.scheduledDate <= ranges.to,
  ).length;

  const weekExpectedPrayers = items.filter(
    (item) =>
      item.sourceType === 'PRAYER' &&
      item.scheduledDate >= ranges.from &&
      item.scheduledDate <= ranges.to,
  ).length;

  const weekExpected =
    weekExpectedHabits +
    weekExpectedPrayers;

  const weekDone =
    weekCompleted +
    weekPrayerCompleted;

  const weekProgress =
    weekExpected === 0
      ? 0
      : Math.min(
          100,
          Math.round(
            (weekDone / weekExpected) * 100,
          ),
        );

  const hours =
    Math.round(
      items.reduce(
        (total, item) =>
          total +
          item.endMinute -
          item.startMinute,
        0,
      ) / 6,
    ) / 10;

  const completedTaskCount = tasks.filter(
    (task) => task.status === 'COMPLETED',
  ).length;

  return (
    <div className="dashboard">
      <header className="dashboard__head">
        <p className="dashboard__greeting">
          {greeting()}, {user?.displayName}
        </p>

        <h1 className="page-title">
          Your life, in one place
        </h1>
      </header>

      <section className="progress-panel">
        <div className="progress-card">
          <span>Today</span>

          <strong>{todayProgress}%</strong>

          <p>
            {todayDone} of {todayTotal} completed
          </p>

          <div className="progress-bar">
            <div
              style={{
                width: `${todayProgress}%`,
              }}
            />
          </div>
        </div>

        <div className="progress-card">
          <span>This week</span>

          <strong>{weekProgress}%</strong>

          <p>{weekDone} completed sessions</p>

          <div className="progress-bar">
            <div
              style={{
                width: `${weekProgress}%`,
              }}
            />
          </div>
        </div>
      </section>

      {analytics && (
        <section className="analytics-panel">
          <div className="section-heading">
            <div>
              <span className="eyebrow">
                Progress analytics
              </span>

              <h2>How you're doing</h2>
            </div>
          </div>

          <div className="analytics-grid">
            <div className="analytics-card">
              <span>Total completion</span>

              <strong>
                {analytics.completionPercentage}%
              </strong>

              <p>
                {analytics.totalCompleted} of{' '}
                {analytics.totalItems}
              </p>
            </div>

            <div className="analytics-card">
              <span>Habits</span>

              <strong>
                {analytics.habitCompleted}/
                {analytics.habitTotal}
              </strong>

              <p>completed</p>
            </div>

            <div className="analytics-card">
              <span>Prayers</span>

              <strong>
                {analytics.prayerCompleted}/
                {analytics.prayerTotal}
              </strong>

              <p>completed</p>
            </div>
          </div>
        </section>
      )}

      <section className="checklist">
        <div className="section-heading">
          <div>
            <span className="eyebrow">
              Daily checklist
            </span>

            <h2>For today</h2>
          </div>

          <span className="checklist-count">
            {todayDone}/{todayTotal}
          </span>
        </div>

        {todayTotal === 0 ? (
          <p className="muted">
            Nothing scheduled for today.
          </p>
        ) : (
          <div className="checklist-list">
            {todayPrayers.map((prayer) => {
              const prayerName =
                PRAYER_NAMES[
                  prayer.displayName
                ];

              const completed =
                prayerCompletions.some(
                  (item) =>
                    item.prayerName ===
                      prayerName &&
                    item.prayerDate === today &&
                    item.status ===
                      'COMPLETED',
                );

              return (
                <button
                  type="button"
                  className={`checklist-item ${
                    completed
                      ? 'is-complete'
                      : ''
                  }`}
                  key={prayer.id}
                  onClick={() =>
                    togglePrayer(prayerName)
                  }
                  disabled={
                    checkingPrayer ===
                    prayerName
                  }
                >
                  <span className="check-circle">
                    {completed ? '✓' : ''}
                  </span>

                  <span className="checklist-item__text">
                    <strong>
                      {prayer.displayName}
                    </strong>

                    <small>
                      {formatTime(
                        prayer.startMinute,
                      )}{' '}
                      · Prayer
                    </small>
                  </span>
                </button>
              );
            })}

            {todayHabits.map((habit) => {
              const completed =
                completions.some(
                  (completion) =>
                    Number(
                      completion.habitId,
                    ) === Number(habit.id) &&
                    completion.occurrenceDate ===
                      today &&
                    completion.status ===
                      'COMPLETED',
                );

              return (
                <button
                  type="button"
                  className={`checklist-item ${
                    completed
                      ? 'is-complete'
                      : ''
                  }`}
                  key={habit.id}
                  onClick={() =>
                    toggleHabit(habit.id)
                  }
                  disabled={
                    checkingId === habit.id
                  }
                >
                  <span className="check-circle">
                    {completed ? '✓' : ''}
                  </span>

                  <span className="checklist-item__text">
                    <strong>{habit.name}</strong>

                    <small>
                      {habit.durationMinutes}{' '}
                      minutes · {habit.priority}
                    </small>
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </section>

      <section className="checklist">
        <div className="section-heading">
          <div>
            <span className="eyebrow">
              Tasks
            </span>

            <h2>Your tasks</h2>
          </div>

          <span className="checklist-count">
            {completedTaskCount}/{tasks.length}
          </span>
        </div>

        {taskLoading ? (
          <p className="muted">
            Loading tasks…
          </p>
        ) : taskError ? (
          <p className="error" role="alert">
            {taskError}
          </p>
        ) : tasks.length === 0 ? (
          <p className="muted">
            No tasks yet.
          </p>
        ) : (
          <div className="checklist-list">
            {tasks.map((task) => (
              <div
                className={`checklist-item ${
                  task.status ===
                  'COMPLETED'
                    ? 'is-complete'
                    : ''
                }`}
                key={task.id}
              >
                <span className="check-circle">
                  {task.status ===
                  'COMPLETED'
                    ? '✓'
                    : ''}
                </span>

                <span className="checklist-item__text">
                  <strong>
                    {task.title}
                  </strong>

                  <small>
                    {task.priority} ·{' '}
                    {task.durationMinutes}{' '}
                    minutes
                    {task.dueDate
                      ? ` · Due ${task.dueDate}`
                      : ''}
                  </small>
                </span>

                <div>
                  {task.status ===
                    'PENDING' && (
                    <>
                      <button
                        type="button"
                        className="button button--secondary"
                        onClick={() =>
                          completeTask(
                            task.id,
                          )
                        }
                        disabled={
                          taskActionId ===
                          task.id
                        }
                      >
                        {taskActionId ===
                        task.id
                          ? 'Working…'
                          : 'Complete'}
                      </button>

                      <button
                        type="button"
                        className="button button--secondary"
                        onClick={() =>
                          skipTask(
                            task.id,
                          )
                        }
                        disabled={
                          taskActionId ===
                          task.id
                        }
                      >
                        Skip
                      </button>
                    </>
                  )}

                  <button
                    type="button"
                    className="button button--secondary"
                    onClick={() =>
                      deleteTask(task.id)
                    }
                    disabled={
                      taskActionId ===
                      task.id
                    }
                  >
                    Delete
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>

      {dueReminders.length > 0 && (
        <section className="checklist">
          <div className="section-heading">
            <div>
              <span className="eyebrow">
                Reminders
              </span>

              <h2>Due now</h2>
            </div>

            <span className="checklist-count">
              {dueReminders.length}
            </span>
          </div>

          <div className="checklist-list">
            {dueReminders.map((reminder) => (
              <div
                className="checklist-item"
                key={reminder.reminderId}
              >
                <span className="check-circle">
                  !
                </span>

                <span className="checklist-item__text">
                  <strong>
                    {reminder.sourceType}
                  </strong>

                  <small>
                    Reminder due now
                  </small>
                </span>
              </div>
            ))}
          </div>
        </section>
      )}

      <div className="calendar-controls">
        <div className="calendar-tabs">
          <button
            type="button"
            className={
              view === 'week'
                ? 'is-active'
                : ''
            }
            onClick={() =>
              setView('week')
            }
          >
            Week
          </button>

          <button
            type="button"
            className={
              view === 'month'
                ? 'is-active'
                : ''
            }
            onClick={() =>
              setView('month')
            }
          >
            Month
          </button>

          <button
            type="button"
            className={
              view === 'year'
                ? 'is-active'
                : ''
            }
            onClick={() =>
              setView('year')
            }
          >
            Year
          </button>
        </div>

        <div className="calendar-navigation">
          <button
            type="button"
            onClick={() =>
              movePeriod(-1)
            }
          >
            Previous
          </button>

          <button
            type="button"
            onClick={goToday}
          >
            Today
          </button>

          <button
            type="button"
            onClick={() =>
              movePeriod(1)
            }
          >
            Next
          </button>
        </div>
      </div>

      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}

      {loading ? (
        <p className="muted">
          Loading your schedule…
        </p>
      ) : (
        <>
          <p className="dashboard__summary">
            {items.length} sessions, {hours}{' '}
            hours.
          </p>

          {view === 'week' && (
            <WeekRibbon
              days={week}
              items={items}
            />
          )}

          {view === 'month' && (
            <MonthView
              items={items}
              selectedDate={selectedDate}
            />
          )}

          {view === 'year' && (
            <YearView
              items={items}
              selectedDate={selectedDate}
            />
          )}

          {items.length > 0 && (
            <div className="schedule-list">
              {items.map((item) => (
                <div
                  className="schedule-item"
                  key={item.id}
                >
                  <div>
                    <strong>
                      {item.displayName}
                    </strong>

                    <p>
                      {item.scheduledDate}{' '}
                      ·{' '}
                      {formatTime(
                        item.startMinute,
                      )}
                      –
                      {formatTime(
                        item.endMinute,
                      )}
                    </p>
                  </div>

                  {item.sourceType !==
                    'PRAYER' && (
                    <button
                      type="button"
                      className="button button--secondary"
                      onClick={() =>
                        handleDelete(
                          item.id,
                          item.sourceType,
                        )
                      }
                      disabled={
                        deletingId ===
                        item.id
                      }
                    >
                      {deletingId ===
                      item.id
                        ? 'Removing…'
                        : 'Delete'}
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}

          <button
            type="button"
            className="button button--primary"
            onClick={onCreate}
          >
            Make room for something else
          </button>
        </>
      )}
    </div>
  );
}

function MonthView({
  items,
  selectedDate,
}) {
  const year =
    selectedDate.getFullYear();

  const month =
    selectedDate.getMonth();

  const first = new Date(
    year,
    month,
    1,
  );

  const last = new Date(
    year,
    month + 1,
    0,
  );

  const startDay =
    (first.getDay() + 6) % 7;

  const totalDays =
    last.getDate();

  const cells = [];

  for (
    let i = 0;
    i < startDay;
    i += 1
  ) {
    cells.push(null);
  }

  for (
    let day = 1;
    day <= totalDays;
    day += 1
  ) {
    const date = new Date(
      year,
      month,
      day,
    );

    cells.push({
      date,
      iso: isoDate(date),
      items: items.filter(
        (item) =>
          item.scheduledDate ===
          isoDate(date),
      ),
    });
  }

  return (
    <section className="calendar-month">
      <h2>
        {first.toLocaleDateString(
          undefined,
          {
            month: 'long',
            year: 'numeric',
          },
        )}
      </h2>

      <div className="calendar-grid calendar-grid--header">
        {[
          'Mon',
          'Tue',
          'Wed',
          'Thu',
          'Fri',
          'Sat',
          'Sun',
        ].map((day) => (
          <div key={day}>
            {day}
          </div>
        ))}
      </div>

      <div className="calendar-grid">
        {cells.map(
          (cell, index) =>
            cell ? (
              <div
                className="calendar-cell"
                key={cell.iso}
              >
                <strong>
                  {cell.date.getDate()}
                </strong>

                {cell.items.map(
                  (item) => (
                    <div
                      className="calendar-event"
                      key={item.id}
                    >
                      {
                        item.displayName
                      }
                    </div>
                  ),
                )}
              </div>
            ) : (
              <div
                className="calendar-cell calendar-cell--empty"
                key={`empty-${index}`}
              />
            ),
        )}
      </div>
    </section>
  );
}

function YearView({
  items,
  selectedDate,
}) {
  const year =
    selectedDate.getFullYear();

  return (
    <section className="calendar-year">
      <h2>{year}</h2>

      <div className="year-grid">
        {Array.from(
          { length: 12 },
          (_, monthIndex) => {
            const monthItems =
              items.filter(
                (item) => {
                  const date =
                    new Date(
                      `${item.scheduledDate}T00:00:00`,
                    );

                  return (
                    date.getMonth() ===
                    monthIndex
                  );
                },
              );

            return (
              <div
                className="year-month"
                key={monthIndex}
              >
                <h3>
                  {new Date(
                    year,
                    monthIndex,
                    1,
                  ).toLocaleDateString(
                    undefined,
                    {
                      month:
                        'long',
                    },
                  )}
                </h3>

                <p>
                  {
                    monthItems.length
                  }{' '}
                  sessions
                </p>

                {monthItems
                  .slice(0, 5)
                  .map((item) => (
                    <div
                      className="calendar-event"
                      key={item.id}
                    >
                      {item.scheduledDate.slice(
                        8,
                      )}{' '}
                      ·{' '}
                      {
                        item.displayName
                      }
                    </div>
                  ))}

                {monthItems.length >
                  5 && (
                  <p className="muted">
                    +
                    {monthItems.length -
                      5}{' '}
                    more
                  </p>
                )}
              </div>
            );
          },
        )}
      </div>
    </section>
  );
}

function formatTime(minutes) {
  const hour =
    Math.floor(minutes / 60);

  const minute =
    minutes % 60;

  const suffix =
    hour >= 12 ? 'PM' : 'AM';

  const displayHour =
    hour % 12 || 12;

  return `${displayHour}:${String(
    minute,
  ).padStart(2, '0')} ${suffix}`;
}

function greeting() {
  const hour =
    new Date().getHours();

  if (hour < 12)
    return 'Good morning';

  if (hour < 17)
    return 'Good afternoon';

  return 'Good evening';
}