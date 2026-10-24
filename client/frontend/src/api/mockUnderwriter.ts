import type {
  ApplicationDetails,
  ApplicationStatus,
  DecisionRequest,
  Fork,
  Offer,
  QueueItem,
  Spot,
  SpotCreate,
  SpotReviewRequest,
  UnderwritingSummary,
} from '../types';
import { ApiError } from './http';

export const BUILDER_ID = 'builder-application';

const UW_ACTIONS: ApplicationDetails['application']['availableActions'] = ['REVIEW_SPOT', 'ADD_SPOT', 'DECIDE'];

const fork = (rateFrom: number, rateTo: number, zone: Fork['zone'], approveMax = 20, rejectMin = 24): Fork => ({
  rateFrom,
  rateTo,
  zone,
  thresholds: { approveMax, rejectMin },
});

const nowIso = () => new Date().toISOString();
const wait = <T>(v: T) => new Promise<T>((r) => setTimeout(() => r(structuredClone(v)), 150));

function initial(): ApplicationDetails {
  return {
    application: {
      id: BUILDER_ID,
      status: 'UNDERWRITING',
      availableActions: UW_ACTIONS,
      company: { name: 'ИП Петров Сергей Николаевич', inn: '590234567822' },
      product: { name: 'Кредит на оборудование' },
      amount: 250000000,
      termMonths: 36,
    },
    fork: fork(19, 23.5, 'UNDERWRITER'),
    spots: [
      {
        id: 'u1',
        type: 'ANOMALY',
        title: 'Почему в марте выручка упала на 45 %',
        clientHint: 'Пока нет объяснения — считаем это риском. Что случилось?',
        scoringFactor: 'revenue_stability',
        pricePp: 2.5,
        closeMethod: 'ANSWER',
        status: 'ON_REVIEW',
        addedBy: 'SYSTEM',
        answerOptions: [
          { code: 'RENOVATION', label: 'Был ремонт' },
          { code: 'SEASON', label: 'Сезонный спад' },
        ],
        lastAnswer: {
          id: 'a1',
          optionCode: 'RENOVATION',
          text: 'В марте объект закрывали на ремонт, договор подряда с генподрядчиком приложен.',
          documentId: 'doc-1',
          contractAmount: 90000000,
          contractTermMonths: 6,
          verdict: 'PENDING',
          createdAt: nowIso(),
        },
      },
      {
        id: 'u2',
        type: 'COLLATERAL',
        title: 'Залог или поручительство — по желанию',
        clientHint: 'Поручительство собственника или залог сузит вилку.',
        scoringFactor: 'collateral',
        pricePp: 1.5,
        closeMethod: 'ADD_COLLATERAL',
        status: 'OPEN',
        addedBy: 'SYSTEM',
      },
      {
        id: 'u3',
        type: 'NO_DATA',
        title: 'Налоговая отчётность подтверждена',
        clientHint: 'Данные получены из ФНС.',
        pricePp: 0,
        closeMethod: 'CONNECT_SOURCE',
        status: 'CLOSED',
        addedBy: 'SYSTEM',
        source: 'FNS',
      },
    ],
    offers: [
      { id: 'o1', productCode: 'EQUIPMENT', amount: 250000000, termMonths: 36, fork: fork(21, 23.5, 'UNDERWRITER'), proposedBy: 'SYSTEM', status: 'CALCULATED', isRequested: true, isManual: false },
      { id: 'o2', productCode: 'EQUIPMENT', amount: 195000000, termMonths: 36, fork: fork(19.2, 19.2, 'APPROVE'), proposedBy: 'SYSTEM', status: 'CALCULATED', isRequested: false, isManual: false },
      { id: 'o3', productCode: 'EQUIPMENT', amount: 150000000, termMonths: 24, fork: fork(18.9, 18.9, 'APPROVE'), proposedBy: 'SYSTEM', status: 'CALCULATED', isRequested: false, isManual: false },
      { id: 'o4', productCode: 'WORKING_CAPITAL', amount: 120000000, termMonths: 36, fork: fork(20.5, 20.5, 'APPROVE', 22, 26), proposedBy: 'SYSTEM', status: 'CALCULATED', isRequested: false, isManual: false },
    ],
    decision: null,
    contract: null,
  };
}

let state: ApplicationDetails = initial();

const setStatus = (status: ApplicationStatus) => {
  state = {
    ...state,
    application: {
      ...state.application,
      status,
      availableActions: status === 'UNDERWRITING' ? UW_ACTIONS : [],
    },
  };
};

const summary: UnderwritingSummary = {
  generatedBy: 'TEMPLATE',
  items: [
    {
      text: 'Выручка в марте упала на 45 %: клиент объясняет это ремонтом объекта и приложил договор подряда.',
      facts: [
        { source: 'OFD', label: 'выручка 2026-03 — 830 000 ₽', spotId: 'u1' },
        { source: 'ОТВЕТ', label: 'договор подряда на 900 000 ₽, 6 мес.', spotId: 'u1' },
      ],
    },
    {
      text: 'Кредитная история чистая, просрочек нет; долговая нагрузка умеренная.',
      facts: [{ source: 'BKI', label: 'действующих просрочек нет' }],
    },
    {
      text: 'Запрошенные условия в зоне андеррайтера: правый край вилки 23,5 % выше порога одобрения 20 %.',
      facts: [{ source: 'ВИЛКА', label: 'от 19 % до 23,5 %' }],
    },
  ],
};

