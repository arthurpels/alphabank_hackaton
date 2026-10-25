import { useState } from 'react';
import { Button } from '@alfalab/core-components/button';
import { Checkbox } from '@alfalab/core-components/checkbox';
import { Input } from '@alfalab/core-components/input';
import { Table } from '@alfalab/core-components/table';
import { Textarea } from '@alfalab/core-components/textarea';
import { CheckmarkCircleMIcon } from '@alfalab/icons-glyph/CheckmarkCircleMIcon';
import { PRODUCT_NAMES } from '../constants';
import { pct, rub } from '../format';
import type {
  ApplicationDetails,
  CloseMethod,
  DecisionRequest,
  DecisionType,
  Offer,
  ProductCode,
  ScoringFactor,
  Spot,
  SpotCreate,
  SpotType,
  UnderwritingSummary,
} from '../types';
import { answerAuthor, modelVerdict, summaryAuthor, WAIT_TEXT } from '../verdictLabels';
import { AuthorBadge } from './AuthorBadge';
import './UnderwriterBlocks.css';

const FACTOR_LABELS: Record<ScoringFactor, string> = {
  debt_load: 'Долговая нагрузка',
  revenue_stability: 'Стабильность выручки',
  purpose: 'Цель кредита',
  collateral: 'Обеспечение',
};
const SPOT_TYPE_LABELS: Partial<Record<SpotType, string>> = {
  NO_DATA: 'Нет данных',
  ANOMALY: 'Аномалия',
  DOCUMENT: 'Документ',
  COLLATERAL: 'Обеспечение',
};
const ADD_CLOSE_METHODS: { code: CloseMethod; label: string }[] = [
  { code: 'ANSWER', label: 'Ответ текстом' },
  { code: 'UPLOAD', label: 'Загрузка документа' },
  { code: 'ADD_COLLATERAL', label: 'Залог или поручительство' },
];

