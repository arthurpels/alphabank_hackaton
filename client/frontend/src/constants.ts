import type { ApplicationStatus, CloseMethod } from './types';

export const STATUS_LABELS: Record<ApplicationStatus, string> = {
  DRAFT: 'Черновик',
  COLLECTING: 'Сбор данных',
  SUBMITTED: 'Подана',
  AUTO_CHECK: 'Автопроверка',
  UNDERWRITING: 'Андеррайтинг',
  INFO_REQUESTED: 'Дозапрос',
  APPROVED: 'Одобрено',
  COUNTER_OFFER: 'Иные условия',
  REJECTED: 'Отказ',
  SIGNING: 'Оформление',
  ISSUED: 'Выдано',
  CANCELLED: 'Отменена',
};

export const CLOSE_METHOD_LABELS: Record<CloseMethod, string> = {
  CONNECT_SOURCE: 'Подключить',
  ANSWER: 'Ответить',
  UPLOAD: 'Загрузить',
  PAY: 'Погасить',
  ADD_COLLATERAL: 'Добавить',
};

export const FORK_SCALE = { min: 15, max: 28 };