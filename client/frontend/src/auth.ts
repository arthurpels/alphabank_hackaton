import { api, setCurrentUser } from './api';

export async function loginAsUnderwriter() {
  const users = await api.listDemoUsers('UNDERWRITER');
  if (users[0]) setCurrentUser(users[0].id);
}