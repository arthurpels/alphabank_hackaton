import { useState, type ComponentType, type SVGProps } from 'react';
import { Button } from '@alfalab/core-components/button';
import { Spinner } from '@alfalab/core-components/spinner';
import { BanknotesMIcon } from '@alfalab/icons-glyph/BanknotesMIcon';
import { CheckmarkCircleMIcon } from '@alfalab/icons-glyph/CheckmarkCircleMIcon';
import { CheckmarkMIcon } from '@alfalab/icons-glyph/CheckmarkMIcon';
import { ExclamationCircleMIcon } from '@alfalab/icons-glyph/ExclamationCircleMIcon';
import { ClockMIcon } from '@alfalab/icons-glyph/ClockMIcon';
import { CrossCircleMIcon } from '@alfalab/icons-glyph/CrossCircleMIcon';
import { DocumentLinesMIcon } from '@alfalab/icons-glyph/DocumentLinesMIcon';
import { PercentMIcon } from '@alfalab/icons-glyph/PercentMIcon';
import { PRODUCT_NAMES } from '../constants';
import { pct, rub, ruDate } from '../format';
import type { ApplicationDetails, ApplicationStatus, Contract, Offer } from '../types';
import './StatusBlocks.css';


type Tone = 'success' | 'info' | 'attention' | 'danger' | 'neutral';
type Icon = ComponentType<SVGProps<SVGSVGElement>>;

function Hero(props: {
  tone: Tone;
  icon?: Icon;
  loading?: boolean;
  title: string;
  text?: string;
  figure?: string;
  figureCaption?: string;
  confetti?: boolean;
}) {
  const { tone, icon: IconComponent, loading, title, text, figure, figureCaption, confetti } = props;
  return (
    <div className={`hero-block hero-block--${tone}`}>
      {confetti && (
        <div className="confetti" aria-hidden="true">
          {Array.from({ length: 16 }, (_, i) => <i key={i} />)}
        </div>
      )}
      <div className={`hero-block__icon ${loading ? 'hero-block__icon--pulse' : ''}`}>
        {loading ? <Spinner visible size={28} lineWidth={3} /> : IconComponent && <IconComponent />}
      </div>
      <h2 className="hero-block__title">{title}</h2>
      {text && <p className="hero-block__text">{text}</p>}
      {figure && (
        <div className="hero-block__figure">
          <span>{figure}</span>
          {figureCaption && <small>{figureCaption}</small>}
        </div>
      )}
    </div>
  );
}

function Stats({ items }: { items: { label: string; value: string; accent?: boolean }[] }) {
  return (
    <div className="stats">
      {items.map((s) => (
        <div key={s.label} className={`stat ${s.accent ? 'stat--accent' : ''}`}>
          <span className="stat__label">{s.label}</span>
          <span className="stat__value">{s.value}</span>
        </div>
      ))}
    </div>
  );
}

export function CancelButton({ busy, onCancel }: { busy: boolean; onCancel: () => void }) {
  const [confirm, setConfirm] = useState(false);
  if (!confirm) {
    return (
      <Button view="secondary" size={48} disabled={busy} onClick={() => setConfirm(true)}>
        Отказаться
      </Button>
    );
  }
  return (
    <div className="confirm">
      <span className="confirm__text">Отказаться от заявки?</span>
      <Button view="secondary" size={40} loading={busy} onClick={onCancel}>Да, отказаться</Button>
      <Button view="text" size={40} disabled={busy} onClick={() => setConfirm(false)}>Нет</Button>
    </div>
  );
}

