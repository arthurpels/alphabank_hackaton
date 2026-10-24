import { forwardRef, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Checkbox } from '@alfalab/core-components/checkbox';
import { Input, type InputProps } from '@alfalab/core-components/input';
import { SliderInput } from '@alfalab/core-components/slider-input';
import { Text } from '@alfalab/core-components/text';
import { Textarea } from '@alfalab/core-components/textarea';
import { Typography } from '@alfalab/core-components/typography';
import { ApiError, api } from '../api';
import { rub, ruDate } from '../format';
import type { DemoUser, Product, ProductCode } from '../types';
import '../App.css';

const SECONDARY = 'var(--color-light-text-secondary)';
const AMOUNT_STEP = 50000;
const GOAL_MAX = 1000;

const SpacedInput = forwardRef<HTMLInputElement, InputProps>(({ value, ...rest }, ref) => (
  <Input
    {...rest}
    ref={ref}
    value={String(value ?? '')
      .replace(/\D/g, '')
      .replace(/\B(?=(\d{3})+(?!\d))/g, ' ')}
  />
));
SpacedInput.displayName = 'SpacedInput';

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'NETWORK') return 'Нет связи с сервером. Попробуйте ещё раз.';
    if (e.code === 'UNAUTHORIZED') return 'Сначала выберите компанию на стартовом экране.';
    if (e.code === 'SOURCE_UNAVAILABLE') return 'Источник данных временно недоступен. Попробуйте позже.';
    return e.message;
  }
  return 'Что-то пошло не так. Попробуйте ещё раз.';
}

