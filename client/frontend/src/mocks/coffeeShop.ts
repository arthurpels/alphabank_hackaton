import type { ApplicationDetails } from '../types';

export const coffeeShop: ApplicationDetails = {
  application: {
    status: 'COLLECTING',
    company: { name: 'ООО «Кофейня»' },
    product: { name: 'Кредит на оборудование' },
    amount: 200000000,
    termMonths: 24,
  },
  fork: {
    rateFrom: 17,
    rateTo: 26,
    zone: 'UNDERWRITER',
    thresholds: { approveMax: 21, rejectMin: 24 },
  },
  spots: [
    {
      id: 's1',
      type: 'NO_DATA',
      title: 'Продажи через кассу (~70% выручки)',
      clientHint: 'Видим только деньги на счёте. Продажи через кассу пока считаем нулём.',
      pricePp: 3.5,
      closeMethod: 'CONNECT_SOURCE',
      status: 'OPEN',
    },
    {
      id: 's2',
      type: 'ANOMALY',
      title: 'В марте выручка упала на 45%',
      clientHint: 'Пока нет объяснения — считаем это риском. Что случилось?',
      pricePp: 2.5,
      closeMethod: 'ANSWER',
      status: 'OPEN',
    },
    {
      id: 's3',
      type: 'NO_DATA',
      title: 'На что именно нужны деньги',
      clientHint: 'Опишите своими словами — подберём продукт и учтём цель.',
      pricePp: 1.5,
      closeMethod: 'ANSWER',
      status: 'OPEN',
    },
    {
      id: 's4',
      type: 'COLLATERAL',
      title: 'Залог или поручительство',
      clientHint: 'Самый дорогой для вас способ — оставьте на конец.',
      pricePp: 1.5,
      closeMethod: 'ADD_COLLATERAL',
      status: 'OPEN',
    },
  ],
};