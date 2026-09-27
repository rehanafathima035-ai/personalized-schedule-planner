import {
  addPendingAction,
  getPendingActions,
  removePendingAction,
  getItems,
  updateItem,
} from './db';

import { api } from '../services/api';

export async function queueAction(
  type,
  payload,
) {
  await addPendingAction({
    type,
    payload,
  });

  await updateOfflineCache(
    type,
    payload,
  );
}

async function updateOfflineCache(
  type,
  payload,
) {
  switch (type) {
    case 'COMPLETE_TASK':
    case 'SKIP_TASK': {
      const tasks =
        await getItems('tasks');

      const updatedTasks =
        tasks.map((task) =>
          task.id === payload.id
            ? {
                ...task,
                status:
                  type === 'COMPLETE_TASK'
                    ? 'COMPLETED'
                    : 'SKIPPED',
              }
            : task,
        );

      await Promise.all(
        updatedTasks.map((task) =>
          updateItem('tasks', task),
        ),
      );

      break;
    }

    case 'DELETE_TASK': {
      const tasks =
        await getItems('tasks');

      const remainingTasks =
        tasks.filter(
          (task) =>
            task.id !== payload.id,
        );

      await Promise.all(
        remainingTasks.map((task) =>
          updateItem('tasks', task),
        ),
      );

      break;
    }

    case 'COMPLETE_HABIT':
    case 'UNCOMPLETE_HABIT': {
      const completions =
        await getItems('completions');

      const id =
        `offline-habit-${payload.habitId}-${payload.date}`;

      const existing =
        completions.find(
          (item) => item.id === id,
        );

      const completion = {
        id,
        habitId: payload.habitId,
        occurrenceDate:
          payload.date,
        status:
          type === 'COMPLETE_HABIT'
            ? 'COMPLETED'
            : 'PENDING',
      };

      if (existing) {
        await updateItem(
          'completions',
          completion,
        );
      } else if (
        type === 'COMPLETE_HABIT'
      ) {
        await updateItem(
          'completions',
          completion,
        );
      }

      break;
    }

    case 'COMPLETE_PRAYER':
    case 'UNCOMPLETE_PRAYER': {
      const completions =
        await getItems(
          'prayerCompletions',
        );

      const id =
        `offline-prayer-${payload.prayerName}-${payload.date}`;

      const existing =
        completions.find(
          (item) => item.id === id,
        );

      const completion = {
        id,
        prayerName:
          payload.prayerName,
        prayerDate:
          payload.date,
        status:
          type === 'COMPLETE_PRAYER'
            ? 'COMPLETED'
            : 'PENDING',
      };

      if (existing) {
        await updateItem(
          'prayerCompletions',
          completion,
        );
      } else if (
        type === 'COMPLETE_PRAYER'
      ) {
        await updateItem(
          'prayerCompletions',
          completion,
        );
      }

      break;
    }

    default:
      break;
  }
}

async function executeAction(action) {
  switch (action.type) {
    case 'COMPLETE_TASK':
      return api.completeTask(
        action.payload.id,
      );

    case 'SKIP_TASK':
      return api.skipTask(
        action.payload.id,
      );

    case 'DELETE_TASK':
      return api.deleteTask(
        action.payload.id,
      );

    case 'COMPLETE_HABIT':
      return api.completeHabit(
        action.payload.habitId,
        action.payload.date,
      );

    case 'UNCOMPLETE_HABIT':
      return api.uncompleteHabit(
        action.payload.habitId,
        action.payload.date,
      );

    case 'COMPLETE_PRAYER':
      return api.completePrayer(
        action.payload.prayerName,
        action.payload.date,
      );

    case 'UNCOMPLETE_PRAYER':
      return api.uncompletePrayer(
        action.payload.prayerName,
        action.payload.date,
      );

    default:
      throw new Error(
        `Unknown offline action: ${action.type}`,
      );
  }
}

export async function syncPendingActions() {
  if (!navigator.onLine) {
    return;
  }

  const actions =
    await getPendingActions();

  for (const action of actions) {
    try {
      await executeAction(action);
      await removePendingAction(
        action.id,
      );
    } catch {
      break;
    }
  }
}

export function startOfflineSync() {
  window.addEventListener(
    'online',
    syncPendingActions,
  );

  if (navigator.onLine) {
    syncPendingActions();
  }

  return () => {
    window.removeEventListener(
      'online',
      syncPendingActions,
    );
  };
}