export function CheckingBlock({ status }: { status: ApplicationStatus }) {
  const steps = [
    { label: 'Заявка принята', state: 'done' },
    { label: 'Автопроверка', state: status === 'AUTO_CHECK' ? 'done' : 'active' },
    { label: 'Решение', state: status === 'AUTO_CHECK' ? 'active' : 'todo' },
  ];
  return (
    <section className="block">
      <Hero
        tone="info"
        loading
        title="Заявка принята, проверяем"
        text="Сверяем источники, стоп-факторы и пересчитываем вилку. Страница обновится сама."
      />
      <ol className="track">
        {steps.map((s) => (
          <li key={s.label} className={`track__step track__step--${s.state}`}>
            <span className="track__node">
              {s.state === 'done' ? <CheckmarkMIcon /> : s.state === 'active' ? <Spinner visible size={16} lineWidth={2} /> : null}
            </span>
            <span className="track__label">{s.label}</span>
          </li>
        ))}
      </ol>
    </section>
  );
}

export function SpecialistBlock({ data }: { data: ApplicationDetails }) {
  const onReview = data.spots.filter((s) => s.status === 'ON_REVIEW');
  return (
    <section className="block">
      <Hero
        tone="attention"
        icon={ClockMIcon}
        title="Заявку смотрит специалист"
        text="Автоматически решить не получилось: нужна проверка человеком. Мы сообщим, когда будет решение."
      />
      {onReview.length > 0 && (
        <>
          <h3 className="block__sub">Ответы на проверке</h3>
          <ul className="chips-list">
            {onReview.map((s) => <li key={s.id}>{s.title}</li>)}
          </ul>
        </>
      )}
    </section>
  );
}

export function InfoRequestedBlock({ data, onCancel, busy }: { data: ApplicationDetails; onCancel: () => void; busy: boolean }) {
  const deadline = data.application.infoRequestDeadline;
  const canCancel = data.application.availableActions.includes('CANCEL');
  return (
    <section className="block">
      <Hero
        tone="attention"
        icon={ExclamationCircleMIcon}
        title="Специалисту нужны уточнения"
        text={
          deadline
            ? `Закройте новые пятна до ${ruDate(deadline)}: иначе заявка будет отменена. Когда ответите на всё, заявка вернётся к специалисту.`
            : 'Закройте новые пятна: когда ответите на всё, заявка вернётся к специалисту.'
        }
      />
      {canCancel && (
        <div className="block__actions">
          <CancelButton busy={busy} onCancel={onCancel} />
        </div>
      )}
    </section>
  );
}

export function ApprovedBlock(props: {
  data: ApplicationDetails;
  busy: string | null;
  onAccept: (offerId: string) => void;
  onCancel: () => void;
}) {
  const { data, busy, onAccept, onCancel } = props;
  const offer = data.offers.find((o) => o.status === 'OFFERED' || o.status === 'CHOSEN');
  const canAccept = data.application.availableActions.includes('ACCEPT');
  const bySystem = data.decision?.decidedBy === 'SYSTEM';

  return (
    <section className="block">
      <Hero
        tone="success"
        icon={CheckmarkCircleMIcon}
        title="Заявка одобрена"
        text={bySystem ? 'Решение принято автоматически: даже в худшем случае условия подходят.' : 'Решение принял специалист банка.'}
        figure={offer ? pct(offer.fork.rateTo) : undefined}
        figureCaption="годовых"
      />
      {offer && (
        <>
          <Stats
            items={[
              { label: 'Сумма', value: rub(offer.amount) },
              { label: 'Срок', value: `${offer.termMonths} мес.` },
              { label: 'Продукт', value: PRODUCT_NAMES[offer.productCode] },
            ]}
          />
          {canAccept && (
            <div className="block__actions">
              <Button view="accent" size={48} loading={busy === 'accept'} disabled={busy !== null} onClick={() => onAccept(offer.id)}>
                Перейти к оформлению
              </Button>
              <CancelButton busy={busy === 'cancel'} onCancel={onCancel} />
            </div>
          )}
        </>
      )}
    </section>
  );
}

function differences(offer: Offer, data: ApplicationDetails): string[] {
  const { application } = data;
  if (offer.isRequested) return ['Как вы просили'];
  const list: string[] = [];
  if (offer.amount < application.amount) list.push('Меньше сумма');
  if (offer.termMonths > application.termMonths) list.push('Дольше срок');
  if (PRODUCT_NAMES[offer.productCode] !== application.product.name) list.push('Другой продукт');
  if (offer.collateralType) list.push('Нужно обеспечение');
  return list;
}

