import type { ApplicationStatus } from '../types';

const BASE = `${import.meta.env.VITE_API_URL ?? 'http://localhost:8080'}/api/v1`;

export type ProblemCode =
  | 'VALIDATION_ERROR'
  | 'UNAUTHORIZED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'INVALID_TRANSITION'
  | 'SOURCE_UNAVAILABLE'
  | 'NETWORK';

export class ApiError extends Error {
  status: number;
  code: ProblemCode;
  currentStatus?: ApplicationStatus;

  constructor(status: number, code: ProblemCode, message: string, currentStatus?: ApplicationStatus) {
    super(message);
    this.status = status;
    this.code = code;
    this.currentStatus = currentStatus;
  }
}

const USER_KEY = 'demoUserId';
let currentUserId: string | null = null;

export function setCurrentUser(id: string | null) {
  currentUserId = id;
  try {
    if (id) sessionStorage.setItem(USER_KEY, id);
    else sessionStorage.removeItem(USER_KEY);
  } catch {
  }
}

export function getCurrentUser(): string | null {
  if (currentUserId) return currentUserId;
  try {
    return sessionStorage.getItem(USER_KEY);
  } catch {
    return null;
  }
}

export async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const user = getCurrentUser();
  if (user) headers['X-Demo-User'] = user;
  const isForm = body instanceof FormData;
  if (body !== undefined && !isForm) headers['Content-Type'] = 'application/json';

  let res: Response;
  try {
    res = await fetch(`${BASE}${path}`, {
      method,
      headers,
    body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'NETWORK', 'Нет связи с сервером');
  }

  if (!res.ok) {
    let problem: { code?: ProblemCode; detail?: string; title?: string; currentStatus?: ApplicationStatus } = {};
    try {
      problem = await res.json();
    } catch {
    }
    throw new ApiError(
      res.status,
      problem.code ?? 'VALIDATION_ERROR',
      problem.detail ?? problem.title ?? `Ошибка ${res.status}`,
      problem.currentStatus,
    );
  }

  return (res.status === 204 ? undefined : await res.json()) as T;
}