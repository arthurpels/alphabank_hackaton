import type { CloseMethod } from './types';

export const CLOSE_METHOD_LABELS: Record<CloseMethod, string> = {
  CONNECT_SOURCE: 'Подключить',
  ANSWER: 'Ответить',
  UPLOAD: 'Загрузить',
  PAY: 'Погасить',
  ADD_COLLATERAL: 'Добавить',
};

export const FORK_SCALE = { min: 15, max: 28 };