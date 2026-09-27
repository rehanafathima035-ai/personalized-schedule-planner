import { formatRange } from '../utils/time';

/**
 * The week as a single ribbon.
 *
 * This is the product's central claim made visible: not nine separate
 * trackers, one coordinated week. Proposed slots are drawn as outlines and
 * approved slots as fills, so "this is not yours yet" is carried by the
 * drawing itself rather than by a label the eye skips.
 */
export default function WeekRibbon({ days, items, proposed = false, onSelectDay }) {
  const byDay = new Map(days.map((day) => [day.iso, []]));
  items.forEach((item) => {
    const bucket = byDay.get(item.day ?? item.scheduledDate);
    if (bucket) bucket.push(item);
  });

  const heaviest = Math.max(
    1,
    ...[...byDay.values()].map((bucket) =>
      bucket.reduce((total, item) => total + (item.end_minute ?? item.endMinute) -
        (item.start_minute ?? item.startMinute), 0),
    ),
  );

  return (
    <ol className={`ribbon ${proposed ? 'ribbon--proposed' : ''}`}>
      {days.map((day) => {
        const bucket = byDay.get(day.iso) ?? [];
        const minutes = bucket.reduce(
          (total, item) =>
            total + (item.end_minute ?? item.endMinute) - (item.start_minute ?? item.startMinute),
          0,
        );
        return (
          <li
            key={day.iso}
            className={`ribbon__day ${day.isToday ? 'is-today' : ''}`}
            onClick={onSelectDay ? () => onSelectDay(day) : undefined}
          >
            <p className="ribbon__label">
              <span className="ribbon__weekday">{day.short}</span>
              <span className="ribbon__date">{day.date}</span>
            </p>

            <div
              className="ribbon__gauge"
              role="img"
              aria-label={`${Math.round(minutes / 60 * 10) / 10} hours scheduled`}
            >
              <span style={{ height: `${Math.round((minutes / heaviest) * 100)}%` }} />
            </div>

            <ul className="ribbon__items">
              {bucket.length === 0 && <li className="ribbon__empty">Open</li>}
              {bucket.map((item, index) => (
                <li key={index} className="slot">
                  <span className="slot__name">
                    {item.activity_name ?? item.displayName}
                  </span>
                  <span className="slot__time">
                    {formatRange(
                      item.start_minute ?? item.startMinute,
                      item.end_minute ?? item.endMinute,
                    )}
                  </span>
                </li>
              ))}
            </ul>
          </li>
        );
      })}
    </ol>
  );
}
