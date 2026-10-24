import type { ComponentProps } from 'react';
import type { Status } from '@alfalab/core-components/status';
import type { ApplicationStatus } from './types';

type StatusColor = NonNullable<ComponentProps<typeof Status>['color']>;

export interface StatusMeta {
  label: string;
  color: StatusColor;
}

export const STATUS_META: Record<ApplicationStatus, StatusMeta> = {
  DRAFT: { label: 'Черновик', color: 'grey' },
  COLLECTING: { label: 'Сбор данных', color: 'blue' },
  SUBMITTED: { label: 'Подана', color: 'blue' },
  AUTO_CHECK: { label: 'Автопроверка', color: 'blue' },
  UNDERWRITING: { label: 'Андеррайтинг', color: 'purple' },
  INFO_REQUESTED: { label: 'Дозапрос', color: 'orange' },
  APPROVED: { label: 'Одобрено', color: 'green' },
  COUNTER_OFFER: { label: 'Иные условия', color: 'teal' },
  REJECTED: { label: 'Отказ', color: 'red' },
  SIGNING: { label: 'Оформление', color: 'blue' },
  ISSUED: { label: 'Выдано', color: 'green' },
  CANCELLED: { label: 'Отменена', color: 'grey' },
};