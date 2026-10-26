import { coffeeShop } from '../mocks/coffeeShop';
import { demoUsers } from '../mocks/demoUsers';
import { products } from '../mocks/products';
import { mockRules } from '../mocks/rules';
import type {
  ApplicationAction,
  ApplicationDetails,
  ApplicationStatus,
  Contract,
  Offer,
  PurposeVerdict,
  Spot,
  SpotCloseRequest,
  AnomalyCause,
  AnomalyVerdict,
} from '../types';
import { ApiError, getCurrentUser } from './http';
import { underwriterMock } from './mockUnderwriter';
import type { Api } from './index';

const STEP_MS = 1500;

const actionsFor = (status: ApplicationStatus): ApplicationAction[] => {
  switch (status) {
    case 'DRAFT': return ['EDIT', 'EVALUATE', 'CANCEL'];
    case 'COLLECTING': return ['EDIT', 'CLOSE_SPOT', 'SUBMIT', 'CANCEL'];
    case 'INFO_REQUESTED': return ['CLOSE_SPOT', 'CANCEL'];
    case 'APPROVED':
    case 'COUNTER_OFFER': return ['ACCEPT', 'CANCEL'];
    case 'SIGNING': return ['SIGN'];
    default: return [];
  }
};

let state: ApplicationDetails = structuredClone(coffeeShop);
let enteredAt = Date.now();
let createdAt: string | null = null;
let frozen = false;

function setStatus(status: ApplicationStatus, patch: Partial<ApplicationDetails> = {}) {
  state = {
    ...state,
    ...patch,
    application: { ...state.application, status, availableActions: actionsFor(status) },
  };
  enteredAt = Date.now();
}

const FACTOR_SPOT: Record<string, string> = { debt_load: 's1', revenue_stability: 's2', purpose: 's3', collateral: 's4' };

function recalc(d: ApplicationDetails): ApplicationDetails {
  if (!d.fork) return d;
  let from = coffeeShop.fork!.rateFrom;
  let to = coffeeShop.fork!.rateTo;
  for (const s of d.spots) {
    if (s.status === 'CLOSED') {
      to -= s.pricePp * 0.87;
      from += s.pricePp * 0.13;
    }
  }
  const { approveMax, rejectMin } = d.fork.thresholds;
  const round = (v: number) => Math.round(v * 10) / 10;
  const rateFrom = round(from);
  const rateTo = round(Math.max(from, to));
  const zone = rateFrom >= rejectMin ? 'REJECT' : rateTo <= approveMax ? 'APPROVE' : 'UNDERWRITER';
  const factors = coffeeShop.fork!.factors?.map((f) => {
    const spot = d.spots.find((s) => s.id === FACTOR_SPOT[f.code]);
    if (f.known || !spot || spot.status !== 'CLOSED') return f;
    const value = round(f.best! + spot.pricePp * 0.13);
    return { ...f, known: true, value, best: null, worst: null, spotPricePp: null };
  });
  return { ...d, fork: { ...d.fork, rateFrom, rateTo, zone, factors } };
}

const pad = (n: number) => String(n).padStart(2, '0');

function buildContract(offer: Offer): Contract {
  const rate = offer.fork.rateTo;
  const n = offer.termMonths;
  const r = rate / 100 / 12;
  const monthly =
    r === 0 ? Math.round(offer.amount / n) : Math.round((offer.amount * r) / (1 - Math.pow(1 + r, -n)));
  let balance = offer.amount;
  const now = new Date();
  const schedule = [];
  for (let i = 1; i <= n; i++) {
    const interest = Math.round(balance * r);
    const principal = i === n ? balance : monthly - interest;
    balance -= principal;
    const d = new Date(now.getFullYear(), now.getMonth() + i, now.getDate());
    schedule.push({
      number: i,
      date: `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`,
      payment: principal + interest,
      principal,
      interest,
      balance,
    });
  }
  return { offerId: offer.id, amount: offer.amount, termMonths: n, rate, monthlyPayment: monthly, schedule };
}

const nowIso = () => new Date().toISOString();

function requestedOffer(): Offer {
  const { application, fork } = state;
  return {
    id: 'offer-requested',
    productCode: 'EQUIPMENT',
    amount: application.amount,
    termMonths: application.termMonths,
    fork: fork!,
    proposedBy: 'SYSTEM',
    status: 'OFFERED',
    isRequested: true,
    isManual: false,
  };
}

