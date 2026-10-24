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

export interface Fork {
  rateFrom: number;
  rateTo: number;
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
  generatedBy: 'LLM' | 'TEMPLATE';
  items: SummaryItem[];
}

export interface Document {
  id: string;
  kind: string;
  fileName: string;
  uploadedAt: string;
}