import { useEffect, useState, type ComponentType, type SVGProps } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Spinner } from '@alfalab/core-components/spinner';
import { Text } from '@alfalab/core-components/text';
import { Typography } from '@alfalab/core-components/typography';
import { CheckmarkCircleMIcon } from '@alfalab/icons-glyph/CheckmarkCircleMIcon';
import { CrossCircleMIcon } from '@alfalab/icons-glyph/CrossCircleMIcon';
import { ExclamationCircleMIcon } from '@alfalab/icons-glyph/ExclamationCircleMIcon';
import { ApiError, api } from '../api';
import { ruDate } from '../format';
import type { CheckStatus, SourceChecks, SourceCode } from '../types';
import '../App.css';
import './CheckPage.css';

const SECONDARY = 'var(--color-light-text-secondary)';
const REVEAL_MS = 700;

const CHECK_TITLES: Partial<Record<SourceCode, string>> = {
  EGRUL: 'ЕГРЮЛ / ЕГРИП — регистрация компании',
  FNS: 'ФНС — налоги',
  BKI: 'Бюро кредитных историй',
};

const STATUS_ICONS: Record<CheckStatus, ComponentType<SVGProps<SVGSVGElement>>> = {
  OK: CheckmarkCircleMIcon,
  WARNING: ExclamationCircleMIcon,
  BLOCKED: CrossCircleMIcon,
};

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'NETWORK') return 'Нет связи с сервером. Попробуйте ещё раз.';
    if (e.code === 'SOURCE_UNAVAILABLE') return 'Источник данных временно недоступен. Попробуйте позже.';
    return e.message;
  }
  return 'Что-то пошло не так. Попробуйте ещё раз.';
}

function timeText(iso: string) {
  return `${ruDate(iso)} в ${new Date(iso).toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}`;
}

export default function CheckPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const id = params.get('id');
  const [checks, setChecks] = useState<SourceChecks | null>(null);
  const [shown, setShown] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    api
      .getChecks(id)
      .then(setChecks)
      .catch((e) => setError(errorText(e)));
  }, [id]);

  useEffect(() => {
    if (!checks || shown >= checks.items.length) return;
    const timer = setTimeout(() => setShown((n) => n + 1), REVEAL_MS);
    return () => clearTimeout(timer);
  }, [checks, shown]);

  if (!id) return <Navigate to="/client/home" replace />;

  const toApplication = () => navigate(`/client/application?id=${id}`);
  const done = checks !== null && shown >= checks.items.length;
  const blocked = checks?.items.some((i) => i.status === 'BLOCKED') ?? false;
  const warning = checks?.items.some((i) => i.status === 'WARNING') ?? false;

  return (
    <div className="card">
      <header className="card__head">
        <div>
          <Typography.TitleResponsive tag="h1" view="small" font="system" defaultMargins={false}>
            Проверяем данные компании
          </Typography.TitleResponsive>
          <Text tag="div" color={SECONDARY} className="card__sub">
            Банк заново запрашивает реестры: данные из профиля могли устареть
          </Text>
        </div>
      </header>

      {error && (
        <section className="card__section">
          <div className="notice notice--bad">{error}</div>
          <div className="checks__actions">
            <Button view="secondary" size={48} onClick={toApplication}>
              К заявке
            </Button>
          </div>
        </section>
      )}

      {!error && (
        <section className="card__section">
          <ol className="checks">
            {(checks?.items ?? [null, null, null]).map((item, i) => {
              const ready = item !== null && i < shown;
              const Icon = ready ? STATUS_ICONS[item.status] : null;
              return (
                <li key={item?.source ?? i} className={`check ${ready ? `check--${item.status.toLowerCase()}` : 'check--pending'}`}>
                  <span className="check__icon">{Icon ? <Icon /> : <Spinner visible size={24} lineWidth={2} />}</span>
                  <div className="check__body">
                    <div className="check__title">
                      {item ? (CHECK_TITLES[item.source] ?? item.source) : 'Запрашиваем источник…'}
                    </div>
                    {ready && (
                      <>
                        <ul className="check__facts">
                          {item.facts.map((f) => <li key={f}>{f}</li>)}
                        </ul>
                        {item.problems.length > 0 && (
                          <ul className="check__problems">
                            {item.problems.map((p) => <li key={p}>{p}</li>)}
                          </ul>
                        )}
                      </>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>
        </section>
      )}

      {done && checks && (
        <section className="card__section">
          <div className={`notice notice--${blocked ? 'bad' : warning ? 'mid' : 'good'}`}>
            {blocked
              ? 'Есть условия, которые устранить нельзя: заявка не может быть одобрена.'
              : warning
                ? 'Данные подтверждены, но есть что устранить: это отмечено красным на следующем шаге.'
                : 'Данные подтверждены. Банк использует их в расчёте ставки.'}
          </div>
          <Text tag="p" color={SECONDARY} className="checks__time">
            Проверено {timeText(checks.checkedAt)}
          </Text>
          <div className="checks__actions">
            <Button view="accent" size={48} onClick={toApplication}>
              {blocked ? 'Перейти к заявке' : 'Посмотреть ставку'}
            </Button>
          </div>
        </section>
      )}
    </div>
  );
}