function approve() {
  setStatus('APPROVED', {
    offers: [requestedOffer()],
    decision: { id: 'decision-1', type: 'APPROVED', decidedBy: 'SYSTEM', override: false, decidedAt: nowIso() },
  });
}

function reject() {
  const reason = state.spots.find((s) => s.status !== 'CLOSED') ?? state.spots[0];
  setStatus('REJECTED', {
    decision: {
      id: 'decision-1',
      type: 'REJECTED',
      decidedBy: 'SYSTEM',
      override: false,
      reasonSpotId: reason?.id ?? null,
      comment: 'Даже в лучшем случае ставка выше допустимой.',
      decidedAt: nowIso(),
    },
  });
}

function counterOffers() {
  const fork = (rate: number) => ({ ...state.fork!, rateFrom: rate, rateTo: rate, zone: 'APPROVE' as const });
  setStatus('COUNTER_OFFER', {
    offers: [
      { id: 'offer-a', productCode: 'EQUIPMENT', amount: 150000000, termMonths: 36, fork: fork(19.5), proposedBy: 'SYSTEM', status: 'OFFERED', isRequested: false, isManual: false },
      { id: 'offer-b', productCode: 'WORKING_CAPITAL', amount: 120000000, termMonths: 36, fork: fork(20.5), proposedBy: 'SYSTEM', status: 'OFFERED', isRequested: false, isManual: false },
    ],
    decision: { id: 'decision-1', type: 'ALTERNATIVE', decidedBy: 'SYSTEM', override: false, decidedAt: nowIso() },
  });
}

function accept(offerId: string) {
  const offers = state.offers.map((o) => (o.id === offerId ? { ...o, status: 'CHOSEN' as const } : o));
  const chosen = offers.find((o) => o.id === offerId) ?? offers[0];
  setStatus('SIGNING', { offers, contract: buildContract(chosen) });
}

function sign() {
  const at = nowIso();
  setStatus('ISSUED', { contract: { ...state.contract!, signedAt: at, issuedAt: at } });
}

function advance() {
  if (frozen || Date.now() - enteredAt < STEP_MS) return;
  if (state.application.status === 'SUBMITTED') {
    setStatus('AUTO_CHECK');
  } else if (state.application.status === 'AUTO_CHECK') {
    const zone = state.fork?.zone;
    if (zone === 'APPROVE') approve();
    else if (zone === 'REJECT') reject();
    else setStatus('UNDERWRITING');
  }
}

function applyPreset(status: ApplicationStatus) {
  const allClosed = recalc({
    ...structuredClone(coffeeShop),
    spots: coffeeShop.spots.map((s) => ({ ...s, status: 'CLOSED' as const })),
  });
  frozen = true;
  state = structuredClone(coffeeShop);
  switch (status) {
    case 'DRAFT':
      state = { ...state, fork: null, spots: [] };
      break;
      case 'INFO_REQUESTED': {
      const deadline = new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString();
      state = { ...state, application: { ...state.application, infoRequestDeadline: deadline } };
      break;
    }
    case 'APPROVED':
    case 'SIGNING':
    case 'ISSUED':
      state = allClosed;
      approve();
      if (status !== 'APPROVED') accept('offer-requested');
      if (status === 'ISSUED') sign();
      return;
    case 'REJECTED':
      state = { ...state, fork: { ...state.fork!, rateFrom: 24.5, rateTo: 26, zone: 'REJECT' } };
      reject();
      return;
    case 'COUNTER_OFFER':
      counterOffers();
      return;
  }
  setStatus(status);
}

let lastPreset: string | null = null;
function checkPreset() {
  const wanted = new URLSearchParams(window.location.search).get('mockStatus');
  if (wanted && wanted !== lastPreset) applyPreset(wanted as ApplicationStatus);
  lastPreset = wanted;
}

function checkCloseBody(b: SpotCloseRequest): string | null {
  if (b.method === 'CONNECT_SOURCE' && (!b.sourceCode || !b.consent)) return 'Нужно согласие на подключение источника';
  if (b.method === 'ANSWER' && !b.optionCode && !b.text?.trim()) return 'Выберите вариант или напишите ответ';
  if (b.method === 'UPLOAD' && !b.documentId) return 'Сначала загрузите документ';
  if (b.method === 'ADD_COLLATERAL' && !b.collateralType) return 'Выберите вариант обеспечения';
  return null;
}

