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
  listDemoUsers(role?: UserRole): Promise<DemoUser[]>; // listDemoUsers
  getApplication(id: string): Promise<ApplicationDetails>; // getApplication
  createApplication(body: ApplicationCreate): Promise<ApplicationDetails>; // createApplication
  evaluateApplication(id: string): Promise<ApplicationDetails>; // evaluateApplication
  closeSpot(id: string, spotId: string, body: SpotCloseRequest): Promise<ApplicationDetails>; // closeSpot
  submitApplication(id: string): Promise<ApplicationDetails>; // submitApplication
}

const realApi: Api = {
  listDemoUsers: (role) => request('GET', `/demo/users${role ? `?role=${role}` : ''}`),
  getApplication: (id) => request('GET', `/applications/${id}`),
  createApplication: (body) => request('POST', '/applications', body),
  evaluateApplication: (id) => request('POST', `/applications/${id}/evaluate`),
  closeSpot: (id, spotId, body) => request('POST', `/applications/${id}/spots/${spotId}/close`, body),
  submitApplication: (id) => request('POST', `/applications/${id}/submit`),
};

export const USE_MOCKS = import.meta.env.VITE_USE_MOCKS !== 'false';

export const api: Api = USE_MOCKS ? mockApi : realApi;