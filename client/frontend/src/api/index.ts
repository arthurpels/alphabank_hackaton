import type {
  ApplicationCreate,
  ApplicationDetails,
  ApplicationStatus,
  ApplicationSummary,
  DecisionRequest,
  Document,
  QueueItem,
  SpotCreate,
  SpotReviewRequest,
  UnderwritingSummary,
  DemoUser,
  Product,
  SourceChecks,
  SpotCloseRequest,
  UserRole,
} from '../types';
import { request, requestBlob } from './http';
import { mockApi } from './mock';

export { ApiError, setCurrentUser, getCurrentUser } from './http';

export interface Api {
  listDemoUsers(role?: UserRole): Promise<DemoUser[]>;
  getMe(): Promise<DemoUser>;
  listProducts(): Promise<Product[]>;
  listApplications(): Promise<ApplicationSummary[]>;
  getApplication(id: string): Promise<ApplicationDetails>;
  createApplication(body: ApplicationCreate): Promise<ApplicationDetails>;
  evaluateApplication(id: string): Promise<ApplicationDetails>;
  getChecks(id: string): Promise<SourceChecks>;
  closeSpot(id: string, spotId: string, body: SpotCloseRequest): Promise<ApplicationDetails>;
  uploadDocument(id: string, file: File, kind: string): Promise<Document>;
  downloadDocument(id: string, documentId: string): Promise<Blob>;
  submitApplication(id: string): Promise<ApplicationDetails>;
  acceptOffer(id: string, offerId: string): Promise<ApplicationDetails>;
  signContract(id: string): Promise<ApplicationDetails>;
  cancelApplication(id: string, reason?: string): Promise<ApplicationDetails>;
  // кабинет андеррайтера (T39)
  getUnderwritingQueue(statuses?: ApplicationStatus[]): Promise<QueueItem[]>;
  getUnderwritingSummary(id: string): Promise<UnderwritingSummary>;
  reviewSpot(id: string, spotId: string, body: SpotReviewRequest): Promise<ApplicationDetails>;
  addSpot(id: string, body: SpotCreate): Promise<ApplicationDetails>;
  makeDecision(id: string, body: DecisionRequest): Promise<ApplicationDetails>;
}

const realApi: Api = {
  listDemoUsers: (role) => request('GET', `/demo/users${role ? `?role=${role}` : ''}`),
  getMe: () => request('GET', '/me'),
  listProducts: () => request('GET', '/products'),
  listApplications: () => request('GET', '/applications'),
  getApplication: (id) => request('GET', `/applications/${id}`),
  createApplication: (body) => request('POST', '/applications', body),
  evaluateApplication: (id) => request('POST', `/applications/${id}/evaluate`),
  getChecks: (id) => request('GET', `/applications/${id}/checks`),
  closeSpot: (id, spotId, body) => request('POST', `/applications/${id}/spots/${spotId}/close`, body),
  uploadDocument: (id, file, kind) => {
    const form = new FormData();
    form.append('file', file);
    form.append('kind', kind);
    return request('POST', `/applications/${id}/documents`, form);
  },
  downloadDocument: (id, documentId) => requestBlob(`/applications/${id}/documents/${documentId}`),
  submitApplication: (id) => request('POST', `/applications/${id}/submit`),
  acceptOffer: (id, offerId) => request('POST', `/applications/${id}/accept`, { offerId }),
  signContract: (id) => request('POST', `/applications/${id}/sign`),
  cancelApplication: (id, reason) => request('POST', `/applications/${id}/cancel`, reason ? { reason } : {}),
  getUnderwritingQueue: (statuses) =>
    request('GET', `/underwriting/queue${statuses?.length ? `?${statuses.map((s) => `status=${s}`).join('&')}` : ''}`),
  getUnderwritingSummary: (id) => request('GET', `/underwriting/applications/${id}/summary`),
  reviewSpot: (id, spotId, body) => request('POST', `/applications/${id}/spots/${spotId}/review`, body),
  addSpot: (id, body) => request('POST', `/applications/${id}/spots`, body),
  makeDecision: (id, body) => request('POST', `/applications/${id}/decision`, body),
};

export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS === 'true';

export const api: Api = USE_MOCKS ? mockApi : realApi;