const MOCK_LLM = {
  model: 'qwen3:4b',
  digest: '359d7dd4bcdab3d86b87d73ac27966f4dbb9f5efdfcc75d34a8764a09474fae7',
  promptVersion: 'bba46b2556f0',
  cached: false,
};
const PURPOSE_WORDS = ['кофемашин', 'оборудован', 'станок', 'техник', 'витрин', 'холодильник', 'принтер'];
const PROFILE_MISMATCH_WORDS = ['трактор', 'экскаватор', 'бульдозер'];
const LLM_DELAY_MS = 1200;

const llmIsOff = () => new URLSearchParams(window.location.search).get('mockLlm') === 'off';

function judgePurpose(text: string): PurposeVerdict {
  const goal = text.toLowerCase().replace(/ё/g, 'е');
  const matched = PURPOSE_WORDS.filter((w) => goal.includes(w));
  const base = { productCode: 'EQUIPMENT' as const, matchedKeywords: matched };
  if (matched.length > 0) {
    return { ...base, status: 'DECIDED', purpose: 'MATCH', generatedBy: 'KEYWORDS', reason: null, llm: null };
  }
  if (llmIsOff()) {
    return { ...base, status: 'UNAVAILABLE', purpose: null, generatedBy: 'KEYWORDS', reason: null, llm: null };
  }
  if (goal.includes('отпуск')) {
    return {
      ...base,
      status: 'DECIDED',
      purpose: 'MISMATCH',
      generatedBy: 'LLM',
      reason: 'Отпуск сотрудников — не покупка оборудования.',
      evidence: 'отпуск',
      violation: null,
      llm: MOCK_LLM,
    };
  }
  if (goal.trim().length < 25 || goal.includes('развитие')) {
    return {
      ...base,
      status: 'UNCLEAR',
      purpose: null,
      generatedBy: 'LLM',
      reason: 'Формулировка слишком общая: не названо, что именно покупаете.',
      llm: MOCK_LLM,
    };
  }
  const odd = PROFILE_MISMATCH_WORDS.find((w) => goal.includes(w));
  const okved = { okved: '56.30', activity: 'ОКВЭД 56.30 — Деятельность по предоставлению продуктов питания и напитков' };
  return {
    ...base,
    status: 'DECIDED',
    purpose: 'MATCH',
    generatedBy: 'LLM',
    reason: 'В тексте назван предмет покупки, он подходит под выбранный продукт.',
    llm: MOCK_LLM,
    profile: odd
      ? { ...okved, status: 'DOES_NOT_FIT', reason: 'Покупка не связана с общепитом.', evidence: odd, violation: null, llm: MOCK_LLM }
      : { ...okved, status: 'FITS', reason: null, evidence: null, violation: null, llm: MOCK_LLM },
  };
}

const ANOMALY_WORDS: Record<Exclude<AnomalyCause, 'OTHER'>, string[]> = {
  RENOVATION: ['ремонт', 'закрыл', 'закрыт'],
  SEASONAL: ['сезон', 'зима', 'лето'],
  CLIENT_LOST: ['клиент', 'ушел', 'потеряли'],
};
const MOCK_ANOMALY = { code: 'REVENUE_DROP' as const, month: '2026-03', changePct: -45, source: 'OFD' as const };

function judgeAnomaly(optionCode: string | undefined, text: string): AnomalyVerdict {
  let code: AnomalyCause | null;
  let classification: AnomalyVerdict['classification'] = null;
  if (optionCode) {
    code = optionCode as AnomalyCause;
  } else {
    const t = text.toLowerCase().replace(/ё/g, 'е');
    const hit = (Object.keys(ANOMALY_WORDS) as Array<keyof typeof ANOMALY_WORDS>).find((k) =>
      ANOMALY_WORDS[k].some((w) => t.includes(w)),
    );
    const matched = hit ? ANOMALY_WORDS[hit].filter((w) => t.includes(w)) : [];
    if (t.includes('одобр') || t.includes('ставк')) {
      code = 'OTHER';
      classification = { status: 'DECIDED', code, generatedBy: 'LLM', matchedKeywords: [], reason: null, violation: 'MANIPULATION', llm: MOCK_LLM };
    } else if (hit) {
      code = hit;
      classification = { status: 'DECIDED', code, generatedBy: 'KEYWORDS', matchedKeywords: matched };
    } else if (llmIsOff()) {
      code = null;
      classification = { status: 'UNAVAILABLE', code: null, generatedBy: 'KEYWORDS', matchedKeywords: [], reason: 'ИИ временно недоступна' };
    } else {
      code = 'OTHER';
      classification = { status: 'DECIDED', code, generatedBy: 'LLM', matchedKeywords: [], reason: 'В тексте нет понятной причины падения выручки.', llm: MOCK_LLM };
    }
  }
  let check: AnomalyVerdict['check'] = null;
  if (code === 'RENOVATION') {
    check = { verdict: 'ACCEPTED', rule: 'RENOVATION_ONE_OFF', explanation: 'Падение разовое: в апреле выручка вернулась к 98 % от уровня до падения.' };
  } else if (code === 'SEASONAL') {
    check = { verdict: 'REVIEW', rule: null, explanation: 'В прошлом году в марте такого спада не было, поэтому ответ посмотрит специалист.' };
  } else if (code) {
    check = { verdict: 'REVIEW', rule: null, explanation: 'Эту причину нельзя проверить данными: ответ посмотрит специалист банка.' };
  }
  return { anomaly: MOCK_ANOMALY, code, classification, check };
}

