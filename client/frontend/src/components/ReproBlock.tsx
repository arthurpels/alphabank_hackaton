import { useState } from 'react';
import { Button } from '@alfalab/core-components/button';
import { CheckmarkCircleMIcon } from '@alfalab/icons-glyph/CheckmarkCircleMIcon';
import { CrossCircleMIcon } from '@alfalab/icons-glyph/CrossCircleMIcon';
import { ApiError, api } from '../api';
import { ruDate } from '../format';
import type { FieldDiff, ForkVerification } from '../types';
import { purposeAuthor } from '../verdictLabels';
import { AuthorBadge } from './AuthorBadge';
import './ReproBlock.css';

const PATH_LABELS: Record<string, string> = {
  '$.rateFrom': 'Ставка «от», %',
  '$.rateTo': 'Ставка «до», %',
  requestHash: 'Входные данные заявки (хеш)',
};

const FACTOR_LABELS: Record<string, string> = {
  purpose: 'Цель кредита',
  debt_load: 'Долговая нагрузка',
  revenue_stability: 'Стабильность выручки',
  collateral: 'Обеспечение',
};

function value(v: unknown): string {
  if (v === null || v === undefined) return '—';
  if (typeof v === 'number') return String(v).replace('.', ',');
  if (typeof v === 'string') return v;
  return JSON.stringify(v);
}

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'NOT_FOUND') {
      return 'У заявки нет сохранённого снимка расчёта: она рассчитана до включения проверки. Пересчитайте вилку заново.';
    }
    if (e.code === 'SOURCE_UNAVAILABLE') return 'Скоринг сейчас недоступен. Попробуйте позже.';
    return e.message;
  }
  return 'Не удалось выполнить проверку. Попробуйте ещё раз.';
}

function DiffTable({ diff }: { diff: FieldDiff[] }) {
  return (
    <table className="repro__diff">
      <thead>
        <tr>
          <th>Что разошлось</th>
          <th>Сохранено</th>
          <th>Пересчёт</th>
        </tr>
      </thead>
      <tbody>
        {diff.map((d) => (
          <tr key={d.path}>
            <td>{PATH_LABELS[d.path] ?? d.path}</td>
            <td>{value(d.expected)}</td>
            <td>{value(d.actual)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function ReproBlock({ applicationId, rulesVersion }: { applicationId: string; rulesVersion?: string | null }) {
  const [result, setResult] = useState<ForkVerification | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const verify = async () => {
    setBusy(true);
    setError(null);
    try {
      setResult(await api.verifyFork(applicationId));
    } catch (e) {
      setResult(null);
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  const version = result?.rulesVersion ?? rulesVersion;
  const engineChanged =
    result?.reproducible && result.engineVersion && result.currentEngineVersion && result.engineVersion !== result.currentEngineVersion;

  return (
    <section className="card__section repro">
      <h2 className="uw__title">Воспроизводимость решения</h2>

      {!result && !error && (
        <p className="uw__muted">
          {version ? `Правила ${version}. ` : ''}
          Банк может заново пересчитать вилку по сохранённым данным заявки и сверить результат с тем, на чём основано решение.
        </p>
      )}

      {result?.reproducible && (
        <div className="notice notice--good repro__result repro__result--center">
          <b className="repro__title">
            <CheckmarkCircleMIcon className="repro__icon" />
            Решение воспроизводимо · правила {result.rulesVersion}
          </b>
          <div>
            Пересчёт дал тот же ответ, что сохранён в заявке. Расчёт снимка: {ruDate(result.calculatedAt)}.
          </div>
          {engineChanged && <div>Код расчёта обновился с момента решения, но ответ остался тем же.</div>}
        </div>
      )}

      {result && !result.reproducible && (
        <>
          <div className="notice notice--bad repro__result repro__result--center">
            <b className="repro__title">
              <CrossCircleMIcon className="repro__icon" />
              Пересчёт не совпал с сохранённым · правила {result.rulesVersion}
            </b>
            <div>
              {result.diff.some((d) => d.path === 'requestHash')
                ? 'Сохранённые входные данные расчёта изменились после решения.'
                : 'Заново посчитанная вилка отличается от сохранённой.'}
            </div>
          </div>
          {result.diff.length > 0 && <DiffTable diff={result.diff} />}
        </>
      )}

      {result && result.verdicts.length > 0 && (
        <div className="repro__verdicts">
          <div className="uw__muted">Ответы, на которых построен расчёт (их ИИ повторно не спрашивали):</div>
          {result.verdicts.map((v) => (
            <div key={v.spotId} className="repro__verdict">
              <span>{FACTOR_LABELS[v.scoringFactor ?? ''] ?? 'Ответ клиента'}</span>
              <AuthorBadge author={purposeAuthor(v.verdict)} />
            </div>
          ))}
        </div>
      )}

      {error && <div className="notice notice--bad">{error}</div>}

      <div className="uw__actions">
        <Button view={result ? 'secondary' : 'accent'} size={40} loading={busy} onClick={verify}>
          {result ? 'Пересчитать ещё раз' : 'Пересчитать'}
        </Button>
      </div>
    </section>
  );
}