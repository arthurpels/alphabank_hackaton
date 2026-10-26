import { execFileSync } from 'node:child_process';
import path from 'node:path';
import { expect, type Locator, type Page } from '@playwright/test';

export const RESET_SCRIPT = path.resolve(process.cwd(), '../../scripts/reset-demo');
export const LLM_WAIT_MS = 100_000; // дольше таймаута ИИ на бэкенде (90 с)

export type Fork = [number, number];

export function resetDemo() {
  if (process.env.E2E_NO_RESET) return;
  execFileSync(RESET_SCRIPT, { stdio: 'pipe', timeout: 120_000 });
}

export async function loginAsClient(page: Page, fio: string) {
  await page.goto('/client');
  await page.locator('.tile').filter({ hasText: fio }).click();
  await page.waitForURL(/\/client\/home/);
}

export interface NewApplication {
  product: string;
  amountRub: number;
  termMonths: number;
  goal: string;
}

export async function createApplication(page: Page, a: NewApplication): Promise<string> {
  await page.getByRole('button', { name: 'Новая заявка' }).first().click();
  await page.waitForURL(/\/client\/new/);
  await page.getByRole('radio', { name: a.product }).click();
  await page.locator('input[inputmode="numeric"]').fill(String(a.amountRub));
  await page.getByRole('radio', { name: `${a.termMonths} мес.`, exact: true }).click();
  await page.locator('textarea').first().fill(a.goal);
  await page.getByText('Согласен на запрос кредитной истории в БКИ').click();
  await page.getByText('Согласен на обработку персональных данных').click();
  await page.getByRole('button', { name: 'Рассчитать' }).click();

  await page.waitForURL(/\/client\/check\?id=/);
  const id = new URL(page.url()).searchParams.get('id');
  expect(id, 'в адресе должен быть id заявки').toBeTruthy();
  await page.getByRole('button', { name: /Посмотреть ставку|Перейти к заявке/ }).click({ timeout: 30_000 });
  await page.waitForURL(/\/client\/application/);
  return id!;
}

export async function readFork(page: Page): Promise<Fork | null> {
  const row = page.locator('.rate-row');
  if ((await row.count()) === 0) return null;
  const text = (await row.innerText()).replace(/\s+/g, ' ');
  const nums = [...text.matchAll(/(\d+(?:,\d+)?)\s*%/g)].map((m) => Number(m[1].replace(',', '.')));
  if (nums.length === 0) return null;
  return [nums[0], nums[nums.length - 1]];
}

export async function expectFork(page: Page, expected: Fork): Promise<Fork> {
  await expect.poll(() => readFork(page), { message: 'ставка на экране', timeout: 30_000 }).toEqual(expected);
  return expected;
}

const spotCard = (page: Page, title: string | RegExp) =>
  page.locator('.spot, .spot-alert').filter({ hasText: title });

const dialog = (page: Page) => page.getByRole('dialog');

export const radio = (scope: Page | Locator, label: string | RegExp) =>
  scope.locator('label').filter({ hasText: label });

export async function expectSpotClosed(page: Page, title: string | RegExp) {
  await expect(spotCard(page, title)).toHaveClass(/spot--closed/, { timeout: 30_000 });
}

export async function connectSource(page: Page, title: string | RegExp, source?: string) {
  await spotCard(page, title).getByRole('button', { name: 'Подключить', exact: true }).click();
  const dlg = dialog(page);
  if (source) await radio(dlg, source).click();
  await dlg.getByText('Согласен на получение данных из источника').click();
  await dlg.getByRole('button', { name: 'Подключить', exact: true }).click();
  await expect(dlg).toBeHidden({ timeout: 30_000 });
}

export async function connectSourceExpectingNoData(page: Page, title: string | RegExp, source: string) {
  await spotCard(page, title).getByRole('button', { name: 'Подключить', exact: true }).click();
  const dlg = dialog(page);
  await radio(dlg, source).click();
  await dlg.getByText('Согласен на получение данных из источника').click();
  await dlg.getByRole('button', { name: 'Подключить', exact: true }).click();
  await expect(dlg).toContainText('не нашёл данных по вашему ИНН');
  await dlg.getByRole('button', { name: 'Отмена' }).click();
  await expect(dlg).toBeHidden();
}

export async function answerSpot(page: Page, title: string | RegExp, text: string, option?: RegExp) {
  await spotCard(page, title).getByRole('button', { name: 'Ответить', exact: true }).click();
  const dlg = dialog(page);
  if (option) await radio(dlg, option).click();
  else await dlg.locator('textarea').first().fill(text);
  await dlg.getByRole('button', { name: 'Отправить ответ' }).click();
  await expect(dlg).toBeHidden({ timeout: LLM_WAIT_MS });
}

export async function paySpot(page: Page, title: string | RegExp) {
  await spotCard(page, title).getByRole('button', { name: 'Погасить', exact: true }).click();
  const dlg = dialog(page);
  await dlg.getByRole('button', { name: 'Погасить', exact: true }).click();
  await expect(dlg).toBeHidden({ timeout: 30_000 });
}

export async function uploadDocument(page: Page, title: string | RegExp, amountRub: number, termMonths: number) {
  await spotCard(page, title).getByRole('button', { name: 'Загрузить', exact: true }).click();
  const dlg = dialog(page);
  await dlg.locator('input[type="file"]').setInputFiles({
    name: 'contract.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF'),
  });
  const numbers = dlg.locator('input[inputmode="numeric"]');
  await numbers.nth(0).fill(String(amountRub));
  await numbers.nth(1).fill(String(termMonths));
  await dlg.getByRole('button', { name: 'Загрузить', exact: true }).click();
  await expect(dlg).toBeHidden({ timeout: 30_000 });
}

export const TITLES = {
  approved: 'Заявка одобрена',
  counter: 'Банк предлагает другие условия',
  rejected: 'Мы не можем одобрить заявку',
  specialist: 'Заявку смотрит специалист',
  clarify: 'Специалисту нужны уточнения',
  signing: 'Оформление договора',
  issued: 'Деньги зачислены',
} as const;

export async function expectScreen(page: Page, title: string) {
  await expect(page.getByRole('heading', { name: title })).toBeVisible({ timeout: 60_000 });
}

export async function submitApplication(page: Page) {
  await page.getByRole('button', { name: 'Отправить заявку' }).click();
}

export async function acceptAndSign(page: Page, screen: 'approved' | 'counter', pickOffer?: string): Promise<string> {
  if (screen === 'approved') {
    await page.getByRole('button', { name: 'Перейти к оформлению' }).click();
  } else {
    await page.locator('.offer').filter({ hasText: pickOffer ?? '' }).click();
    await page.getByRole('button', { name: 'Принять выбранный вариант' }).click();
  }
  await expectScreen(page, TITLES.signing);
  await page.getByRole('button', { name: 'Подписать договор' }).click();
  await expectScreen(page, TITLES.issued);
  return (await page.locator('.hero-block__figure span').first().innerText()).replace(/\s+/g, ' ').trim();
}