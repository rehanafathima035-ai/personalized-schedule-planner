/** Minute-of-day helpers, mirroring the engine's representation. */

export function formatMinute(minute) {
  const total = ((minute % 1440) + 1440) % 1440;
  const hour24 = Math.floor(total / 60);
  const minutes = total % 60;
  const suffix = hour24 < 12 ? 'AM' : 'PM';
  const hour12 = hour24 % 12 === 0 ? 12 : hour24 % 12;
  return `${hour12}:${String(minutes).padStart(2, '0')} ${suffix}`;
}

export function formatRange(start, end) {
  return `${formatMinute(start)} – ${formatMinute(end)}`;
}

export function isoDate(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function startOfWeek(date = new Date()) {
  const result = new Date(date);
  const offset = (result.getDay() + 6) % 7; // Monday-first
  result.setDate(result.getDate() - offset);
  result.setHours(0, 0, 0, 0);
  return result;
}

export function weekDays(from = startOfWeek()) {
  return Array.from({ length: 7 }, (_, index) => {
    const day = new Date(from);
    day.setDate(from.getDate() + index);
    return day;
  });
}
