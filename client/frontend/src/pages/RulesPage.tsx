import { useEffect, useState } from 'react';
import { Text } from '@alfalab/core-components/text';
import { Typography } from '@alfalab/core-components/typography';
import { ApiError, api } from '../api';
import type { FactorCode, RulesFactor, RulesProduct, RulesResponse, RulesRule } from '../types';
import '../App.css';
import './RulesPage.css';

const SECONDARY = 'var(--color-light-text-secondary)';
const MINUS = '\u2212';

const TERMS: Record<string, string> = {
  credit_score: 'кредитный балл',
  delinquencies_12m: 'просрочки за 12 мес.',
  age_months: 'возраст бизнеса, мес.',
  unfiled_reports: 'несданные отчёты',
  pti: 'платёж / выручка',
  limit_share: 'лимит / выручка',
  series: 'есть история выручки',
  no_anomalies: 'резких скачков нет',
  anomaly_explained_accepted: 'объяснение скачка принято',
  only_document: 'выручка подтверждена только договором',
  anomaly_explanation_rejected: 'объяснение скачка отклонено',
  purpose_matches_product: 'цель подходит продукту',
  guarantee_or_pledge: 'есть поручительство или залог',
  director_months: 'директор в должности, мес.',
  'egrul.status': 'статус в ЕГРЮЛ',
  'egrul.address.is_mass': 'адрес массовой регистрации',
  'fns.tax_debt.amount': 'налоговая задолженность',
  'bki.delinquencies.current': 'текущие просрочки в БКИ',
  or: 'или',
  and: 'и',
  '>=': '\u2265',
  '<=': '\u2264',
  '!=': '\u2260',
};
const TERM_RE = new RegExp(
  Object.keys(TERMS).sort((a, b) => b.length - a.length).map((k) => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|'),
  'g',
);

const STOP_TITLES: Record<string, string> = {
  LIQUIDATION: 'Компания ликвидируется',
  YOUNG_BUSINESS: 'Слишком молодой бизнес',
  NEW_DIRECTOR: 'Недавно назначенный директор',
  MASS_ADDRESS: 'Адрес массовой регистрации',
  TAX_DEBT: 'Налоговая задолженность',
  BKI_OVERDUE: 'Текущая просрочка в БКИ',
};

const FACTOR_NAMES: Record<FactorCode, string> = {
  credit_history: 'Кредитная история',
  business_age: 'Возраст бизнеса',
  tax_discipline: 'Налоговая дисциплина',
  debt_load: 'Долговая нагрузка',
  revenue_stability: 'Стабильность выручки',
  purpose: 'Цель кредита',
  collateral: 'Обеспечение',
};

const ruNum = (v: number) => String(v).replace('.', ',');
const pp = (v: number) => (v > 0 ? `+${ruNum(v)}` : v < 0 ? `${MINUS}${ruNum(Math.abs(v))}` : '0');
const rub = (kop: number) => (kop / 100).toLocaleString('ru-RU');

function humanize(condition: string): string {
  return condition
    .replace(TERM_RE, (m) => TERMS[m])
    .replace(/(\d)\.(\d)/g, '$1,$2')
    .replace(/'([^']*)'/g, '«$1»');
}

function policyText(p: RulesProduct) {
  const share = ruNum(Math.round(p.policy.cap * 1000) / 10);
  return p.policy.metric === 'PTI'
    ? `Платёж по кредиту — не больше ${share} % среднемесячной выручки`
    : `Лимит — не больше ${share} % среднемесячной выручки`;
}