export default function NewApplicationPage() {
  const navigate = useNavigate();
  const [me, setMe] = useState<DemoUser | null>(null);
  const [products, setProducts] = useState<Product[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const [productCode, setProductCode] = useState<ProductCode | null>(null);
  const [amountRub, setAmountRub] = useState<number | ''>('');
  const [term, setTerm] = useState<number | null>(null);
  const [goal, setGoal] = useState('');
  const [bki, setBki] = useState(false);
  const [personalData, setPersonalData] = useState(false);
  const [touched, setTouched] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const createdId = useRef<string | null>(null);

  useEffect(() => {
    Promise.all([api.getMe(), api.listProducts()])
      .then(([m, p]) => {
        setMe(m);
        setProducts(p);
        if (p[0]) {
          setProductCode(p[0].code);
          setAmountRub(p[0].minAmount / 100);
          setTerm(p[0].allowedTerms[p[0].allowedTerms.length - 1] ?? null);
        }
      })
      .catch((e) => setLoadError(errorText(e)));
  }, []);

  if (!me || !products) {
    return (
      <div className="card">
        <Text tag="p" color={SECONDARY}>{loadError ?? 'Загружаем…'}</Text>
      </div>
    );
  }

  const product = products.find((p) => p.code === productCode) ?? null;
  const company = me.company;
  const amountKop = (amountRub || 0) * 100;

  let amountError: string | null = null;
  if (product) {
    if (!amountKop) amountError = 'Укажите сумму';
    else if (amountKop < product.minAmount) amountError = `Не меньше ${rub(product.minAmount)}`;
    else if (amountKop > product.maxAmount) amountError = `Не больше ${rub(product.maxAmount)}`;
  }
  const goalError = goal.trim() ? null : 'Опишите, на что нужны деньги';
  const consentsOk = bki && personalData;
  const valid = !!product && term !== null && !amountError && !goalError && consentsOk;

  const pickProduct = (p: Product) => {
    setProductCode(p.code);
    if (amountKop < p.minAmount || amountKop > p.maxAmount) setAmountRub(p.minAmount / 100);
    if (term === null || !p.allowedTerms.includes(term)) setTerm(p.allowedTerms[p.allowedTerms.length - 1] ?? null);
  };

  const submit = async () => {
    setTouched(true);
    if (!valid || !product || term === null) return;
    setBusy(true);
    setError(null);
    try {
      if (!createdId.current) {
        const created = await api.createApplication({
          productCode: product.code,
          amount: amountKop,
          termMonths: term,
          goal: goal.trim(),
          consents: { bki, personalData },
        });
        createdId.current = created.application.id;
      }
      await api.evaluateApplication(createdId.current);
      navigate(`/client/application?id=${createdId.current}`);
    } catch (e) {
      setError(errorText(e));
      setBusy(false);
    }
  };

  const companyRows: [string, string | null | undefined][] = [
    ['Название', company?.name],
    ['ИНН', company?.inn],
    ['Форма', company ? (company.legalForm === 'IP' ? 'Индивидуальный предприниматель' : 'Общество с ограниченной ответственностью') : null],
    ['Адрес', company?.address],
    ['Руководитель', company?.directorFio],
    ['Дата регистрации', company?.registeredAt ? ruDate(company.registeredAt) : null],
  ];

  return (
    <div className="card">
      <header className="card__head">
        <div>
          <Typography.TitleResponsive tag="h1" view="small" font="system" defaultMargins={false}>
            Новая заявка
          </Typography.TitleResponsive>
          <Text tag="div" color={SECONDARY} className="card__sub">
            Подберём ставку и покажем, что на неё влияет
          </Text>
        </div>
      </header>

      <section className="card__section">
        <h2 className="form__title">Компания</h2>
        <dl className="company">
          {companyRows
            .filter(([, v]) => v)
            .map(([label, value]) => (
              <div key={label} className="company__row">
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
        </dl>
        <p className="form__hint">
          Данные берутся из выбранной компании, изменить их здесь нельзя.
        </p>
      </section>

      <section className="card__section">
        <h2 className="form__title">Что нужно</h2>
        <div className="products" role="radiogroup" aria-label="Продукт">
          {products.map((p) => (
            <button
              key={p.code}
              type="button"
              role="radio"
              aria-checked={p.code === productCode}
              className={`product ${p.code === productCode ? 'product--selected' : ''}`}
              onClick={() => pickProduct(p)}
            >
              <span className="product__radio" />
              <span className="product__body">
                <span className="product__name">{p.name}</span>
                <span className="product__range">
                  от {rub(p.minAmount)} до {rub(p.maxAmount)}
                </span>
              </span>
            </button>
          ))}
        </div>

        <div className="form__field">
          <SliderInput
            block
            size={56}
            label="Сумма, ₽"
            Input={SpacedInput}
            min={product ? product.minAmount / 100 : 0}
            max={product ? product.maxAmount / 100 : 0}
            step={AMOUNT_STEP}
            value={amountRub}
            steps={product ? [rub(product.minAmount), rub(product.maxAmount)] : undefined}
            error={touched && amountError ? amountError : undefined}
            onChange={(_, { value }) => setAmountRub(value)}
          />
        </div>

        <div className="form__field">
          <div className="form__label">Срок</div>
          <div className="terms" role="radiogroup" aria-label="Срок">
            {product?.allowedTerms.map((t) => (
              <button
                key={t}
                type="button"
                role="radio"
                aria-checked={t === term}
                className={`term ${t === term ? 'term--selected' : ''}`}
                onClick={() => setTerm(t)}
              >
                {t} мес.
              </button>
            ))}
          </div>
        </div>

        <div className="form__field">
          <Textarea
            block
            label="Цель кредита"
            minRows={3}
            maxLength={GOAL_MAX}
            value={goal}
            error={touched && goalError ? goalError : undefined}
            hint={`${goal.length} из ${GOAL_MAX}`}
            onChange={(_, { value }) => setGoal(value)}
          />
        </div>
      </section>

      <section className="card__section">
        <h2 className="form__title">Согласия</h2>
        <div className="consents">
          <Checkbox
            block
            checked={bki}
            label="Согласен на запрос кредитной истории в БКИ"
            onChange={(_, { checked }) => setBki(checked)}
          />
          <Checkbox
            block
            checked={personalData}
            label="Согласен на обработку персональных данных"
            onChange={(_, { checked }) => setPersonalData(checked)}
          />
        </div>
        {touched && !consentsOk && <div className="form__error">Без обоих согласий заявку подать нельзя</div>}
      </section>

      {error && (
        <section className="card__section">
          <div className="notice notice--bad">{error}</div>
        </section>
      )}

      <footer className="card__footer">
        <Button view="accent" size={48} loading={busy} onClick={submit}>
          Рассчитать
        </Button>
      </footer>
    </div>
  );
}