export type ApplicationStatus =
  | 'DRAFT' | 'COLLECTING' | 'SUBMITTED' | 'AUTO_CHECK' | 'UNDERWRITING'
  | 'INFO_REQUESTED' | 'APPROVED' | 'COUNTER_OFFER' | 'REJECTED'
  | 'SIGNING' | 'ISSUED' | 'CANCELLED';

export type SpotType = 'NO_DATA' | 'ANOMALY' | 'DOCUMENT' | 'STOP_FACTOR' | 'COLLATERAL';
export type SpotStatus = 'OPEN' | 'ON_REVIEW' | 'CLOSED';
export type CloseMethod = 'CONNECT_SOURCE' | 'ANSWER' | 'UPLOAD' | 'PAY' | 'ADD_COLLATERAL';
export type DecisionZone = 'APPROVE' | 'UNDERWRITER' | 'REJECT';
export type UserRole = 'CLIENT' | 'UNDERWRITER';

export interface Spot {
  id: string;
  type: SpotType;
  title: string;
  clientHint: string;
  pricePp: number;
  closeMethod: CloseMethod | null;
  status: SpotStatus;
  source?: SourceCode | null;
  removable?: boolean | null;
  scoringFactor?: string | null;
  addedBy?: DecidedBy;
  answerOptions?: AnswerOption[];
  lastAnswer?: SpotAnswer | null;
}

export type FactorCode =
  | 'credit_history' | 'business_age' | 'tax_discipline' | 'debt_load'
  | 'revenue_stability' | 'purpose' | 'collateral';

export interface ForkFactor {
  code: FactorCode;
  title: string;
  applicable: boolean;
  known: boolean;
  value?: number | null;
  best?: number | null;
  worst?: number | null;
  spotPricePp?: number | null;
  source?: SourceCode | null;
}

export interface Fork {
  rateFrom: number;
  rateTo: number;
  factors?: ForkFactor[];
  reproducible?: boolean | null;
  rulesVersion?: string | null;
  asOf?: string | null;
  zone: DecisionZone;
  thresholds: { approveMax: number; rejectMin: number };
}

export interface ApplicationDetails {
  application: {
    id: string;
    status: ApplicationStatus;
    availableActions: ApplicationAction[];
    infoRequestDeadline?: string | null;
    company: { name: string; inn?: string };
    product: { name: string; code?: ProductCode };
    amount: number;
    termMonths: number;
    goal?: string;
  };
  fork: Fork | null;
  spots: Spot[];
  offers: Offer[];
  decision: Decision | null;
  contract: Contract | null;
}

export interface Company {
  id: string;
  inn: string;
  name: string;
  legalForm: 'OOO' | 'IP';
  address?: string | null;
  directorFio?: string | null;
  registeredAt?: string | null;
  okvedMain?: string | null;
}

export interface DemoUser {
  id: string;
  fio: string;
  role: UserRole;
  personaTitle: string | null;
  company: Company | null;
}

export interface ApplicationSummary {
  id: string;
  status: ApplicationStatus;
  productCode: ProductCode;
  amount: number;
  termMonths: number;
  fork?: Fork | null;
  createdAt: string;
}

export interface Product {
  code: ProductCode;
  name: string;
  minAmount: number;
  maxAmount: number;
  allowedTerms: number[];
}

export type ProductCode = 'WORKING_CAPITAL' | 'EQUIPMENT' | 'OVERDRAFT';
export type SourceCode = 'BKI' | 'EGRUL' | 'FNS' | 'OFD' | 'MARKETPLACE' | 'ACQUIRING' | 'REPUTATION';
export type CollateralType = 'GUARANTEE' | 'PLEDGE' | 'DECLINED';

export interface ApplicationCreate {
  productCode: ProductCode;
  amount: number;
  termMonths: number;
  goal: string;
  consents: { bki: boolean; personalData: boolean };
}

export interface SpotCloseRequest {
  method: CloseMethod;
  sourceCode?: SourceCode;
  consent?: boolean;
  optionCode?: string;
  text?: string;
  documentId?: string;
  collateralType?: CollateralType;
  collateralComment?: string;
  contractAmount?: number;
  contractTermMonths?: number;
}

export type ApplicationAction =
  | 'EDIT' | 'EVALUATE' | 'CLOSE_SPOT' | 'SUBMIT' | 'ACCEPT'
  | 'SIGN' | 'CANCEL' | 'REVIEW_SPOT' | 'ADD_SPOT' | 'DECIDE';

export type OfferStatus = 'CALCULATED' | 'OFFERED' | 'CHOSEN' | 'DECLINED' | 'EXPIRED';
export type DecidedBy = 'SYSTEM' | 'UNDERWRITER';
export type DecisionType = 'APPROVED' | 'ALTERNATIVE' | 'REJECTED';

export interface Offer {
  id: string;
  productCode: ProductCode;
  amount: number;
  termMonths: number;
  collateralType?: string | null;
  fork: Fork;
  proposedBy: DecidedBy;
  status: OfferStatus;
  isRequested: boolean;
  isManual: boolean;
}

export interface Decision {
  id: string;
  type: DecisionType;
  decidedBy: DecidedBy;
  override: boolean;
  comment?: string | null;
  reasonSpotId?: string | null;
  decidedAt: string;
  rulesVersion?: string | null;
  forkSnapshotId?: string | null;
  requestHash?: string | null;
}

export interface PaymentScheduleItem {
  number: number;
  date: string;
  payment: number;
  principal: number;
  interest: number;
  balance: number;
}

export interface Contract {
  offerId: string;
  amount: number;
  termMonths: number;
  rate: number;
  monthlyPayment: number;
  signedAt?: string | null;
  issuedAt?: string | null;
  schedule: PaymentScheduleItem[];
}


