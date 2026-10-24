import type { CloseMethod, ProductCode } from './types';

export const CLOSE_METHOD_LABELS: Record<CloseMethod, string> = {
  CONNECT_SOURCE: 'Подключить',
  ANSWER: 'Ответить',
  UPLOAD: 'Загрузить',
  PAY: 'Погасить',
  ADD_COLLATERAL: 'Добавить',
};

export const FORK_SCALE = { min: 15, max: 28 };

export const PRODUCT_NAMES: Record<ProductCode, string> = {
  EQUIPMENT: 'Кредит на оборудование',
  WORKING_CAPITAL: 'Кредит на пополнение оборотных средств',
  OVERDRAFT: 'Овердрафт для бизнеса',
};