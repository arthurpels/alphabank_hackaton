import type { ApplicationDetails } from '../types';

export const coffeeShop: ApplicationDetails = {
  application: {
    id: 'coffee-application',
    status: 'COLLECTING',
    availableActions: ['EDIT', 'CLOSE_SPOT', 'SUBMIT', 'CANCEL'],
    company: { name: 'ООО «Кофейня»' },
    product: { name: 'Кредит на оборудование' },
    amount: 200000000,
    termMonths: 36,
    goal: 'Покупка профессиональной кофемашины и холодильного оборудования для второй точки',
  },
  fork: {
    rateFrom: 17,
    rateTo: 26,
    zone: 'UNDERWRITER',
    thresholds: { approveMax: 20, rejectMin: 24 },
    factors: [
      { code: 'credit_history', title: 'Кредитная история', applicable: true, known: true, value: -1, source: 'BKI' },
      { code: 'business_age', title: 'Возраст бизнеса', applicable: true, known: true, value: -0.5, source: 'EGRUL' },
      { code: 'tax_discipline', title: 'Налоговая дисциплина', applicable: true, known: true, value: 0, source: 'FNS' },
      { code: 'debt_load', title: 'Долговая нагрузка', applicable: true, known: false, best: -1.5, worst: 2, spotPricePp: 3.5, source: 'OFD' },
      { code: 'revenue_stability', title: 'Стабильность выручки', applicable: true, known: false, best: 0, worst: 2.5, spotPricePp: 2.5, source: 'OFD' },
      { code: 'purpose', title: 'Цель кредита', applicable: true, known: false, best: -0.5, worst: 1, spotPricePp: 1.5, source: null },
      { code: 'collateral', title: 'Обеспечение', applicable: true, known: false, best: -1.5, worst: 0, spotPricePp: 1.5, source: null },
    ],
  },
  offers: [],
  decision: null,
  contract: null,
  spots: [
    { id: 's1', type: 'NO_DATA', title: 'Продажи через кассу не видны', clientHint: 'Видим только деньги на счёте. Подключите онлайн-кассу — банк увидит реальную выручку.', pricePp: 3.5, closeMethod: 'CONNECT_SOURCE', status: 'OPEN', source: 'OFD' },
    { id: 's2', type: 'NO_DATA', title: 'Не знаем, стабильна ли выручка — закроется, когда подключите источник', clientHint: 'Закроется само, когда подключите кассу.', pricePp: 2.5, closeMethod: 'CONNECT_SOURCE', status: 'OPEN', source: 'OFD' },
    { id: 's3', type: 'NO_DATA', title: 'На что именно нужны деньги', clientHint: 'Опишите своими словами — подберём продукт и учтём цель.', scoringFactor: 'purpose', pricePp: 1.5, closeMethod: 'ANSWER', status: 'OPEN' },
    { id: 's4', type: 'COLLATERAL', title: 'Залог или поручительство — по желанию', clientHint: 'Поручительство собственника или залог сузит вилку.', pricePp: 1.5, closeMethod: 'ADD_COLLATERAL', status: 'OPEN' },
  ],
};