import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Status } from '@alfalab/core-components/status';
import { Text } from '@alfalab/core-components/text';
import { ApiError, api } from '../api';
import { loginAsUnderwriter } from '../auth';
import { ForkBar } from '../components/ForkBar';
import {
  AddSpotForm,
  DecisionForm,
  OffersTable,
  SpotsReview,
  SummaryBlock,
} from '../components/UnderwriterBlocks';
import { FORK_SCALE } from '../constants';
import { rub, ruDate } from '../format';
import { STATUS_META } from '../statuses';
import type { ApplicationDetails, UnderwritingSummary } from '../types';
import '../App.css';

const SECONDARY = 'var(--color-light-text-secondary)';

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'NETWORK') return 'Нет связи с сервером. Попробуйте ещё раз.';
    if (e.code === 'FORBIDDEN') return 'Это действие доступно только сотруднику банка.';
    if (e.code === 'SOURCE_UNAVAILABLE') return 'Источник данных временно недоступен. Попробуйте позже.';
    if (e.code === 'INVALID_TRANSITION') return 'Действие сейчас недоступно: заявка уже в другом статусе.';
    return e.message;
  }
  return 'Что-то пошло не так. Попробуйте ещё раз.';
}

const OUTCOME: Partial<Record<string, string>> = {
  INFO_REQUESTED: 'Запрос отправлен клиенту. Заявка вернётся к вам, когда он ответит.',
  APPROVED: 'Решение вынесено: одобрено на запрошенных условиях.',
  COUNTER_OFFER: 'Решение вынесено: клиенту предложены иные условия.',
  REJECTED: 'Решение вынесено: отказ.',
};

export default function UnderwriterApplicationPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const id = params.get('id');
  const [data, setData] = useState<ApplicationDetails | null>(null);
  const [summary, setSummary] = useState<UnderwritingSummary | null>(null);
  const [summaryError, setSummaryError] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    loginAsUnderwriter()
      .then(() => api.getApplication(id))
      .then(setData)
      .catch((e) => setError(errorText(e)));
    api
      .getUnderwritingSummary(id)
      .then(setSummary)
      .catch((e) =>
        setSummaryError(
          e instanceof ApiError && e.status === 409 ? 'Сводка появится, когда будет рассчитана вилка.' : 'Сводку получить не удалось.',
        ),
      );
  }, [id]);

  const run = useCallback(async (key: string, call: () => Promise<ApplicationDetails>) => {
    setBusy(key);
    setError(null);
    try {
      setData(await call());
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(null);
    }
  }, []);

  if (!id) {
    return (
      <div className="uw-wrap">
        <div className="uw-body" style={{ borderRadius: 24 }}>
          <Text tag="p" color={SECONDARY}>Заявка не выбрана. Вернитесь в очередь.</Text>
          <Button view="secondary" size={40} onClick={() => navigate('/underwriter')}>К очереди</Button>
        </div>
      </div>
    );
  }
  if (!data) {
    return (
      <div className="uw-wrap">
        <div className="uw-body" style={{ borderRadius: 24 }}>
          <Text tag="p" color={SECONDARY}>{error ?? 'Загружаем заявку…'}</Text>
        </div>
      </div>
    );
  }

  const { application, fork, spots, offers } = data;
  const actions = application.availableActions;
  const outcome = OUTCOME[application.status];

  return (
    <div className="uw-wrap">
      <header className="uw-hero">
        <div>
          <h1 className="uw-hero__title">{application.company.name}</h1>
          <div className="uw-hero__sub">
            {application.company.inn ? `ИНН ${application.company.inn} · ` : ''}
            {application.product.name} · {rub(application.amount)} · {application.termMonths} мес.
          </div>
        </div>
        <div className="uw-hero__status">
          <Status color={STATUS_META[application.status].color} size={32} shape="rounded" uppercase={false}>
            {STATUS_META[application.status].label}
          </Status>
        </div>
      </header>

      <div className="uw-body">

      {error && (
        <section className="card__section">
          <div className="notice notice--bad">{error}</div>
        </section>
      )}

      {outcome && (
        <section className="card__section">
          <div className="notice notice--good">{outcome}</div>
          {application.status === 'INFO_REQUESTED' && application.infoRequestDeadline && (
            <Text tag="p" color={SECONDARY}>Срок ответа клиента — до {ruDate(application.infoRequestDeadline)}.</Text>
          )}
          <div className="uw__actions">
            <Button view="secondary" size={40} onClick={() => navigate('/underwriter')}>К очереди</Button>
          </div>
        </section>
      )}

      {fork && (
        <section className="card__section">
          <ForkBar
            from={fork.rateFrom}
            to={fork.rateTo}
            scaleMin={FORK_SCALE.min}
            scaleMax={FORK_SCALE.max}
            approveMax={fork.thresholds.approveMax}
            rejectMin={fork.thresholds.rejectMin}
          />
        </section>
      )}

      <SummaryBlock summary={summary} error={summaryError} />

      <SpotsReview
        spots={spots}
        canReview={actions.includes('REVIEW_SPOT')}
        busy={busy}
        onReview={(spotId, verdict, comment) =>
          run(spotId, () => api.reviewSpot(application.id, spotId, { verdict, comment }))
        }
      />

      {actions.includes('ADD_SPOT') && (
        <AddSpotForm busy={busy === 'add'} onAdd={(body) => run('add', () => api.addSpot(application.id, body))} />
      )}

      {actions.includes('DECIDE') ? (
        <DecisionForm
          data={data}
          busy={busy === 'decide'}
          onDecide={(body) => run('decide', () => api.makeDecision(application.id, body))}
        />
      ) : (
        <section className="card__section">
          <h2 className="uw__title">Варианты условий</h2>
          <OffersTable offers={offers} selectable={false} selected={[]} onToggle={() => undefined} />
        </section>
      )}
    </div>
    </div>
  );
}