// 2.8 Иные условия: COUNTER_OFFER
export function CounterOfferBlock(props: {
  data: ApplicationDetails;
  busy: string | null;
  onAccept: (offerId: string) => void;
  onCancel: () => void;
}) {
  const { data, busy, onAccept, onCancel } = props;
  const offers = data.offers.filter((o) => o.status === 'OFFERED');
  const [selected, setSelected] = useState<string | null>(offers[0]?.id ?? null);
  const canAccept = data.application.availableActions.includes('ACCEPT');

  return (
    <section className="block">
      <Hero
        tone="info"
        icon={PercentMIcon}
        title="Банк предлагает другие условия"
        text="Запрошенные условия не проходят, но есть варианты, на которых мы готовы дать деньги. Выберите подходящий."
      />
      <div className="offers">
        {offers.map((o) => (
          <button
            key={o.id}
            type="button"
            aria-pressed={selected === o.id}
            className={`offer ${selected === o.id ? 'offer--selected' : ''}`}
            onClick={() => setSelected(o.id)}
          >
            <span className="offer__radio" />
            <span className="offer__body">
              <span className="offer__title">{PRODUCT_NAMES[o.productCode]}</span>
              <span className="offer__main">{rub(o.amount)} · {o.termMonths} мес.</span>
              <span className="offer__tags">
                {differences(o, data).map((d) => <span key={d} className="tag">{d}</span>)}
              </span>
            </span>
            <span className="offer__rate">
              {pct(o.fork.rateTo)}
              <small>годовых</small>
            </span>
          </button>
        ))}
      </div>
      {canAccept && (
        <div className="block__actions">
          <Button
            view="accent"
            size={48}
            loading={busy === 'accept'}
            disabled={busy !== null || !selected}
            onClick={() => selected && onAccept(selected)}
          >
            Принять выбранный вариант
          </Button>
          <CancelButton busy={busy === 'cancel'} onCancel={onCancel} />
        </div>
      )}
    </section>
  );
}

export function RejectedBlock({ data, onHome }: { data: ApplicationDetails; onHome: () => void }) {
  const reason = data.spots.find((s) => s.id === data.decision?.reasonSpotId);
  const byUnderwriter = data.decision?.decidedBy === 'UNDERWRITER';
  return (
    <section className="block">
      <Hero
        tone="danger"
        icon={CrossCircleMIcon}
        title="Мы не можем одобрить заявку"
        text={byUnderwriter ? 'Решение принял специалист банка.' : 'Решение принято автоматически по правилам банка.'}
      />
      {(reason || data.decision?.comment) && (
        <div className="note note--danger">
          <span className="note__label">Причина</span>
          {reason && <strong className="note__title">{reason.title}</strong>}
          {reason && <span className="note__text">{reason.clientHint}</span>}
          {data.decision?.comment && <span className="note__text">{data.decision.comment}</span>}
        </div>
      )}
      <div className="note">
        <span className="note__label">Что можно сделать</span>
        <span className="note__text">
          {reason && reason.closeMethod === null
            ? 'Это условие устранить нельзя. Если обстоятельства изменятся, подайте новую заявку.'
            : 'Когда причина будет устранена, подайте новую заявку — мы рассчитаем условия заново.'}
        </span>
      </div>
      <div className="block__actions">
        <Button view="secondary" size={48} onClick={onHome}>К выбору компании</Button>
      </div>
    </section>
  );
}

