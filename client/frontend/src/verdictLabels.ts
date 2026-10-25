import type { LlmMeta, PurposeVerdict, Spot, SpotAnswer, UnderwritingSummary } from './types';

export const LLM_WAIT_SECONDS = 90;
export const SUMMARY_POLL_MS = 3000;
export const SUMMARY_MAX_POLLS = Math.ceil((LLM_WAIT_SECONDS * 1000) / SUMMARY_POLL_MS);

export type AuthorKind = 'RULE' | 'AI' | 'HUMAN' | 'WAITING';

export interface Author {
  kind: AuthorKind;
  title: string;
  details: string[];
}

export const WAIT_TEXT = {
  checkingAnswer: `Проверяем ответ… Это может занять до ${LLM_WAIT_SECONDS} секунд.`,
  generatingSummary: 'Готовим сводку с помощью ИИ… Пока показан шаблон.',
  summaryGaveUp: 'Версия от ИИ не успела подготовиться, показан шаблон.',
  handedToSpecialist: 'Передано специалисту',
};

const shortPrompt = (v: string) => v.slice(0, 6);

export function llmTitle(llm: LlmMeta): string {
  return `ИИ · ${llm.model} · промпт ${shortPrompt(llm.promptVersion)}`;
}

function llmDetails(llm: LlmMeta): string[] {
  return [
    `Модель ${llm.model}, версия подсказки ${shortPrompt(llm.promptVersion)}.`,
    llm.cached
      ? 'Ответ взят из сохранённых: модель повторно не запускалась, поэтому результат всегда тот же.'
      : 'Ответ получен от модели и сохранён: при пересчёте он не изменится.',
  ];
}

export function purposeAuthor(v: PurposeVerdict): Author {
  if (v.status === 'UNAVAILABLE') {
    return {
      kind: 'WAITING',
      title: WAIT_TEXT.handedToSpecialist,
      details: ['ИИ-проверка сейчас недоступна. Отказа из-за этого нет: ответ посмотрит специалист банка.'],
    };
  }
  if (v.generatedBy === 'LLM' && v.llm) {
    return {
      kind: 'AI',
      title: llmTitle(v.llm),
      details: [...(v.reason ? [v.reason] : []), ...llmDetails(v.llm)],
    };
  }
  const details =
    v.status === 'NOT_APPLICABLE'
      ? ['Для этого продукта цель кредита не учитывается.']
      : v.matchedKeywords.length > 0
        ? [`Цель определена по словарю, совпали слова: ${v.matchedKeywords.join(', ')}.`]
        : ['Цель определена по словарю банка.'];
  return { kind: 'RULE', title: 'Правило', details };
}

export function answerAuthor(spot: Spot, answer: SpotAnswer | null | undefined = spot.lastAnswer): Author | null {
  if (!answer) return null;
  if (answer.purposeVerdict) return purposeAuthor(answer.purposeVerdict);
  if (answer.verdict === 'ACCEPTED' && answer.reviewedBy === 'SYSTEM') {
    return {
      kind: 'RULE',
      title: 'Правило',
      details: ['Банк проверил ответ по данным из источников и принял его без участия специалиста.'],
    };
  }
  if (answer.verdict !== 'PENDING' && answer.reviewedBy && answer.reviewedBy !== 'SYSTEM') {
    return { kind: 'HUMAN', title: 'Специалист банка', details: answer.reviewComment ? [answer.reviewComment] : [] };
  }
  return null;
}

export function summaryAuthor(s: UnderwritingSummary): Author {
  if (s.generatedBy === 'LLM' && s.llm) {
    return { kind: 'AI', title: llmTitle(s.llm), details: llmDetails(s.llm) };
  }
  return {
    kind: 'RULE',
    title: 'Шаблон',
    details: ['Сводка собрана по правилам из фактов заявки, без участия ИИ.'],
  };
}