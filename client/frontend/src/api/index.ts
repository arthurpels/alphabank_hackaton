import type {
  ApplicationCreate,
  ApplicationDetails,
  DemoUser,
  SpotCloseRequest,
  UserRole,
} from '../types';
import { request } from './http';
import { mockApi } from './mock';

export { ApiError, setCurrentUser, getCurrentUser } from './http';

export interface Api {
  listDemoUsers(role?: UserRole): Promise<DemoUser[]>;
  getApplication(id: string): Promise<ApplicationDetails>;
  createApplication(body: ApplicationCreate): Promise<ApplicationDetails>;
  evaluateApplication(id: string): Promise<ApplicationDetails>;
  closeSpot(id: string, spotId: string, body: SpotCloseRequest): Promise<ApplicationDetails>;
  submitApplication(id: string): Promise<ApplicationDetails>;
  acceptOffer(id: string, offerId: string): Promise<ApplicationDetails>;
  signContract(id: string): Promise<ApplicationDetails>;
  cancelApplication(id: string, reason?: string): Promise<ApplicationDetails>;
}

const realApi: Api = {
  listDemoUsers: (role) => request('GET', `/demo/users${role ? `?role=${role}` : ''}`),
  getApplication: (id) => request('GET', `/applications/${id}`),
  createApplication: (body) => request('POST', '/applications', body),
  evaluateApplication: (id) => request('POST', `/applications/${id}/evaluate`),
  closeSpot: (id, spotId, body) => request('POST', `/applications/${id}/spots/${spotId}/close`, body),
  submitApplication: (id) => request('POST', `/applications/${id}/submit`),
  acceptOffer: (id, offerId) => request('POST', `/applications/${id}/accept`, { offerId }),
  signContract: (id) => request('POST', `/applications/${id}/sign`),
  cancelApplication: (id, reason) => request('POST', `/applications/${id}/cancel`, reason ? { reason } : {}),
};

export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS !== 'false';

export const api: Api = USE_MOCKS ? mockApi : realApi;