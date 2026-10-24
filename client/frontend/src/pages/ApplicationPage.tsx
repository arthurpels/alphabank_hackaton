import { useCallback, useEffect, useState } from 'react';
import { Button } from '@alfalab/core-components/button';
import { Status } from '@alfalab/core-components/status';
import { Text } from '@alfalab/core-components/text';
import { Typography } from '@alfalab/core-components/typography';
import { ApiError, USE_MOCKS, api } from '../api';
import { ForkBar } from '../components/ForkBar';
import { SpotCard } from '../components/SpotCard';
import { FORK_SCALE } from '../constants';
import { STATUS_META } from '../statuses';
import type { ApplicationDetails, DecisionZone, Spot, SpotCloseRequest } from '../types';
import '../App.css';

const APPLICATION_ID = 'coffee-application';

const fmt = (v: number) => String(v).replace('.', ',');
const SECONDARY = 'var(--color-light-text-secondary)';

const NOTICE: Record<DecisionZone, { tone: 'good' | 'mid' | 'bad'; text: string }> = {
  APPROVE: { tone: 'good', text: 'Даже в худшем случае условия подходят: решение будет принято автоматически.' },
  UNDERWRITER: { tone: 'mid', text: 'Заявку может посмотреть специалист. Закройте ещё пятна — решение будет быстрее.' },
  REJECT: { tone: 'bad', text: 'Даже если всё неизвестное окажется хорошим, условия не подходят: будет отказ.' },
};

function spotsWord(n: number) {
  const last = n % 10;
  const tens = n % 100;
  if (tens >= 11 && tens <= 14) return 'пятен';
  if (last === 1) return 'пятно';
  if (last >= 2 && last <= 4) return 'пятна';
  return 'пятен';
}

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'NETWORK') return 'Нет связи с сервером. Попробуйте ещё раз.';
    if (e.code === 'SOURCE_UNAVAILABLE') return 'Источник данных временно недоступен. Попробуйте позже.';
    if (e.code === 'INVALID_TRANSITION') return 'Это действие сейчас недоступно: заявка уже в другом статусе.';
    return e.message;
  }
  return 'Что-то пошло не так. Попробуйте ещё раз.';
}
function closeRequest(spot: Spot): SpotCloseRequest {
  if (!spot.closeMethod) throw new Error('У пятна нет способа закрытия');
  if (spot.closeMethod === 'CONNECT_SOURCE' && spot.source) {
    return { method: 'CONNECT_SOURCE', sourceCode: spot.source, consent: true };
  }
  return { method: spot.closeMethod };
}

export default function ApplicationPage() {
  const [data, setData] = useState<ApplicationDetails | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null); // 'submit', 'reset' или id пятна

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

  useEffect(() => {
    api
      .getApplication(APPLICATION_ID)
      .then(setData)
      .catch((e) => setError(errorText(e)));
  }, []);

  if (!data) {
    return (
      <div className="card">
        <Text tag="p" color={SECONDARY}>{error ?? 'Загружаем заявку…'}</Text>
      </div>
    );
  }

  const { application, fork, spots } = data;
  const openSpots = spots.filter((s) => s.status !== 'CLOSED');
  const canCloseSpots = application.status === 'COLLECTING' || application.status === 'INFO_REQUESTED';
  const exact = fork !== null && fork.rateFrom === fork.rateTo; // вилка сошлась в одну точку
  const notice = fork ? NOTICE[fork.zone] : null;

  return (
    <div className="card">
      <header className="card__head">
        <div>
          <Typography.TitleResponsive tag="h1" view="small" font="system" defaultMargins={false}>
            {application.product.name}
          </Typography.TitleResponsive>
          <Text tag="div" color={SECONDARY} className="card__sub">
            {application.company.name} · {(application.amount / 100).toLocaleString('ru-RU')} ₽ ·{' '}
            {application.termMonths} мес.
          </Text>
        </div>
        <Status color={STATUS_META[application.status].color} size={32} shape="rounded" uppercase={false}>
          {STATUS_META[application.status].label}
        </Status>
      </header>

      {fork && notice ? (
        <section className="card__section">
          <div className="rate-row">
            <Text tag="span" color={SECONDARY}>{exact ? 'Ставка' : 'Ставка сейчас'}</Text>
            <Typography.TitleResponsive tag="div" view="large" font="system" defaultMargins={false}>
              {exact ? `${fmt(fork.rateFrom)}%` : `от ${fmt(fork.rateFrom)}% до ${fmt(fork.rateTo)}%`}
            </Typography.TitleResponsive>
          </div>
          <Text tag="div" color={SECONDARY} align="center" className="hint">
            {exact ? (
              <div>Вилка сошлась: банк знает достаточно и называет точную ставку.</div>
            ) : (
              <>
                <div>Левый край — если всё неизвестное окажется хорошим.</div>
                <div>Правый — если плохим: так банк вынужден считать сейчас.</div>
              </>
            )}
          </Text>
          <ForkBar
            from={fork.rateFrom}
            to={fork.rateTo}
            scaleMin={FORK_SCALE.min}
            scaleMax={FORK_SCALE.max}
            approveMax={fork.thresholds.approveMax}
            rejectMin={fork.thresholds.rejectMin}
          />
          <div className={`notice notice--${notice.tone}`}>{notice.text}</div>
        </section>
      ) : (
        <section className="card__section">
          <Text tag="p" color={SECONDARY}>Ставка появится после расчёта.</Text>
        </section>
      )}

      {error && (
        <section className="card__section">
          <div className="notice notice--bad">{error}</div>
        </section>
      )}

      <section className="card__section">
        <h2 className="spots__title">
          {openSpots.length > 0
            ? `Что банк о вас пока не знает: ${openSpots.length} ${spotsWord(openSpots.length)}`
            : 'Все пятна закрыты'}
        </h2>
        {spots.map((s) => (
          <SpotCard
            key={s.id}
            spot={s}
            busy={busy === s.id}
            disabled={!canCloseSpots || busy !== null}
            onClose={() => run(s.id, () => api.closeSpot(application.id, s.id, closeRequest(s)))}
          />
        ))}
      </section>

      <footer className="card__footer">
        <Button
          view="accent"
          size={48}
          loading={busy === 'submit'}
          disabled={application.status !== 'COLLECTING' || busy !== null}
          onClick={() => run('submit', () => api.submitApplication(application.id))}
        >
          Отправить заявку
        </Button>
        {USE_MOCKS && (
          <Button
            view="secondary"
            size={48}
            disabled={busy !== null}
            onClick={() => run('reset', () => api.evaluateApplication(application.id))}
          >
            Начать заново
          </Button>
        )}
      </footer>
    </div>
  );
}