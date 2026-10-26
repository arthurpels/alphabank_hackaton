import { Link, useLocation } from 'react-router-dom';
import { SOURCE_LABELS } from '../constants';
import { rub } from '../format';
import type { Fork, ForkFactor } from '../types';
import './ForkBreakdown.css';

const MINUS = '\u2212';

const num = (v: number) => Math.abs(v).toFixed(1).replace('.', ',');
const signed = (v: number) => (v > 0 ? `+${num(v)}` : v < 0 ? `${MINUS}${num(v)}` : '0');
const tone = (v: number) => (v > 0 ? 'bad' : v < 0 ? 'good' : 'zero');

type Props = {
  fork: Fork;
  showSource?: boolean;
};

function Row({ factor, showSource }: { factor: ForkFactor; showSource: boolean }) {
  const source = factor.source ? SOURCE_LABELS[factor.source] : null;
  const known = factor.known && factor.value != null;
  return (
    <li className="breakdown__row">
      <div className="breakdown__name">
        <span>{factor.title}</span>
        {showSource && <span className="breakdown__source">{source ? `Источник: ${source}` : 'Источник: —'}</span>}
        {!known && <span className="breakdown__unknown">Пока неизвестно</span>}
      </div>
      {known ? (
        <span className={`breakdown__val breakdown__val--${tone(factor.value!)}`}>{signed(factor.value!)} п.п.</span>
      ) : (
        <span className="breakdown__val breakdown__val--range">
          от {signed(factor.best ?? 0)} до {signed(factor.worst ?? 0)} п.п.
          {factor.spotPricePp ? <span className="breakdown__price">закрыть пятно: до {MINUS}{num(factor.spotPricePp)} п.п.</span> : null}
        </span>
      )}
    </li>
  );
}

export function ForkBreakdown({ fork, showSource = false }: Props) {
  const location = useLocation();
  const factors = (fork.factors ?? []).filter((f) => f.applicable);
  if (factors.length === 0) return null;
  const loadKnown = factors.some((f) => f.code === 'debt_load' && f.known);
  const existingPayment = fork.existingPayment ?? 0;

  const base = Math.round(
    (fork.rateFrom - factors.reduce((s, f) => s + (f.known ? f.value ?? 0 : f.best ?? 0), 0)) * 100,
  ) / 100;

  return (
    <>
      {existingPayment > 0 && (
        <p className="breakdown__existing">
          <span className="breakdown__existing-title">Учтены действующие платежи по кредитам</span>
          <span>
            <b>{rub(existingPayment)} в месяц</b>{' '}
            {loadKnown ? 'входят в долговую нагрузку.' : 'войдут в долговую нагрузку, когда станет известна выручка.'}
          </span>
        </p>
      )}
      <details className="breakdown">
        <summary className="breakdown__summary">Из чего складывается ставка</summary>
        <ul className="breakdown__list">
          <li className="breakdown__row">
            <div className="breakdown__name"><span>Базовая ставка продукта</span></div>
            <span className="breakdown__val">{num(base)} %</span>
          </li>
          {factors.map((f) => <Row key={f.code} factor={f} showSource={showSource} />)}
          {existingPayment > 0 && (
            <li className="breakdown__row">
              <div className="breakdown__name">
                <span>Действующие платежи по кредитам</span>
                <span className="breakdown__source">
                  {loadKnown ? 'Уже входят в долговую нагрузку' : 'Войдут в долговую нагрузку, когда станет известна выручка'}
                </span>
              </div>
              <span className="breakdown__val">{rub(existingPayment)} в месяц</span>
            </li>
          )}
          <li className="breakdown__row breakdown__row--total">
            <div className="breakdown__name"><span>Итого</span></div>
            <span className="breakdown__val">
              {fork.rateFrom === fork.rateTo ? `${num(fork.rateFrom)} %` : `от ${num(fork.rateFrom)} % до ${num(fork.rateTo)} %`}
            </span>
          </li>
        </ul>
        <Link to="/rules" state={{ back: location.pathname + location.search }} className="breakdown__more">Как мы считаем</Link>
      </details>
    </>
  );
}