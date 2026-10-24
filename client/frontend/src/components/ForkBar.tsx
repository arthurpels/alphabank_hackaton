import './ForkBar.css';

type Props = {
  from: number;
  to: number;
  scaleMin: number;
  scaleMax: number;
  approveMax: number;
  rejectMin: number;
  marks?: { value: number; label: string }[];
};

const fmt = (v: number) => String(v).replace('.', ',');

export function ForkBar({ from, to, scaleMin, scaleMax, approveMax, rejectMin, marks }: Props) {
  const pct = (v: number) => ((v - scaleMin) / (scaleMax - scaleMin)) * 100;

  const exact = from === to;

  const ticks = [scaleMin, approveMax, rejectMin, scaleMax];

  return (
    <div className="fork">
      <div className="fork__row fork__row--top">
        <span className="fork__edge" style={{ left: `${pct(from)}%` }}>{fmt(from)}%</span>
        {!exact && (
          <span className="fork__edge" style={{ left: `${pct(to)}%` }}>{fmt(to)}%</span>
        )}
      </div>

      <div className="fork__bar">
        {!exact && (
          <div
            className="fork__range"
            style={{ left: `${pct(from)}%`, width: `${pct(to) - pct(from)}%` }}
          />
        )}
        <div className="fork__track">
          <div className="fork__zone fork__zone--good"
               style={{ left: 0, width: `${pct(approveMax)}%` }} />
          <div className="fork__zone fork__zone--mid"
               style={{ left: `${pct(approveMax)}%`, width: `${pct(rejectMin) - pct(approveMax)}%` }} />
          <div className="fork__zone fork__zone--bad"
               style={{ left: `${pct(rejectMin)}%`, width: `${100 - pct(rejectMin)}%` }} />
        </div>
      </div>

      {marks && marks.length > 0 && (
        <div className="fork__marks">
          {marks.map((m, i) => (
            <span key={i} className="fork__mark" style={{ left: `${pct(m.value)}%` }} title={m.label} />
          ))}
        </div>
      )}

      <div className="fork__row">
        {ticks.map((t, i) => (
          <span
            key={t}
            className={`fork__tick ${i === 0 ? 'fork__tick--first' : ''} ${i === ticks.length - 1 ? 'fork__tick--last' : ''}`}
            style={{ left: `${pct(t)}%` }}
          >
            {fmt(t)}%
          </span>
        ))}
      </div>

      <div className="fork__legend">
        <span className="fork__legend-item fork__legend-item--good">одобрение</span>
        <span className="fork__legend-item fork__legend-item--mid">андеррайтер</span>
        <span className="fork__legend-item fork__legend-item--bad">отказ</span>
        {marks && marks.length > 0 && <span className="fork__legend-item fork__legend-item--mark">варианты условий</span>}
      </div>
    </div>
  );
}