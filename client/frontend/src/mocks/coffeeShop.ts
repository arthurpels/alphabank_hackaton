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
  },
  fork: {
    rateFrom: 17,
    rateTo: 26,
    zone: 'UNDERWRITER',
    thresholds: { approveMax: 20, rejectMin: 24 },
  },
  offers: [],
  decision: null,
  contract: null,
  spots: [
    { id: 's1', type: 'NO_DATA', title: 'Продажи через кассу не видны', clientHint: 'Видим только деньги на счёте. Подключите онлайн-кассу — банк увидит реальную выручку.', pricePp: 3.5, closeMethod: 'CONNECT_SOURCE', status: 'OPEN', source: 'OFD' },
    { id: 's2', type: 'NO_DATA', title: 'Не знаем, стабильна ли выручка — закроется, когда подключите источник', clientHint: 'Закроется само, когда подключите кассу.', pricePp: 2.5, closeMethod: 'CONNECT_SOURCE', status: 'OPEN', source: 'OFD' },
    { id: 's3', type: 'NO_DATA', title: 'На что именно нужны деньги', clientHint: 'Опишите своими словами — подберём продукт и учтём цель.', pricePp: 1.5, closeMethod: 'ANSWER', status: 'OPEN' },
    { id: 's4', type: 'COLLATERAL', title: 'Залог или поручительство — по желанию', clientHint: 'Поручительство собственника или залог сузит вилку.', pricePp: 1.5, closeMethod: 'ADD_COLLATERAL', status: 'OPEN' },
  ],
};