export const underwriterMock = {
  isBuilder: (id: string) => id === BUILDER_ID,

  getBuilder: () => wait(state),

  getUnderwritingQueue: (statuses?: ApplicationStatus[]) => {
    const st = state.application.status;
    const wanted: ApplicationStatus[] = statuses?.length ? statuses : ['UNDERWRITING', 'INFO_REQUESTED'];
    if (!wanted.includes(st)) return wait<QueueItem[]>([]);
    const item: QueueItem = {
      applicationId: BUILDER_ID,
      companyName: state.application.company.name,
      inn: state.application.company.inn ?? '',
      productCode: 'EQUIPMENT',
      amount: state.application.amount,
      termMonths: state.application.termMonths,
      status: st,
      fork: state.fork,
      openSpots: state.spots.filter((s) => s.status === 'OPEN').length,
      onReviewSpots: state.spots.filter((s) => s.status === 'ON_REVIEW').length,
      submittedAt: nowIso(),
    };
    return wait([item]);
  },

  getUnderwritingSummary: () => wait(summary),

  reviewSpot: (_id: string, spotId: string, body: SpotReviewRequest) => {
    if (body.verdict === 'REJECTED' && !body.comment?.trim()) {
      return Promise.reject(new ApiError(400, 'VALIDATION_ERROR', 'При отказе нужен комментарий'));
    }
    const spot = state.spots.find((s) => s.id === spotId);
    if (!spot || spot.status !== 'ON_REVIEW') {
      return Promise.reject(new ApiError(409, 'INVALID_TRANSITION', 'Ответ по этому пятну уже проверен'));
    }
    const accepted = body.verdict === 'ACCEPTED';
    state = {
      ...state,
      spots: state.spots.map((s) =>
        s.id === spotId
          ? {
              ...s,
              status: accepted ? 'CLOSED' : 'OPEN',
              lastAnswer: s.lastAnswer && {
                ...s.lastAnswer,
                verdict: body.verdict,
                reviewComment: body.comment ?? null,
                reviewedAt: nowIso(),
              },
            }
          : s,
      ),
      fork:
        accepted && state.fork
          ? {
              ...state.fork,
              rateTo: Math.max(state.fork.rateFrom, Math.round((state.fork.rateTo - spot.pricePp) * 10) / 10),
            }
          : state.fork,
    };
    return wait(state);
  },

  addSpot: (_id: string, body: SpotCreate) => {
    if (!body.title.trim() || !body.clientHint.trim()) {
      return Promise.reject(new ApiError(400, 'VALIDATION_ERROR', 'Заполните название и подсказку клиенту'));
    }
    const spot: Spot = {
      id: `u${state.spots.length + 1}`,
      type: body.type,
      title: body.title,
      clientHint: body.clientHint,
      scoringFactor: body.scoringFactor ?? null,
      pricePp: body.scoringFactor ? 1.5 : 0,
      closeMethod: body.closeMethod,
      status: 'OPEN',
      addedBy: 'UNDERWRITER',
    };
    state = {
      ...state,
      spots: [...state.spots, spot],
      application: {
        ...state.application,
        infoRequestDeadline: new Date(Date.now() + 5 * 24 * 3600 * 1000).toISOString(),
      },
    };
    setStatus('INFO_REQUESTED');
    return wait(state);
  },

  makeDecision: (_id: string, body: DecisionRequest) => {
    const fail = (m: string) => Promise.reject(new ApiError(400, 'VALIDATION_ERROR', m));
    if (body.type === 'REJECTED') {
      if (!body.reasonSpotId) return fail('Укажите пятно-причину отказа');
      if (!body.comment?.trim()) return fail('При отказе нужен комментарий');
    }
    if (body.override && !body.comment?.trim()) return fail('При ручных условиях нужен комментарий');

    let offers: Offer[] = state.offers;
    let status: ApplicationStatus = 'APPROVED';
    if (body.type === 'APPROVED') {
      const requested = state.offers.find((o) => o.isRequested);
      offers = state.offers.map((o) => (o === requested ? { ...o, status: 'OFFERED' as const } : o));
    } else if (body.type === 'ALTERNATIVE') {
      const picked = new Set(body.offerIds ?? []);
      const count = picked.size + (body.manualOffer ? 1 : 0);
      if (count < 1 || count > 3) return fail('Нужно отметить от 1 до 3 вариантов');
      offers = state.offers.map((o) => (picked.has(o.id) ? { ...o, status: 'OFFERED' as const } : o));
      if (body.manualOffer) {
        offers.push({
          id: 'o-manual',
          productCode: body.manualOffer.productCode,
          amount: body.manualOffer.amount,
          termMonths: body.manualOffer.termMonths,
          fork: fork(20, 20, 'APPROVE'),
          proposedBy: 'UNDERWRITER',
          status: 'OFFERED',
          isRequested: false,
          isManual: true,
        });
      }
      status = 'COUNTER_OFFER';
    } else {
      status = 'REJECTED';
    }
    state = {
      ...state,
      offers,
      decision: {
        id: 'decision-uw',
        type: body.type,
        decidedBy: 'UNDERWRITER',
        override: !!body.override,
        comment: body.comment ?? null,
        reasonSpotId: body.reasonSpotId ?? null,
        decidedAt: nowIso(),
      },
    };
    setStatus(status);
    return wait(state);
  },
};