import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import {
  TITLES,
  acceptAndSign,
  answerSpot,
  connectSource,
  connectSourceExpectingNoData,
  createApplication,
  expectFork,
  expectScreen,
  expectSpotClosed,
  loginAsClient,
  paySpot,
  readFork,
  resetMocks,
  submitApplication,
  uploadDocument,
  type Fork,
} from './helpers.ts';

const EQUIPMENT = 'Кредит на оборудование';
const WORKING_CAPITAL = 'Кредит на пополнение оборотных средств';

interface Trace {
  forks: Array<Fork | null>;
  screen: string;
  result: string | null;
}

async function seeFork(page: Page, trace: Trace, expected: Fork) {
  trace.forks.push(await expectFork(page, expected));
}

async function coffeeShop(page: Page): Promise<Trace> {
  const trace: Trace = { forks: [], screen: '', result: null };
  await loginAsClient(page, 'Смирнова А. В.');
  await createApplication(page, {
    product: EQUIPMENT,
    amountRub: 2_000_000,
    termMonths: 36,
    goal: '2 кофемашины и витрина для второй точки',
  });
  await seeFork(page, trace, [17, 26]);

  await connectSource(page, /Продажи через кассу не видны/);
  await seeFork(page, trace, [18, 23.5]);

  await answerSpot(page, /Почему в марте выручка упала/, 'Точка закрывалась на 2 недели', /ремонт/i);
  await seeFork(page, trace, [18, 21]);

  await answerSpot(page, 'На что именно нужны деньги', '2 кофемашины и витрина');
  await expectSpotClosed(page, 'На что именно нужны деньги');
  await seeFork(page, trace, [18, 19.5]);

  await submitApplication(page);
  await expectScreen(page, TITLES.approved);
  trace.screen = TITLES.issued;
  trace.result = await acceptAndSign(page, 'approved');
  return trace;
}

async function builder(page: Page, context: BrowserContext): Promise<Trace> {
  const trace: Trace = { forks: [], screen: '', result: null };
  await loginAsClient(page, 'Петров С. Н.');
  const id = await createApplication(page, {
    product: WORKING_CAPITAL,
    amountRub: 3_000_000,
    termMonths: 12,
    goal: 'закупка материалов под договор подряда',
  });
  await seeFork(page, trace, [17, 26]);

  await connectSourceExpectingNoData(page, /Не видим выручку/, 'Эквайринг');
  await seeFork(page, trace, [17, 26]);

  await answerSpot(page, 'На что именно нужны деньги', 'закупка материалов под договор подряда');
  await expectSpotClosed(page, 'На что именно нужны деньги');
  await seeFork(page, trace, [17, 24.5]);

  await submitApplication(page);
  await expectScreen(page, TITLES.specialist);

  // андеррайтер просит договор
  const uw = await context.newPage();
  await uw.goto(`/underwriter/application?id=${id}`);
  await expect(uw.getByText('Андеррайтинг').first()).toBeVisible({ timeout: 30_000 });
  await uw.getByRole('button', { name: 'Запросить у клиента' }).click();
  await uw.locator('.uw-form input').first().fill('Договор с генподрядчиком');
  await uw.locator('.uw-form textarea').first().fill(
    'Подтвердите будущие поступления. Нет договора — подойдёт акт сверки.');
  await uw.getByRole('radio', { name: 'Долговая нагрузка' }).click();
  await uw.getByRole('button', { name: 'Отправить клиенту' }).click();
  await expect(uw.getByText('Запрос отправлен клиенту')).toBeVisible();

  await page.reload();
  await expectScreen(page, TITLES.clarify);
  await uploadDocument(page, 'Договор с генподрядчиком', 9_600_000, 12);
  await expectScreen(page, TITLES.specialist);

  await uw.reload();
  await uw.getByRole('button', { name: 'Принимаю', exact: true }).click();
  await expect(uw.getByRole('button', { name: 'Принимаю', exact: true })).toBeHidden({ timeout: 30_000 });
  await uw.getByRole('radio', { name: 'Предложить иные условия' }).click();
  await uw.getByRole('row').filter({ hasText: '1 950 000' }).filter({ hasText: '36 мес.' }).getByRole('checkbox').check({ force: true });
  await uw.locator('.uw-form textarea').last().fill('Сумма снижена под подтверждённую выручку'); // комментарий
  await uw.getByRole('button', { name: 'Вынести решение' }).click();
  await expect(uw.getByText('клиенту предложены иные условия')).toBeVisible({ timeout: 30_000 });
  await uw.close();

  await page.reload();
  await expectScreen(page, TITLES.counter);
  trace.screen = TITLES.issued;
  trace.result = await acceptAndSign(page, 'counter', '1 950 000');
  return trace;
}

async function marketplaceSeller(page: Page): Promise<Trace> {
  const trace: Trace = { forks: [], screen: '', result: null };
  await loginAsClient(page, 'Кузнецова М. И.');
  await createApplication(page, {
    product: WORKING_CAPITAL,
    amountRub: 1_500_000,
    termMonths: 12,
    goal: 'закупка товара к сезону распродаж',
  });
  await seeFork(page, trace, [17, 26]);

  await connectSource(page, /Продажи на маркетплейсе не видны/);
  await seeFork(page, trace, [20, 23]);

  await answerSpot(page, 'На что именно нужны деньги', 'закупка товара к сезону');
  await expectSpotClosed(page, 'На что именно нужны деньги');
  await seeFork(page, trace, [20, 21.5]);

  await submitApplication(page);
  await expectScreen(page, TITLES.counter);
  trace.screen = TITLES.issued;
  trace.result = await acceptAndSign(page, 'counter', '1 200 000');
  return trace;
}