const ANOMALY_SPOT: Spot = {
  id: 's5',
  type: 'ANOMALY',
  title: 'Почему в марте выручка упала на 45 %',
  clientHint: 'Пока нет объяснения — считаем это риском. Что случилось?',
  scoringFactor: 'revenue_stability',
  pricePp: 2.5,
  closeMethod: 'ANSWER',
  status: 'OPEN',
  addedBy: 'SYSTEM',
  source: 'OFD',
  answerOptions: [
    { code: 'RENOVATION', label: 'Был ремонт' },
    { code: 'SEASONAL', label: 'Сезонный спад' },
    { code: 'CLIENT_LOST', label: 'Ушёл крупный клиент' },
  ],
};

const wait = <T>(v: T) => new Promise<T>((r) => setTimeout(() => r(structuredClone(v)), 150));

export const mockApi: Api = {
  listDemoUsers: (role) => wait(role ? demoUsers.filter((u) => u.role === role) : demoUsers),
  getMe: () => wait(demoUsers.find((u) => u.id === getCurrentUser()) ?? demoUsers[0]),
  listProducts: () => wait(products),
  getRules: () => wait(mockRules),
  listApplications: () => {
    checkPreset();
    if (!createdAt && !frozen) return wait([]);
    const { application } = state;
    const code = products.find((p) => p.name === application.product.name)?.code ?? 'EQUIPMENT';
    return wait([
      {
        id: application.id,
        status: application.status,
        productCode: code,
        amount: application.amount,
        termMonths: application.termMonths,
        fork: state.fork,
        createdAt: createdAt ?? nowIso(),
      },
    ]);
  },
  ...underwriterMock,

    getApplication: (id) => {
        if (underwriterMock.isBuilder(id)) return underwriterMock.getBuilder();
        checkPreset();
    advance();
    return wait(state);
  },

  createApplication: (body) => {
    frozen = false;
    const product = products.find((p) => p.code === body.productCode);
    state = { ...structuredClone(coffeeShop), fork: null, spots: [] };
    state.application = {
      ...state.application,
      product: { name: product?.name ?? state.application.product.name },
      amount: body.amount,
      termMonths: body.termMonths,
      goal: body.goal,
    };
    setStatus('DRAFT');
    createdAt = nowIso();
    return wait(state);
  },

  evaluateApplication: () => {
    frozen = false;
    const { product, amount, termMonths } = state.application;
    state = structuredClone(coffeeShop);
    state.application = { ...state.application, product, amount, termMonths };
    setStatus('COLLECTING');
    return wait(state);
  },
  getChecks: () =>
    wait({
      checkedAt: nowIso(),
      items: [
        {
          source: 'EGRUL' as const,
          status: 'OK' as const,
          facts: ['ООО «Кофейня», ИНН 7701234560', 'статус: действует', 'компания зарегистрирована 15.04.2019',
            'руководитель Смирнова А. В. с 15.04.2019', 'адрес: г. Москва, ул. Покровка, 12'],
          problems: [],
        },
        { source: 'FNS' as const, status: 'OK' as const, facts: ['долга перед ФНС нет', 'несданных отчётов нет'], problems: [] },
        {
          source: 'BKI' as const,
          status: 'OK' as const,
          facts: ['кредитный рейтинг 712, просрочек за 12 мес. нет', 'текущих просрочек нет'],
          problems: [],
        },
      ],
    }),

  closeSpot: (_appId, spotId, body) => {
    const spot = state.spots.find((s) => s.id === spotId);
    if (!spot) return Promise.reject(new ApiError(404, 'NOT_FOUND', 'Пятно не найдено'));
    if (spot.status === 'CLOSED') return Promise.reject(new ApiError(409, 'INVALID_TRANSITION', 'Пятно уже закрыто'));
    const bad = checkCloseBody(body);
    if (bad) return Promise.reject(new ApiError(400, 'VALIDATION_ERROR', bad));

    let status: Spot['status'] = 'CLOSED';
    let lastAnswer: Spot['lastAnswer'] = null;
    const answer = { id: `a-${spotId}`, optionCode: body.optionCode, text: body.text, documentId: body.documentId, contractAmount: body.contractAmount, contractTermMonths: body.contractTermMonths, createdAt: new Date().toISOString() };
    let delay = 150;
    if (spot.type === 'ANOMALY' && body.method === 'ANSWER') {
      const verdict = judgeAnomaly(body.optionCode, body.text ?? '');
      if (verdict.classification?.generatedBy === 'LLM') delay = LLM_DELAY_MS;
      if (verdict.check?.verdict === 'ACCEPTED') {
        lastAnswer = { ...answer, verdict: 'ACCEPTED', reviewedBy: 'SYSTEM', anomalyVerdict: verdict };
      } else {
        status = 'ON_REVIEW';
        lastAnswer = { ...answer, verdict: 'PENDING', anomalyVerdict: verdict };
      }
    } else if ((spot.scoringFactor === 'purpose' || spot.id === 's3') && body.method === 'ANSWER') {
      const verdict = judgePurpose(body.text ?? body.optionCode ?? '');
      if (verdict.generatedBy === 'LLM') delay = LLM_DELAY_MS;
      if (verdict.status === 'UNAVAILABLE' || verdict.purpose === 'MISMATCH' || verdict.profile?.status === 'DOES_NOT_FIT') {
        status = 'ON_REVIEW';
        lastAnswer = { ...answer, verdict: 'PENDING', purposeVerdict: verdict };
      } else if (verdict.status === 'UNCLEAR') {
        status = 'OPEN';
        lastAnswer = {
          ...answer,
          verdict: 'REJECTED',
          reviewedBy: 'SYSTEM',
          reviewComment: 'Не удалось понять цель — напишите подробнее, что именно покупаете',
          purposeVerdict: verdict,
        };
      } else {
        lastAnswer = { ...answer, verdict: 'ACCEPTED', reviewedBy: 'SYSTEM', purposeVerdict: verdict };
      }
    } else if (body.method === 'UPLOAD' || (body.method === 'ANSWER' && spot.answerOptions?.length) || (body.method === 'ADD_COLLATERAL' && body.collateralType === 'PLEDGE')) {
      status = 'ON_REVIEW';
      lastAnswer = { ...answer, verdict: 'PENDING' };
    } else if (body.method === 'ANSWER' && (body.text ?? '').trim().length < 10) {
      status = 'OPEN';
      lastAnswer = { ...answer, verdict: 'REJECTED', reviewComment: 'Опишите цель подробнее: на что именно пойдут деньги.' };
    }
    let spots = state.spots.map((s) => (s.id === spotId ? { ...s, status, lastAnswer } : s));
    if (spotId === 's1' && body.method === 'CONNECT_SOURCE' && !spots.some((s) => s.id === 's5')) {
      spots = [...spots, ANOMALY_SPOT];
    }
    state = recalc({ ...state, spots });
    if (state.application.status === 'INFO_REQUESTED' && state.spots.every((s) => s.status !== 'OPEN')) {
      setStatus('UNDERWRITING');
    }
    return new Promise((resolve) => setTimeout(() => resolve(structuredClone(state)), delay));
  },

  uploadDocument: (_appId, file, kind) =>
    wait({ id: `doc-${Date.now()}`, kind, fileName: file.name, uploadedAt: new Date().toISOString() }),

  downloadDocument: () => Promise.resolve(new Blob(['Демо-документ'], { type: 'text/plain' })),

  submitApplication: () => {
    setStatus('SUBMITTED');
    return wait(state);
  },

  acceptOffer: (_appId, offerId) => {
    accept(offerId);
    return wait(state);
  },

  signContract: () => {
    sign();
    return wait(state);
  },

  cancelApplication: () => {
    setStatus('CANCELLED');
    return wait(state);
  },
};