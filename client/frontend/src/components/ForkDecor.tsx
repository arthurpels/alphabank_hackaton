import './ForkDecor.css';

export function ForkDecor() {
  return (
    <div className="fork-decor" aria-hidden="true">
      <div className="fork-decor__labels">
        <span className="fork-decor__label" style={{ left: '20%' }}>17%</span>
        <span className="fork-decor__label" style={{ left: '82%' }}>26%</span>
      </div>
      <div className="fork-decor__track">
        <i className="fork-decor__zone fork-decor__zone--good" />
        <i className="fork-decor__zone fork-decor__zone--mid" />
        <i className="fork-decor__zone fork-decor__zone--bad" />
      </div>
    </div>
  );
}