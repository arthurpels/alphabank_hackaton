import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Status } from '@alfalab/core-components/status';
import { Table } from '@alfalab/core-components/table';
import { Text } from '@alfalab/core-components/text';
import { ApiError, api } from '../api';
import { loginAsUnderwriter } from '../auth';
import { PRODUCT_NAMES } from '../constants';
import { pct, rub } from '../format';
import { STATUS_META } from '../statuses';
import type { ApplicationStatus, QueueItem } from '../types';
import '../App.css';

// Очередь андеррайтера (T39): GET /underwriting/queue
const SECONDARY = 'var(--color-light-text-secondary)';

const FILTERS: { code: ApplicationStatus | 'ALL'; label: string }[] = [
  { code: 'ALL', label: 'Все' },
  { code: 'UNDERWRITING', label: 'На рассмотрении' },
  { code: 'INFO_REQUESTED', label: 'Ждём клиента' },
];

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'NETWORK') return 'Нет связи с сервером. Обновите страницу.';
    if (e.code === 'FORBIDDEN') return 'У этого пользователя нет доступа к очереди.';
    return e.message;
  }
  return 'Что-то пошло не так. Обновите страницу.';
}

const forkText = (i: QueueItem) =>
  !i.fork ? '—' : i.fork.rateFrom === i.fork.rateTo ? pct(i.fork.rateFrom) : `${pct(i.fork.rateFrom)} – ${pct(i.fork.rateTo)}`;

export default function UnderwriterPage() {
  const navigate = useNavigate();
  const [filter, setFilter] = useState<ApplicationStatus | 'ALL'>('ALL');
  const [items, setItems] = useState<QueueItem[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    loginAsUnderwriter()
      .then(() => api.getUnderwritingQueue(filter === 'ALL' ? undefined : filter))
      .then((list) => alive && setItems(list))
      .catch((e) => alive && setError(errorText(e)));
    return () => {
      alive = false;
    };
  }, [filter]);

    return (
    <div className="uw-wrap">
      <header className="uw-hero">
        <div>
          <h1 className="uw-hero__title">Очередь заявок</h1>
          <div className="uw-hero__sub">Заявки на рассмотрении и в ожидании ответа клиента</div>
        </div>
      </header>

      <div className="uw-body">
        <section className="card__section">
          <div className="chips" role="radiogroup" aria-label="Фильтр очереди">
            {FILTERS.map((f) => (
              <button
                key={f.code}
                type="button"
                role="radio"
                aria-checked={f.code === filter}
                className={`term ${f.code === filter ? 'term--selected' : ''}`}
                onClick={() => {
                  setItems(null);
                  setError(null);
                  setFilter(f.code);
                }}
              >
                {f.label}
              </button>
            ))}
          </div>
        </section>

        <section className="card__section">
          {error && <div className="notice notice--bad">{error}</div>}
          {!error && !items && <Text tag="p" color={SECONDARY}>Загружаем…</Text>}
          {items && items.length === 0 && (
            <div className="empty">
              <div className="empty__title">Очередь пуста</div>
              <Text tag="div" color={SECONDARY}>Новые заявки появятся здесь, когда клиенты их отправят.</Text>
            </div>
          )}
          {items && items.length > 0 && (
            <Table wrapper className="queue-table">
              <Table.THead>
                <Table.THeadCell title="Компания" textAlign="center">Компания</Table.THeadCell>
                <Table.THeadCell title="Продукт" textAlign="center">Продукт</Table.THeadCell>
                <Table.THeadCell title="Сумма" textAlign="center">Сумма</Table.THeadCell>
                <Table.THeadCell title="Ставка" textAlign="center">Ставка</Table.THeadCell>
                <Table.THeadCell title="Пятна" textAlign="center">Пятна</Table.THeadCell>
                <Table.THeadCell title="Статус" textAlign="center">Статус</Table.THeadCell>
              </Table.THead>
              <Table.TBody>
                {items.map((i) => (
                  <Table.TRow
                    key={i.applicationId}
                    className="queue-row"
                    onClick={() => navigate(`/underwriter/application?id=${i.applicationId}`)}
                  >
                    <Table.TCell>
                      <div className="queue-company">{i.companyName}</div>
                      <div className="queue-inn">ИНН {i.inn}</div>
                    </Table.TCell>
                    <Table.TCell>{PRODUCT_NAMES[i.productCode]}</Table.TCell>
                    <Table.TCell>
                      <div className="queue-num queue-main">{rub(i.amount)}</div>
                      <div className="queue-num queue-sub">{i.termMonths} мес.</div>
                    </Table.TCell>
                    <Table.TCell>
                      <div className="queue-num queue-main">{forkText(i)}</div>
                    </Table.TCell>
                    <Table.TCell>
                      <div className="queue-num queue-main">{i.openSpots} открыто</div>
                      <div className="queue-num queue-sub">
                        {i.onReviewSpots > 0 ? `${i.onReviewSpots} на проверке` : ' '}
                      </div>
                    </Table.TCell>
                    <Table.TCell>
                      <Status color={STATUS_META[i.status].color} size={24} shape="rounded" uppercase={false}>
                        {STATUS_META[i.status].label}
                      </Status>
                    </Table.TCell>
                  </Table.TRow>
                ))}
              </Table.TBody>
            </Table>
          )}
        </section>
      </div>
    </div>
  );
}