async function techSupplyUntilSubmit(page: Page, trace: Trace) {
  await loginAsClient(page, 'Орлов Д. А.');
  await createApplication(page, {
    product: WORKING_CAPITAL,
    amountRub: 800_000,
    termMonths: 12,
    goal: 'закупка запчастей для перепродажи',
  });
  await seeFork(page, trace, [15, 24]);

  await connectSource(page, /Обороты по эквайрингу не видны/);
  await answerSpot(page, 'На что именно нужны деньги', 'закупка запчастей для перепродажи');
  await expectSpotClosed(page, 'На что именно нужны деньги');
  await seeFork(page, trace, [16, 17.5]);
}

async function techSupplyPaid(page: Page): Promise<Trace> {
  const trace: Trace = { forks: [], screen: '', result: null };
  await techSupplyUntilSubmit(page, trace);
  await paySpot(page, /Долг в ФНС/);
  await expectSpotClosed(page, /Долг в ФНС/);
  await seeFork(page, trace, [16, 17.5]);

  await submitApplication(page);
  await expectScreen(page, TITLES.approved);
  trace.screen = TITLES.issued;
  trace.result = await acceptAndSign(page, 'approved');
  return trace;
}

async function techSupplyUnpaid(page: Page): Promise<Trace> {
  const trace: Trace = { forks: [], screen: '', result: null };
  await techSupplyUntilSubmit(page, trace);
  await submitApplication(page);
  await expectScreen(page, TITLES.rejected);
  trace.screen = TITLES.rejected;
  trace.result = (await page.locator('.note__title').innerText()).replace(/\s+/g, ' ').trim();
  return trace;
}

async function oneDay(page: Page): Promise<Trace> {
  const trace: Trace = { forks: [], screen: '', result: null };
  await loginAsClient(page, 'Иванов И. И.');
  await createApplication(page, {
    product: WORKING_CAPITAL,
    amountRub: 2_000_000,
    termMonths: 12,
    goal: 'пополнение оборотных средств',
  });
  await expect(page.getByText('Заявка не может быть одобрена')).toBeVisible();
  trace.forks.push(await readFork(page));

  await submitApplication(page);
  await expectScreen(page, TITLES.rejected);
  trace.screen = TITLES.rejected;
  trace.result = (await page.locator('.note__title').innerText()).replace(/\s+/g, ' ').trim();
  return trace;
}

interface Scenario {
  name: string;
  run: (page: Page, context: BrowserContext) => Promise<Trace>;
  expected: Trace;
}

const scenarios: Scenario[] = [
  {
    name: 'TS-01 ООО «Кофейня»: сбор данных, автоодобрение, выдача',
    run: coffeeShop,
    expected: {
      forks: [[17, 26], [18, 23.5], [18, 21], [18, 19.5]],
      screen: TITLES.issued,
      result: '2 000 000 ₽',
    },
  },
  {
    name: 'TS-02 ИП Петров: андеррайтер, дозапрос, иные условия',
    run: builder,
    expected: {
      forks: [[17, 26], [17, 26], [17, 24.5]],
      screen: TITLES.issued,
      result: '1 950 000 ₽',
    },
  },
  {
    name: 'TS-03 ИП Кузнецова: иные условия от системы',
    run: marketplaceSeller,
    expected: {
      forks: [[17, 26], [20, 23], [20, 21.5]],
      screen: TITLES.issued,
      result: '1 200 000 ₽',
    },
  },
  {
    name: 'TS-04 ООО «Техснаб»: погасил долг — автоодобрение',
    run: techSupplyPaid,
    expected: {
      forks: [[15, 24], [16, 17.5], [16, 17.5]],
      screen: TITLES.issued,
      result: '800 000 ₽',
    },
  },
  {
    name: 'TS-05 ООО «Техснаб»: не погасил долг — отказ',
    run: techSupplyUnpaid,
    expected: {
      forks: [[15, 24], [16, 17.5]],
      screen: TITLES.rejected,
      result: 'Долг в ФНС 3 412 ₽ — без погашения будет отказ',
    },
  },
  {
    name: 'TS-06 ООО «Однодневка»: отказ по неустранимым условиям',
    run: oneDay,
    expected: {
      forks: [null],
      screen: TITLES.rejected,
      result: 'Директор назначен меньше 3 месяцев назад',
    },
  },
];

for (const scenario of scenarios) {
  test(scenario.name, async ({ context, request }) => {
    const traces: Trace[] = [];
    for (let run = 1; run <= 2; run += 1) {
      await resetMocks(request);
      const page = await context.newPage();
      traces.push(await test.step(`прогон ${run}`, () => scenario.run(page, context)));
      await page.close();
    }
    expect(traces[0], 'первый прогон совпадает с ожидаемым по docs/test-scenarios.md').toEqual(scenario.expected);
    expect(traces[1], 'второй прогон даёт те же результаты, что и первый').toEqual(traces[0]);
  });
}