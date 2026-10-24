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
  removable?: boolean | null;
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
    company: { name: string };
    product: { name: string };
    amount: number;
    termMonths: number;
  };
  fork: Fork | null;
  spots: Spot[];
}

export interface DemoUser {
  id: string;
  fio: string;
  role: UserRole;
  personaTitle: string | null;
  company: { name: string; inn: string } | null;
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