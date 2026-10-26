import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Status } from '@alfalab/core-components/status';
import { Text } from '@alfalab/core-components/text';
import { Typography } from '@alfalab/core-components/typography';
import { ApiError, api } from '../api';
import { PRODUCT_NAMES } from '../constants';
import { pct, rub, ruDate } from '../format';
import { STATUS_META } from '../statuses';
import type { ApplicationSummary, DemoUser } from '../types';
import '../App.css';

const SECONDARY = 'var(--color-light-text-secondary)';

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'NETWORK') return 'Нет связи с сервером. Обновите страницу.';
    if (e.code === 'UNAUTHORIZED') return 'Сначала выберите компанию на стартовом экране.';
    return e.message;
  }
  return 'Что-то пошло не так. Обновите страницу.';
}

function forkText(a: ApplicationSummary): string | null {
  if (!a.fork) return null;
  return a.fork.rateFrom === a.fork.rateTo
    ? pct(a.fork.rateFrom)
    : `от ${pct(a.fork.rateFrom)} до ${pct(a.fork.rateTo)}`;
}

export default function ClientHomePage() {
  const navigate = useNavigate();
  const [me, setMe] = useState<DemoUser | null>(null);
  const [apps, setApps] = useState<ApplicationSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([api.getMe(), api.listApplications()])
      .then(([m, list]) => {
        setMe(m);
        setApps(list);
      })
      .catch((e) => setError(errorText(e)));
  }, []);

  if (!me || !apps) {
    return (
      <div className="card">
        <Text tag="p" color={SECONDARY}>{error ?? 'Загружаем…'}</Text>
      </div>
    );
  }

  return (
    <div className="card">
      <header className="card__head">
        <div>
          <Typography.TitleResponsive tag="h1" view="small" font="system" defaultMargins={false}>
            Мои заявки
          </Typography.TitleResponsive>
          <Text tag="div" color={SECONDARY} className="card__sub">
            {me.company?.name ?? me.fio}
          </Text>
        </div>
        {apps.length > 0 && (
          <Button view="accent" size={40} onClick={() => navigate('/client/new')}>
            Новая заявка
          </Button>
        )}
      </header>

      <section className="card__section">
        {apps.length === 0 ? (
          <div className="empty">
            <div className="empty__title">Заявок пока нет</div>
            <Text tag="div" color={SECONDARY}>
              Оформите первую — покажем ставку и что на неё влияет.
            </Text>
            <Button view="accent" size={48} onClick={() => navigate('/client/new')}>
              Новая заявка
            </Button>
          </div>
        ) : (
          <div className="apps">
            {apps.map((a) => {
              const meta = STATUS_META[a.status];
              const fork = forkText(a);
              return (
                <button
                  key={a.id}
                  type="button"
                  className="app"
                  onClick={() => navigate(`/client/application?id=${a.id}`)}
                >
                  <span className="app__main">
                    <span className="app__title">{PRODUCT_NAMES[a.productCode]}</span>
                    <span className="app__sub">
                      {rub(a.amount)} · {a.termMonths} мес. · {ruDate(a.createdAt)}
                    </span>
                    {fork && <span className="app__fork">Ставка: {fork}</span>}
                  </span>
                  <Status color={meta.color} size={32} shape="rounded" uppercase={false}>
                    {meta.label}
                  </Status>
                </button>
              );
            })}
          </div>
        )}
      </section>
    </div>
  );
}