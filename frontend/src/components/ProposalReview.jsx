import WeekRibbon from './WeekRibbon';

/**
 * The approval gate.
 *
 * Three things must be on screen before a person can reasonably say yes:
 * what changes, what the system noticed, and why it chose this. A conflict
 * is shown with its severity, never flattened into a generic warning, and
 * a soft conflict is phrased as something to consider rather than a verdict.
 */
export default function ProposalReview({
  proposal,
  alternatives = [],
  days,
  busy,
  onApprove,
  onReject,
  onChooseAlternative,
}) {
  const hard = proposal.conflicts.filter((conflict) => conflict.severity === 'HARD');
  const soft = proposal.conflicts.filter((conflict) => conflict.severity === 'SOFT');

  return (
    <section className="review">
      <header className="review__header">
        <h2>Here is where this fits</h2>
        <p className="review__status">
          Nothing is saved yet. This becomes part of your week only when you approve it.
        </p>
      </header>

      <WeekRibbon days={days} items={proposal.items} proposed />

      {hard.length > 0 && (
        <div className="notice notice--hard">
          <h3>These cannot both happen</h3>
          <ul>
            {hard.map((conflict, index) => (
              <li key={index}>{conflict.message}</li>
            ))}
          </ul>
        </div>
      )}

      {soft.length > 0 && (
        <div className="notice notice--soft">
          <h3>Worth a look</h3>
          <ul>
            {soft.map((conflict, index) => (
              <li key={index}>{conflict.message}</li>
            ))}
          </ul>
        </div>
      )}

      {proposal.explanations.length > 0 && (
        <div className="reasoning">
          <h3>Why these times</h3>
          <ul>
            {proposal.explanations.map((line, index) => (
              <li key={index} className={line.startsWith('  ') ? 'reasoning__detail' : ''}>
                {line.trim().replace(/^•\s*/, '')}
              </li>
            ))}
          </ul>
        </div>
      )}

      {alternatives.length > 0 && (
        <div className="alternatives">
          <h3>Other arrangements</h3>
          {alternatives.map((alternative) => (
            <button
              key={alternative.strategy}
              type="button"
              className="alternatives__option"
              onClick={() => onChooseAlternative(alternative)}
            >
              <span>{describeStrategy(alternative.strategy)}</span>
              <span className="alternatives__days">
                {summarise(alternative.items)}
              </span>
            </button>
          ))}
        </div>
      )}

      <div className="review__actions">
        <button type="button" className="button button--primary"
                onClick={onApprove} disabled={busy}>
          {busy ? 'Adding…' : 'Add to my week'}
        </button>
        <button type="button" className="button button--quiet"
                onClick={onReject} disabled={busy}>
          Discard
        </button>
      </div>
    </section>
  );
}

function describeStrategy(strategy) {
  switch (strategy) {
    case 'SPREAD':
      return 'Spread evenly across the week';
    case 'EARLY_WEEK':
      return 'Front-load the earlier days';
    case 'WEEKEND_FIRST':
      return 'Lean on the weekend';
    default:
      return strategy;
  }
}

function summarise(items) {
  const days = [...new Set(items.map((item) => item.weekday?.slice(0, 3)))];
  return days.join(', ');
}