function Chips<T extends string>(props: {
  value: T | null;
  options: { code: T; label: string }[];
  onChange: (v: T) => void;
  label: string;
}) {
  return (
    <div className="chips" role="radiogroup" aria-label={props.label}>
      {props.options.map((o) => (
        <button
          key={o.code}
          type="button"
          role="radio"
          aria-checked={o.code === props.value}
          className={`term ${o.code === props.value ? 'term--selected' : ''}`}
          onClick={() => props.onChange(o.code)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function SummaryBlock(props: { summary: UnderwritingSummary | null; error: string | null; waiting?: boolean; gaveUp?: boolean }) {
  const { summary, error, waiting = false, gaveUp = false } = props;
  return (
    <section className="card__section">
      <h2 className="uw__title">Сводка</h2>
      {summary && <AuthorBadge author={summaryAuthor(summary)} />}
      {waiting && <p className="uw__muted">{WAIT_TEXT.generatingSummary}</p>}
      {gaveUp && <p className="uw__muted">{WAIT_TEXT.summaryGaveUp}</p>}
      {!summary && <p className="uw__muted">{error ?? 'Готовим сводку…'}</p>}
      {summary && (
        <ol className="summary">
          {summary.items.map((it, i) => (
            <li key={i} className="summary__item">
              <p>{it.text}</p>
              {it.facts.length > 0 && (
                <div className="facts">
                  {it.facts.map((f, j) => (
                    <span key={j} className="fact">
                      <b>{f.source}</b> {f.label}
                    </span>
                  ))}
                </div>
              )}
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}

function AnswerView({ spot, onOpenDocument }: { spot: Spot; onOpenDocument: (documentId: string) => void }) {
  const a = spot.lastAnswer;
  if (!a) return null;
  const option = spot.answerOptions?.find((o) => o.code === a.optionCode)?.label ?? a.optionCode;
  const author = answerAuthor(spot, a);
  const model = modelVerdict(spot, a);
  return (
    <div className="answer">
      <div className="answer__label">Ответ клиента</div>
      {option && !a.purposeVerdict && <div className="answer__row">Вариант: {option}</div>}
      {a.text && <div className="answer__row">{a.text}</div>}
      {a.contractAmount != null && (
        <div className="answer__row">
          Договор: {rub(a.contractAmount)}
          {a.contractTermMonths ? ` на ${a.contractTermMonths} мес.` : ''}
        </div>
      )}
      {a.documentId && (
        <div className="answer__row">
          <Button view="text" size={32} onClick={() => onOpenDocument(a.documentId!)}>
            Открыть документ
          </Button>
        </div>
      )}
      {model && (
        <div className="answer__model">
          <div className="answer__label">Вердикт: {model.title}</div>
          {model.evidence && <div className="answer__row">Цитата: «{model.evidence}»</div>}
          {model.reason && <div className="answer__row">Обоснование: {model.reason}</div>}
          {model.violation && <div className="answer__row answer__row--bad">Нарушение: {model.violation}</div>}
        </div>
      )}
      {a.verdict === 'REJECTED' && a.reviewComment && (
        <div className="answer__row answer__row--bad">Не принято: {a.reviewComment}</div>
      )}
      {author && <AuthorBadge author={author} />}
    </div>
  );
}

export function SpotsReview(props: {
  spots: Spot[];
  canReview: boolean;
  busy: string | null;
  onOpenDocument: (documentId: string) => void;
  onReview: (spotId: string, verdict: 'ACCEPTED' | 'REJECTED', comment?: string) => void;
}) {
  const { spots, canReview, busy, onOpenDocument, onReview } = props;
  const [rejecting, setRejecting] = useState<string | null>(null);
  const [comment, setComment] = useState('');
  const [touched, setTouched] = useState(false);

  const active = spots.filter((s) => s.status !== 'CLOSED');
  const closed = spots.filter((s) => s.status === 'CLOSED');

  return (
    <section className="card__section">
      <h2 className="uw__title">Пятна: открыто {active.length}</h2>
      {active.length === 0 && <p className="uw__muted">Открытых пятен нет.</p>}
      {active.map((s) => (
        <div key={s.id} className={`uw-spot ${s.status === 'ON_REVIEW' ? 'uw-spot--review' : ''}`}>
          <div className="uw-spot__head">
            <span className="uw-spot__title">{s.title}</span>
            <span className="uw__badge">
              {s.status === 'ON_REVIEW' ? 'ждёт проверки' : s.addedBy === 'UNDERWRITER' ? 'ждём клиента' : 'открыто'}
            </span>
          </div>
          <div className="uw__muted">{s.clientHint}</div>
          {s.scoringFactor && (
            <div className="uw__muted">
              Фактор: {FACTOR_LABELS[s.scoringFactor as ScoringFactor] ?? s.scoringFactor}
              {s.pricePp > 0 ? ` · до −${String(s.pricePp).replace('.', ',')} п.п.` : ''}
            </div>
          )}
          <AnswerView spot={s} onOpenDocument={onOpenDocument} />

          {canReview && s.status === 'ON_REVIEW' && (
            <>
              {rejecting === s.id ? (
                <div className="reject">
                  <Textarea
                    block
                    label="Что не так с ответом (увидит клиент)"
                    minRows={2}
                    value={comment}
                    error={touched && !comment.trim() ? 'Комментарий обязателен' : undefined}
                    onChange={(_, { value }) => setComment(value)}
                  />
                  <div className="uw__actions">
                    <Button
                      view="secondary"
                      size={40}
                      loading={busy === s.id}
                      disabled={busy !== null}
                      onClick={() => {
                        setTouched(true);
                        if (!comment.trim()) return;
                        onReview(s.id, 'REJECTED', comment.trim());
                        setRejecting(null);
                        setComment('');
                        setTouched(false);
                      }}
                    >
                      Отправить отказ
                    </Button>
                    <Button view="text" size={40} disabled={busy !== null} onClick={() => setRejecting(null)}>
                      Отмена
                    </Button>
                  </div>
                </div>
              ) : (
                <div className="uw__actions">
                  <Button
                    view="accent"
                    size={40}
                    loading={busy === s.id}
                    disabled={busy !== null}
                    onClick={() => onReview(s.id, 'ACCEPTED')}
                  >
                    Принимаю
                  </Button>
                  <Button view="secondary" size={40} disabled={busy !== null} onClick={() => setRejecting(s.id)}>
                    Не принимаю
                  </Button>
                </div>
              )}
            </>
          )}
        </div>
      ))}
      {closed.length > 0 && (
        <details className="uw-closed">
          <summary>Закрытые пятна ({closed.length})</summary>
          <ul>
            {closed.map((s) => (
              <li key={s.id}>
                <div>
                  <CheckmarkCircleMIcon className="uw-closed__check" /> {s.title}
                </div>
                <AnswerView spot={s} onOpenDocument={onOpenDocument} />
              </li>
            ))}
          </ul>
        </details>
      )}
    </section>
  );
}

export function AddSpotForm({ busy, onAdd }: { busy: boolean; onAdd: (body: SpotCreate) => void }) {
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState('');
  const [hint, setHint] = useState('');
  const [type, setType] = useState<SpotType>('DOCUMENT');
  const [method, setMethod] = useState<CloseMethod>('UPLOAD');
  const [factor, setFactor] = useState<ScoringFactor | 'NONE'>('NONE');
  const [touched, setTouched] = useState(false);

  if (!open) {
    return (
      <section className="card__section">
        <Button view="secondary" size={40} onClick={() => setOpen(true)}>
          Запросить у клиента
        </Button>
      </section>
    );
  }

  const submit = () => {
    setTouched(true);
    if (!title.trim() || !hint.trim()) return;
    onAdd({
      type,
      title: title.trim(),
      clientHint: hint.trim(),
      closeMethod: method,
      scoringFactor: factor === 'NONE' ? null : factor,
    });
  };

  return (
    <section className="card__section">
      <h2 className="uw__title">Запросить у клиента</h2>
      <div className="uw-form">
        <Input
          block
          size={56}
          label="Что нужно (название пятна)"
          value={title}
          error={touched && !title.trim() ? 'Укажите название' : undefined}
          onChange={(_, { value }) => setTitle(value)}
        />
        <Textarea
          block
          label="Подсказка клиенту"
          minRows={2}
          value={hint}
          error={touched && !hint.trim() ? 'Объясните клиенту, что приложить' : undefined}
          onChange={(_, { value }) => setHint(value)}
        />
        <div>
          <div className="form__label">Тип пятна</div>
          <Chips
            label="Тип пятна"
            value={type}
            onChange={setType}
            options={(Object.keys(SPOT_TYPE_LABELS) as SpotType[]).map((c) => ({ code: c, label: SPOT_TYPE_LABELS[c]! }))}
          />
        </div>
        <div>
          <div className="form__label">Как клиент закроет</div>
          <Chips label="Способ закрытия" value={method} onChange={setMethod} options={ADD_CLOSE_METHODS} />
        </div>
        <div>
          <div className="form__label">На какой фактор влияет (стоп-фактор добавить нельзя)</div>
          <Chips
            label="Фактор скоринга"
            value={factor}
            onChange={setFactor}
            options={[
              { code: 'NONE' as const, label: 'Не влияет на вилку' },
              ...(Object.keys(FACTOR_LABELS) as ScoringFactor[]).map((c) => ({ code: c, label: FACTOR_LABELS[c] })),
            ]}
          />
        </div>
        <div className="uw__actions">
          <Button view="accent" size={40} loading={busy} onClick={submit}>
            Отправить клиенту
          </Button>
          <Button view="text" size={40} disabled={busy} onClick={() => setOpen(false)}>
            Отмена
          </Button>
        </div>
      </div>
    </section>
  );
}

const rateText = (o: Offer) =>
  o.fork.rateFrom === o.fork.rateTo ? pct(o.fork.rateFrom) : `${pct(o.fork.rateFrom)} – ${pct(o.fork.rateTo)}`;

export function OffersTable(props: {
  offers: Offer[];
  selectable: boolean;
  selected: string[];
  onToggle: (id: string) => void;
}) {
  const { offers, selectable, selected, onToggle } = props;
  if (offers.length === 0) return <p className="uw__muted">Проходящих вариантов нет.</p>;
  return (
    <Table wrapper>
      <Table.THead>
        <Table.THeadCell title="Выбор" width={56}>{selectable ? '' : ' '}</Table.THeadCell>
        <Table.THeadCell title="Продукт">Продукт</Table.THeadCell>
        <Table.THeadCell title="Сумма">Сумма</Table.THeadCell>
        <Table.THeadCell title="Срок">Срок</Table.THeadCell>
        <Table.THeadCell title="Ставка">Ставка</Table.THeadCell>
        <Table.THeadCell title="Пометка">Пометка</Table.THeadCell>
      </Table.THead>
      <Table.TBody>
        {offers.map((o) => (
          <Table.TRow key={o.id} selected={selected.includes(o.id)}>
            <Table.TCell>
              {selectable && o.status === 'CALCULATED' ? (
                <Checkbox
                  checked={selected.includes(o.id)}
                  onChange={() => onToggle(o.id)}
                  aria-label="Предложить клиенту"
                />
              ) : (
                ' '
              )}
            </Table.TCell>
            <Table.TCell>{PRODUCT_NAMES[o.productCode]}</Table.TCell>
            <Table.TCell><div className="queue-num">{rub(o.amount)}</div></Table.TCell>
            <Table.TCell><div className="queue-num">{o.termMonths} мес.</div></Table.TCell>
            <Table.TCell><div className="queue-num queue-main">{rateText(o)}</div></Table.TCell>
            <Table.TCell>
              {[o.isRequested ? 'Запрошено' : null, o.isManual ? 'Вручную' : null, o.status === 'OFFERED' ? 'Предложено' : null]
                .filter(Boolean)
                .join(', ') || ' '}
            </Table.TCell>
          </Table.TRow>
        ))}
      </Table.TBody>
    </Table>
  );
}

const DECISIONS: { code: DecisionType; label: string }[] = [
  { code: 'APPROVED', label: 'Одобрить запрошенные' },
  { code: 'ALTERNATIVE', label: 'Предложить иные условия' },
  { code: 'REJECTED', label: 'Отказать' },
];
const PRODUCT_OPTIONS = (Object.keys(PRODUCT_NAMES) as ProductCode[]).map((c) => ({ code: c, label: PRODUCT_NAMES[c] }));

export function DecisionForm(props: {
  data: ApplicationDetails;
  busy: boolean;
  onDecide: (body: DecisionRequest) => void;
}) {
  const { data, busy, onDecide } = props;
  const calculated = data.offers.filter((o) => o.status === 'CALCULATED');
  const [type, setType] = useState<DecisionType | null>(null);
  const [selected, setSelected] = useState<string[]>([]);
  const [manual, setManual] = useState(false);
  const [mProduct, setMProduct] = useState<ProductCode>(data.application.product.code ?? 'EQUIPMENT');
  const [mAmount, setMAmount] = useState('');
  const [mTerm, setMTerm] = useState('');
  const [reason, setReason] = useState<string | null>(null);
  const [comment, setComment] = useState('');
  const [touched, setTouched] = useState(false);

  const toggle = (id: string) =>
    setSelected((cur) => (cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id]));

  const errors: string[] = [];
  const count = selected.length + (manual ? 1 : 0);
  if (type === 'ALTERNATIVE') {
    if (count < 1 || count > 3) errors.push('Отметьте от 1 до 3 вариантов');
    if (manual && (!Number(mAmount) || !Number(mTerm))) errors.push('Укажите сумму и срок своего варианта');
    if (manual && !comment.trim()) errors.push('Для своего варианта нужен комментарий');
  }
  if (type === 'REJECTED') {
    if (!reason) errors.push('Выберите пятно-причину отказа');
    if (!comment.trim()) errors.push('При отказе нужен комментарий');
  }

  const submit = () => {
    setTouched(true);
    if (!type || errors.length > 0) return;
    const body: DecisionRequest = { type };
    if (comment.trim()) body.comment = comment.trim();
    if (type === 'ALTERNATIVE') {
      if (selected.length > 0) body.offerIds = selected;
      if (manual) {
        body.manualOffer = { productCode: mProduct, amount: Number(mAmount) * 100, termMonths: Number(mTerm) };
        body.override = true;
      }
    }
    if (type === 'REJECTED' && reason) body.reasonSpotId = reason;
    onDecide(body);
  };

  return (
    <section className="card__section">
      <h2 className="uw__title">Варианты условий</h2>
      <OffersTable
        offers={data.offers}
        selectable={type === 'ALTERNATIVE'}
        selected={selected}
        onToggle={toggle}
      />

      <h2 className="uw__title uw__title--gap">Решение</h2>
      <Chips label="Решение" value={type} options={DECISIONS} onChange={setType} />

      {type === 'ALTERNATIVE' && (
        <div className="uw-form">
          <p className="uw__muted">
            Отметьте 1–3 варианта в таблице выше{calculated.length === 0 ? ' (подходящих нет — задайте свой)' : ''}.
            Выбрано: {count}.
          </p>
          <Checkbox checked={manual} label="Задать свой вариант" onChange={(_, { checked }) => setManual(checked)} />
          {manual && (
            <>
              <Chips label="Продукт" value={mProduct} options={PRODUCT_OPTIONS} onChange={setMProduct} />
              <div className="uw-form__row">
                <Input
                  block
                  label="Сумма, ₽"
                  inputMode="numeric"
                  value={mAmount}
                  onChange={(_, { value }) => setMAmount(value.replace(/\D/g, ''))}
                />
                <Input
                  block
                  label="Срок, мес."
                  inputMode="numeric"
                  value={mTerm}
                  onChange={(_, { value }) => setMTerm(value.replace(/\D/g, ''))}
                />
              </div>
            </>
          )}
        </div>
      )}

      {type === 'REJECTED' && (
        <div className="uw-form">
          <div className="form__label">Пятно-причина отказа</div>
          <div className="reasons">
            {data.spots.map((s) => (
              <label key={s.id} className={`reason ${reason === s.id ? 'reason--selected' : ''}`}>
                <input type="radio" name="reason" checked={reason === s.id} onChange={() => setReason(s.id)} />
                <span>{s.title}</span>
              </label>
            ))}
          </div>
        </div>
      )}

      {type && (
        <div className="uw-form">
          <Textarea
            block
            label={type === 'APPROVED' ? 'Комментарий (по желанию)' : 'Комментарий'}
            minRows={2}
            value={comment}
            onChange={(_, { value }) => setComment(value)}
          />
          {touched && errors.length > 0 && (
            <div className="notice notice--bad">
              {errors.map((e) => (
                <div key={e}>{e}</div>
              ))}
            </div>
          )}
          <div className="uw__actions">
            <Button view="accent" size={48} loading={busy} onClick={submit}>
              Вынести решение
            </Button>
          </div>
        </div>
      )}
    </section>
  );
}