function Schedule({ contract }: { contract: Contract }) {
  return (
    <div className="sched">
      <table>
        <thead>
          <tr>
            <th>№</th><th>Дата</th><th>Платёж</th><th>Основной долг</th><th>Проценты</th><th>Остаток</th>
          </tr>
        </thead>
        <tbody>
          {contract.schedule.map((p) => (
            <tr key={p.number}>
              <td>{p.number}</td>
              <td>{ruDate(p.date)}</td>
              <td>{rub(p.payment)}</td>
              <td>{rub(p.principal)}</td>
              <td>{rub(p.interest)}</td>
              <td>{rub(p.balance)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function BalanceChart({ contract }: { contract: Contract }) {
  const W = 600;
  const H = 120;
  const pts = [contract.amount, ...contract.schedule.map((p) => p.balance)];
  const x = (i: number) => (i / (pts.length - 1)) * W;
  const y = (v: number) => H - (v / contract.amount) * (H - 8) - 4;
  const line = pts.map((v, i) => `${i === 0 ? 'M' : 'L'}${x(i).toFixed(1)},${y(v).toFixed(1)}`).join(' ');
  return (
    <div className="chart">
      <div className="chart__head">
        <span>Остаток долга</span>
        <span>{rub(contract.amount)} → 0 ₽</span>
      </div>
      <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" role="img" aria-label="График остатка долга">
        <path d={`${line} L${W},${H} L0,${H} Z`} className="chart__area" />
        <path d={line} className="chart__line" vectorEffect="non-scaling-stroke" />
      </svg>
      <div className="chart__foot">
        <span>{ruDate(contract.schedule[0].date)}</span>
        <span>{ruDate(contract.schedule[contract.schedule.length - 1].date)}</span>
      </div>
    </div>
  );
}

export function SigningBlock(props: {
  data: ApplicationDetails;
  busy: string | null;
  onSign: () => void;
}) {
  const { data, busy, onSign } = props;
  const c = data.contract;
  if (!c) return null;
  return (
    <section className="block">
      <Hero
        tone="info"
        icon={DocumentLinesMIcon}
        title="Оформление договора"
        text="Проверьте условия. Подписание и зачисление смоделированы одним шагом."
      />
      <Stats
        items={[
          { label: 'Сумма', value: rub(c.amount) },
          { label: 'Срок', value: `${c.termMonths} мес.` },
          { label: 'Ставка', value: pct(c.rate) },
          { label: 'Платёж в месяц', value: rub(c.monthlyPayment), accent: true },
        ]}
      />
      <details className="details">
        <summary>График платежей ({c.schedule.length})</summary>
        <Schedule contract={c} />
      </details>
      {data.application.availableActions.includes('SIGN') && (
        <div className="block__actions">
          <Button view="accent" size={48} loading={busy === 'sign'} disabled={busy !== null} onClick={onSign}>
            Подписать договор
          </Button>
        </div>
      )}
    </section>
  );
}

export function IssuedBlock({ contract }: { contract: Contract | null }) {
  if (!contract) return null;
  return (
    <section className="block">
      <Hero
        tone="success"
        icon={BanknotesMIcon}
        confetti
        title="Деньги зачислены"
        text={contract.issuedAt ? `Дата зачисления: ${ruDate(contract.issuedAt)}` : undefined}
        figure={rub(contract.amount)}
        figureCaption="на ваш счёт"
      />
      <Stats
        items={[
          { label: 'Срок', value: `${contract.termMonths} мес.` },
          { label: 'Ставка', value: pct(contract.rate) },
          { label: 'Платёж в месяц', value: rub(contract.monthlyPayment), accent: true },
        ]}
      />
      <BalanceChart contract={contract} />
      <details className="details">
        <summary>График платежей ({contract.schedule.length})</summary>
        <Schedule contract={contract} />
      </details>
    </section>
  );
}

export function CancelledBlock({ onHome }: { onHome: () => void }) {
  return (
    <section className="block">
      <Hero tone="neutral" icon={CrossCircleMIcon} title="Заявка отменена" text="Если передумаете, можно подать новую." />
      <div className="block__actions">
        <Button view="secondary" size={48} onClick={onHome}>К выбору компании</Button>
      </div>
    </section>
  );
}