//  Кабинет андеррайтера (T39): схемы из docs/openapi.yaml 
export interface AnswerOption {
  code: string;
  label: string;
}

export interface SpotAnswer {
  id: string;
  optionCode?: string | null;
  text?: string | null;
  documentId?: string | null;
  contractAmount?: number | null;
  contractTermMonths?: number | null;
  verdict: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  reviewedBy?: string | null;
  reviewComment?: string | null;
  createdAt: string;
  reviewedAt?: string | null;
  purposeVerdict?: PurposeVerdict | null;
  anomalyVerdict?: AnomalyVerdict | null;
}

export type ScoringFactor = 'debt_load' | 'revenue_stability' | 'purpose' | 'collateral';

export interface SpotCreate {
  type: SpotType;
  title: string;
  clientHint: string;
  closeMethod: CloseMethod;
  scoringFactor?: ScoringFactor | null;
}

export interface SpotReviewRequest {
  verdict: 'ACCEPTED' | 'REJECTED';
  comment?: string;
}

export interface ManualOffer {
  productCode: ProductCode;
  amount: number; // копейки
  termMonths: number;
  collateralType?: string | null;
}

export interface DecisionRequest {
  type: DecisionType;
  offerIds?: string[];
  manualOffer?: ManualOffer;
  override?: boolean;
  comment?: string;
  reasonSpotId?: string;
}

export interface QueueItem {
  applicationId: string;
  companyName: string;
  inn: string;
  productCode: ProductCode;
  amount: number;
  termMonths: number;
  status: ApplicationStatus;
  fork?: Fork | null;
  openSpots: number;
  onReviewSpots: number;
  submittedAt?: string | null;
}

export interface FactRef {
  source: string;
  label: string;
  spotId?: string | null;
}

export interface SummaryItem {
  text: string;
  facts: FactRef[];
}

export interface UnderwritingSummary {
  status?: 'ready' | 'generating';
  generatedBy: 'LLM' | 'TEMPLATE';
  items: SummaryItem[];
  llm?: LlmMeta | null;
  generatedAt?: string;
}

export type CheckStatus = 'OK' | 'WARNING' | 'BLOCKED';

export interface SourceCheck {
  source: SourceCode;
  status: CheckStatus;
  facts: string[];
  problems: string[];
}

export interface SourceChecks {
  checkedAt: string;
  items: SourceCheck[];
}

export interface Document {
  id: string;
  kind: string;
  fileName: string;
  uploadedAt: string;
}

export interface LlmMeta {
  model: string;
  digest: string;
  promptVersion: string;
  cached: boolean;
}

export interface PurposeVerdict {
  productCode: ProductCode;
  status: 'DECIDED' | 'UNCLEAR' | 'UNAVAILABLE' | 'NOT_APPLICABLE';
  purpose?: 'MATCH' | 'MISMATCH' | null;
  generatedBy: 'KEYWORDS' | 'LLM';
  matchedKeywords: string[];
  reason?: string | null;
  evidence?: string | null;
  violation?: string | null;
  llm?: LlmMeta | null;
  profile?: PurposeProfile | null;
}

export interface PurposeProfile {
  okved: string;
  activity?: string | null;
  status: 'FITS' | 'DOES_NOT_FIT' | 'UNCLEAR' | 'NOT_CHECKED';
  reason?: string | null;
  evidence?: string | null;
  violation?: string | null;
  llm?: LlmMeta | null;
}

export type AnomalyCause = 'RENOVATION' | 'SEASONAL' | 'CLIENT_LOST' | 'OTHER';

export interface AnomalyVerdict {
  anomaly?: {
    code: 'REVENUE_DROP' | 'REVENUE_SPIKE';
    month: string;
    changePct: number;
    source?: SourceCode | null;
  } | null;
  code?: AnomalyCause | null;
  classification?: {
    status: 'DECIDED' | 'UNAVAILABLE';
    code?: AnomalyCause | null;
    generatedBy: 'KEYWORDS' | 'LLM';
    matchedKeywords: string[];
    reason?: string | null;
    evidence?: string | null;
    violation?: string | null;
    llm?: LlmMeta | null;
  } | null;
  check?: {
    verdict: 'ACCEPTED' | 'REVIEW';
    rule?: string | null;
    explanation: string;
  } | null;
}

export interface FieldDiff {
  path: string;
  expected?: unknown;
  actual?: unknown;
}

export interface UsedVerdict {
  spotId: string;
  scoringFactor?: string | null;
  verdict: PurposeVerdict;
}

export interface ForkVerification {
  snapshotId: string;
  reproducible: boolean;
  rulesVersion: string;
  engineVersion?: string | null;
  currentEngineVersion?: string | null;
  requestHash: string;
  calculatedAt: string;
  verdicts: UsedVerdict[];
  diff: FieldDiff[];
}

export interface RulesRule {
  condition: string | null;
  value: number;
}

export interface RulesFactor {
  code: FactorCode;
  title: string;
  unknown: { best: number; worst: number; spotPricePp: number } | null;
  rules: RulesRule[];
  overdraftRules: RulesRule[];
}

export interface RulesProduct {
  code: ProductCode;
  name: string;
  baseRate: number;
  thresholds: { approveMax: number; rejectMin: number };
  amount: { min: number; max: number };
  terms: number[];
  policy: { metric: 'PTI' | 'LIMIT_SHARE'; cap: number };
  skipFactors: FactorCode[];
}

export interface RulesStopFactor {
  code: string;
  condition: string;
  removable: boolean;
  closeMethod: 'PAY' | null;
}

export interface RulesResponse {
  rulesVersion: string;
  calcRatePct: number;
  products: RulesProduct[];
  factors: RulesFactor[];
  stopFactors: RulesStopFactor[];
}