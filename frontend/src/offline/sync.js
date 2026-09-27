import {
  getItems,
  saveItems,
} from './db';

export async function cacheData(data) {
  const entries = [
    ['tasks', data.tasks],
    ['habits', data.habits],
    ['goals', data.goals],
    ['schedule', data.schedule],
    ['prayers', data.prayers],
    ['completions', data.completions],
    ['prayerCompletions', data.prayerCompletions],
  ];

  for (const [store, items] of entries) {
    if (Array.isArray(items)) {
      await saveItems(store, items);
    }
  }
}

export async function loadCachedData() {
  const [
    tasks,
    habits,
    goals,
    schedule,
    prayers,
    completions,
    prayerCompletions,
  ] = await Promise.all([
    getItems('tasks'),
    getItems('habits'),
    getItems('goals'),
    getItems('schedule'),
    getItems('prayers'),
    getItems('completions'),
    getItems('prayerCompletions'),
  ]);

  return {
    tasks,
    habits,
    goals,
    schedule,
    prayers,
    completions,
    prayerCompletions,
  };
}

export function isOffline() {
  return !navigator.onLine;
}