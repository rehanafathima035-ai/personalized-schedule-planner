const DB_NAME = 'life-planner-offline';
const DB_VERSION = 4;

const STORES = [
  'tasks',
  'habits',
  'goals',
  'schedule',
  'prayers',
  'pendingActions',
  'completions',
  'prayerCompletions',
];

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(
      DB_NAME,
      DB_VERSION,
    );

    request.onupgradeneeded = () => {
      const db = request.result;

      STORES.forEach((store) => {
        if (!db.objectStoreNames.contains(store)) {
          db.createObjectStore(store, {
            keyPath: 'id',
          });
        }
      });
    };

    request.onsuccess = () => {
      resolve(request.result);
    };

    request.onerror = () => {
      reject(request.error);
    };
  });
}

export async function saveItems(
  storeName,
  items,
) {
  const db = await openDatabase();

  return new Promise((resolve, reject) => {
    const transaction =
      db.transaction(
        storeName,
        'readwrite',
      );

    const store =
      transaction.objectStore(
        storeName,
      );

    store.clear();

    items.forEach((item) => {
      store.put(item);
    });

    transaction.oncomplete = () => {
      resolve();
    };

    transaction.onerror = () => {
      reject(transaction.error);
    };
  });
}

export async function getItems(
  storeName,
) {
  const db = await openDatabase();

  return new Promise((resolve, reject) => {
    const transaction =
      db.transaction(
        storeName,
        'readonly',
      );

    const request =
      transaction
        .objectStore(storeName)
        .getAll();

    request.onsuccess = () => {
      resolve(request.result);
    };

    request.onerror = () => {
      reject(request.error);
    };
  });
}
export async function updateItem(
  storeName,
  item,
) {
  const db = await openDatabase();

  return new Promise((resolve, reject) => {
    const transaction =
      db.transaction(
        storeName,
        'readwrite',
      );

    transaction
      .objectStore(storeName)
      .put(item);

    transaction.oncomplete = () => {
      resolve();
    };

    transaction.onerror = () => {
      reject(transaction.error);
    };
  });
}
export async function clearStore(
  storeName,
) {
  const db = await openDatabase();

  return new Promise((resolve, reject) => {
    const transaction =
      db.transaction(
        storeName,
        'readwrite',
      );

    transaction
      .objectStore(storeName)
      .clear();

    transaction.oncomplete = () => {
      resolve();
    };

    transaction.onerror = () => {
      reject(transaction.error);
    };
  });
}

export async function addPendingAction(action) {
  const db = await openDatabase();

  return new Promise((resolve, reject) => {
    const transaction =
      db.transaction(
        'pendingActions',
        'readwrite',
      );

    const store =
      transaction.objectStore(
        'pendingActions',
      );

    store.put({
      id: crypto.randomUUID(),
      ...action,
      createdAt: Date.now(),
    });

    transaction.oncomplete = () => {
      resolve();
    };

    transaction.onerror = () => {
      reject(transaction.error);
    };
  });
}

export async function getPendingActions() {
  return getItems('pendingActions');
}

export async function removePendingAction(id) {
  const db = await openDatabase();

  return new Promise((resolve, reject) => {
    const transaction =
      db.transaction(
        'pendingActions',
        'readwrite',
      );

    transaction
      .objectStore('pendingActions')
      .delete(id);

    transaction.oncomplete = () => {
      resolve();
    };

    transaction.onerror = () => {
      reject(transaction.error);
    };
  });
}