import type { RulesResponse } from '../types';

export const mockRules: RulesResponse = {
  rulesVersion: '2026-10-26.draft2',
  calcRatePct: 20,
  products: [
    { code: 'EQUIPMENT', name: 'Кредит на оборудование', baseRate: 22, thresholds: { approveMax: 20, rejectMin: 24 }, amount: { min: 30000000, max: 1000000000 }, terms: [12, 24, 36], policy: { metric: 'PTI', cap: 0.3 }, skipFactors: [] },
    { code: 'WORKING_CAPITAL', name: 'Кредит на пополнение оборотных средств', baseRate: 20, thresholds: { approveMax: 21, rejectMin: 25 }, amount: { min: 10000000, max: 1000000000 }, terms: [6, 12, 24, 36], policy: { metric: 'PTI', cap: 0.3 }, skipFactors: [] },
    { code: 'OVERDRAFT', name: 'Овердрафт для бизнеса', baseRate: 21, thresholds: { approveMax: 22, rejectMin: 26 }, amount: { min: 5000000, max: 300000000 }, terms: [3, 6, 12], policy: { metric: 'LIMIT_SHARE', cap: 0.3 }, skipFactors: ['purpose', 'collateral'] },
  ],
  factors: [
    { code: 'credit_history', title: 'Кредитная история', unknown: null, overdraftRules: [], rules: [
      { condition: 'delinquencies_12m > 0 or credit_score < 500', value: 2 },
      { condition: 'credit_score >= 700', value: -1 },
      { condition: 'credit_score >= 600', value: 0 },
      { condition: null, value: 1 },
    ] },
    { code: 'business_age', title: 'Возраст бизнеса', unknown: null, overdraftRules: [], rules: [
      { condition: 'age_months < 12', value: 1.5 },
      { condition: 'age_months < 36', value: 0.5 },
      { condition: null, value: -0.5 },
    ] },
    { code: 'tax_discipline', title: 'Налоговая дисциплина', unknown: null, overdraftRules: [], rules: [
      { condition: 'unfiled_reports > 0', value: 1 },
      { condition: null, value: 0 },
    ] },
    { code: 'debt_load', title: 'Подтверждённая выручка и долговая нагрузка', unknown: { best: -1.5, worst: 2, spotPricePp: 3.5 }, rules: [
      { condition: 'pti <= 0.05', value: -1.5 },
      { condition: 'pti <= 0.10', value: -0.5 },
      { condition: 'pti <= 0.25', value: 0.5 },
      { condition: null, value: 1.5 },
    ], overdraftRules: [
      { condition: 'limit_share <= 0.15', value: -0.5 },
      { condition: null, value: 0.5 },
    ] },
    { code: 'revenue_stability', title: 'Стабильность выручки', unknown: { best: 0, worst: 2.5, spotPricePp: 2.5 }, overdraftRules: [], rules: [
      { condition: 'series and no_anomalies', value: 0 },
      { condition: 'anomaly_explained_accepted', value: 0 },
      { condition: 'only_document', value: 1 },
      { condition: 'anomaly_explanation_rejected', value: 2.5 },
    ] },
    { code: 'purpose', title: 'Цель кредита', unknown: { best: -0.5, worst: 1, spotPricePp: 1.5 }, overdraftRules: [], rules: [
      { condition: 'purpose_matches_product', value: -0.5 },
      { condition: null, value: 0.5 },
    ] },
    { code: 'collateral', title: 'Обеспечение', unknown: { best: -1.5, worst: 0, spotPricePp: 1.5 }, overdraftRules: [], rules: [
      { condition: 'guarantee_or_pledge', value: -1.5 },
      { condition: null, value: 0 },
    ] },
  ],
  stopFactors: [
    { code: 'LIQUIDATION', condition: "egrul.status != 'ACTIVE'", removable: false, closeMethod: null },
    { code: 'YOUNG_BUSINESS', condition: 'age_months < 6', removable: false, closeMethod: null },
    { code: 'NEW_DIRECTOR', condition: 'director_months < 3', removable: false, closeMethod: null },
    { code: 'MASS_ADDRESS', condition: 'egrul.address.is_mass', removable: false, closeMethod: null },
    { code: 'TAX_DEBT', condition: 'fns.tax_debt.amount > 0', removable: true, closeMethod: 'PAY' },
    { code: 'BKI_OVERDUE', condition: 'bki.delinquencies.current > 0', removable: false, closeMethod: null },
  ],
};