function RuleTable({ rules }: { rules: RulesRule[] }) {
  return (
    <table className="rules__table">
      <tbody>
        {rules.map((r, i) => (
          <tr key={i}>
            <td>{r.condition ? humanize(r.condition) : 'во всех остальных случаях'}</td>
            <td className={`rules__val ${r.value > 0 ? 'rules__val--bad' : r.value < 0 ? 'rules__val--good' : ''}`}>
              {pp(r.value)} п.п.
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function Factor({ f, hasOverdraft }: { f: RulesFactor; hasOverdraft: boolean }) {
  return (
    <div className="rules__factor">
      <h3 className="rules__h3">{f.title || FACTOR_NAMES[f.code]}</h3>
      {f.unknown ? (
        <p className="rules__note">
          Пока данных нет — это «белое пятно»: вклад от {pp(f.unknown.best)} до {pp(f.unknown.worst)} п.п. Закрыть пятно
          — сузить вилку на {ruNum(f.unknown.spotPricePp)} п.п.
        </p>
      ) : (
        <p className="rules__note">Данные банк получает сразу, поэтому вклад всегда известен.</p>
      )}
      <RuleTable rules={f.rules} />
      {hasOverdraft && f.overdraftRules.length > 0 && (
        <>
          <p className="rules__note rules__note--sub">Для овердрафта:</p>
          <RuleTable rules={f.overdraftRules} />
        </>
      )}
    </div>
  );
}

export default function RulesPage() {
  const [rules, setRules] = useState<RulesResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .getRules()
      .then(setRules)
      .catch((e) =>
        setError(
          e instanceof ApiError && e.code === 'NETWORK'
            ? 'Нет связи с сервером. Попробуйте ещё раз.'
            : e instanceof ApiError && e.code === 'SOURCE_UNAVAILABLE'
              ? 'Правила скоринга временно недоступны. Попробуйте позже.'
              : 'Не удалось загрузить правила. Попробуйте ещё раз.',
        ),
      );
  }, []);

  if (!rules) {
    return (
      <div className="card">
        <Text tag="p" color={SECONDARY}>{error ?? 'Загружаем правила…'}</Text>
      </div>
    );
  }

  return (
    <div className="card rules">
      <Typography.TitleResponsive tag="h1" view="small" font="system" defaultMargins={false}>
        Как мы считаем ставку
      </Typography.TitleResponsive>
      <Text tag="div" color={SECONDARY} className="card__sub">Правила версии {rules.rulesVersion}</Text>

      <section className="card__section">
        <h2 className="rules__h2">Вилка ставки</h2>
        <p className="rules__p">
          Банк называет не одну цифру, а вилку «от и до». Начинаем с базовой ставки продукта и прибавляем вклад каждого
          фактора. Если фактор известен, у него один вклад. Если неизвестен — диапазон: он и есть «белое пятно».
        </p>
        <ul className="rules__list">
          <li><b>Левый край</b> — если всё неизвестное окажется хорошим.</li>
          <li><b>Правый край</b> — если плохим: так банк считает сейчас.</li>
          <li><b>Цена пятна</b> — на сколько сузится вилка, когда вы его закроете.</li>
        </ul>
        <p className="rules__p">
          Одни и те же данные и одна версия правил всегда дают одну и ту же вилку. Каждое решение можно пересчитать.
        </p>
      </section>

      <section className="card__section">
        <h2 className="rules__h2">Продукты и пороги</h2>
        {rules.products.map((p) => (
          <div key={p.code} className="rules__product">
            <h3 className="rules__h3">{p.name}</h3>
            <dl className="rules__dl">
              <dt>Базовая ставка</dt><dd>{ruNum(p.baseRate)} %</dd>
              <dt>Решение принимается автоматически</dt><dd>правый край вилки не выше {ruNum(p.thresholds.approveMax)} %</dd>
              <dt>Отказ</dt><dd>левый край вилки не ниже {ruNum(p.thresholds.rejectMin)} %</dd>
              <dt>Между порогами</dt><dd>заявку смотрит специалист</dd>
              <dt>Сумма</dt><dd>{rub(p.amount.min)} – {rub(p.amount.max)} ₽</dd>
              <dt>Срок, мес.</dt><dd>{p.terms.join(', ')}</dd>
              <dt>Ограничение</dt><dd>{policyText(p)}</dd>
              {p.skipFactors.length > 0 && (
                <>
                  <dt>Не учитывается</dt>
                  <dd>{p.skipFactors.map((c) => FACTOR_NAMES[c]?.toLowerCase() ?? c).join(', ')}</dd>
                </>
              )}
            </dl>
          </div>
        ))}
      </section>

      <section className="card__section">
        <h2 className="rules__h2">Из чего складывается ставка</h2>
        <p className="rules__p">Вклад в процентных пунктах: минус — ставка ниже, плюс — выше. Срабатывает первое подходящее правило.</p>
        {rules.factors.map((f) => (
          <Factor key={f.code} f={f} hasOverdraft={rules.products.some((p) => p.code === 'OVERDRAFT')} />
        ))}
      </section>

      <section className="card__section">
        <h2 className="rules__h2">Когда заявка не может быть одобрена</h2>
        <p className="rules__p">Это не цена, а запрет: пока условие не выполнено, заявка получит отказ.</p>
        <table className="rules__table">
          <tbody>
            {rules.stopFactors.map((s) => (
              <tr key={s.code}>
                <td>
                  {STOP_TITLES[s.code] ?? s.code}
                  <div className="rules__note">{humanize(s.condition)}</div>
                </td>
                <td className="rules__val">{s.removable ? 'можно устранить' : 'устранить нельзя'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      <section className="card__section">
        <h2 className="rules__h2">Что делает ИИ и чего не делает</h2>
        <p className="rules__p">
          Ставку, вклады, пороги и решение считают правила выше, ИИ в них не участвует. ИИ только читает свободный текст:
          на что нужны деньги и почему изменилась выручка. Из текста он выбирает один из заранее заданных ответов и обязан
          привести цитату. Дальше ответ проверяют правила.
        </p>
        <ul className="rules__list">
          <li>Сначала текст разбирает словарь, ИИ подключается, только если словарь не справился.</li>
          <li>Если ИИ не ответил, ваш ответ уходит специалисту — отказать из-за этого нельзя.</li>
          <li>Окончательное решение по спорным заявкам принимает человек.</li>
        </ul>
        <p className="rules__p rules__note">Расчётная ставка для платежа при проверке нагрузки: {ruNum(rules.calcRatePct)} %.</p>
      </section>
    </div>
  );
}