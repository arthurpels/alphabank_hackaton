import { coffeeShop } from '../mocks/coffeeShop';
import { demoUsers } from '../mocks/demoUsers';
import type { ApplicationDetails } from '../types';
import type { Api } from './index';

let state: ApplicationDetails = structuredClone(coffeeShop);

function recalc(d: ApplicationDetails): ApplicationDetails {
  if (!d.fork) return d;
  let from = coffeeShop.fork!.rateFrom;
  let to = coffeeShop.fork!.rateTo;
  for (const s of d.spots) {
    if (s.status === 'CLOSED') {
      to -= s.pricePp * 0.87;
      from += s.pricePp * 0.13;
    }
  }
  const { approveMax, rejectMin } = d.fork.thresholds;
  const round = (v: number) => Math.round(v * 10) / 10;
  const rateFrom = round(from);
  const rateTo = round(Math.max(from, to));
  const zone = rateFrom >= rejectMin ? 'REJECT' : rateTo <= approveMax ? 'APPROVE' : 'UNDERWRITER';
  return { ...d, fork: { ...d.fork, rateFrom, rateTo, zone } };
}

const wait = <T>(v: T) => new Promise<T>((r) => setTimeout(() => r(structuredClone(v)), 150));

export const mockApi: Api = {
  listDemoUsers: (role) => wait(role ? demoUsers.filter((u) => u.role === role) : demoUsers),

  getApplication: () => wait(state),

  createApplication: () => {
    state = { ...structuredClone(coffeeShop), application: { ...coffeeShop.application, status: 'DRAFT' }, fork: null, spots: [] };
    return wait(state);
  },

  evaluateApplication: () => {
    state = { ...structuredClone(coffeeShop), application: { ...coffeeShop.application, status: 'COLLECTING' } };
    return wait(state);
  },

  closeSpot: (_appId, spotId) => {
    state = recalc({
      ...state,
      spots: state.spots.map((s) => (s.id === spotId ? { ...s, status: 'CLOSED' } : s)),
    });
    return wait(state);
  },

  submitApplication: () => {
    state = { ...state, application: { ...state.application, status: 'SUBMITTED' } };
    return wait(state);
  },
};