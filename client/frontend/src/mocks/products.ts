import type { Product } from '../types';

export const products: Product[] = [
  { code: 'EQUIPMENT', name: 'Кредит на оборудование', minAmount: 30000000, maxAmount: 1000000000, allowedTerms: [12, 24, 36] },
  { code: 'WORKING_CAPITAL', name: 'Кредит на пополнение оборотных средств', minAmount: 10000000, maxAmount: 1000000000, allowedTerms: [6, 12, 24, 36] },
  { code: 'OVERDRAFT', name: 'Овердрафт для бизнеса', minAmount: 5000000, maxAmount: 300000000, allowedTerms: [3, 6, 12] },
];