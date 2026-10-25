import type { AnomalyCause, AnomalyVerdict, LlmMeta, PurposeVerdict, Spot, SpotAnswer, UnderwritingSummary } from './types';

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
   if (v.profile?.status === 'DOES_NOT_FIT') {
    const p = v.profile;
    return {
      kind: 'WAITING',
      title: WAIT_TEXT.handedToSpecialist,
      details: [
        `Цель подходит продукту, но не похожа на деятельность компании${p.activity ? ` (${p.activity})` : ''}. Это не отказ: ответ посмотрит специалист банка.`,
        ...(p.evidence ? [`В вашем тексте: «${p.evidence}».`] : []),
        ...(p.reason ? [p.reason] : []),
        ...(p.llm ? llmDetails(p.llm) : []),
      ],
    };
  }
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

const CAUSE_LABELS: Record<AnomalyCause, string> = {
  RENOVATION: 'Был ремонт',
  SEASONAL: 'Сезонный спад',
  CLIENT_LOST: 'Ушёл крупный клиент',
  OTHER: 'Другая причина',
};

const RULE_LABELS: Record<string, string> = {
  RENOVATION_ONE_OFF: 'падение было разовым, выручка вернулась',
  SEASONAL_LAST_YEAR: 'такой же спад был в прошлом году',
};

export function causeLabel(spot: Spot, code: AnomalyCause | null | undefined): string | null {
  if (!code) return null;
  return spot.answerOptions?.find((o) => o.code === code)?.label ?? CAUSE_LABELS[code];
}

export function anomalyAuthor(spot: Spot, v: AnomalyVerdict): Author {
  const cause = causeLabel(spot, v.code);
  const c = v.classification;
  const how: string[] = [];
  if (cause) {
    if (!c) how.push(`Вы выбрали вариант «${cause}».`);
    else if (c.generatedBy === 'LLM' && c.llm) how.push(`Причину из вашего текста определил ИИ: «${cause}». ${llmDetails(c.llm).join(' ')}`);
    else if (c.matchedKeywords.length > 0) how.push(`Причина «${cause}» определена по словам: ${c.matchedKeywords.join(', ')}.`);
    else how.push(`Причина определена как «${cause}».`);
  }
  if (c?.violation) {
    how.push('Ответ похож на попытку повлиять на проверку, поэтому причину «Другая» посмотрит специалист.');
  }
  if (c?.reason) how.push(c.reason);

  if (v.check?.verdict === 'ACCEPTED') {
    const rule = v.check.rule ? RULE_LABELS[v.check.rule] ?? v.check.rule : null;
    return {
      kind: 'RULE',
      title: 'Подтверждено данными',
      details: [...how, v.check.explanation, ...(rule ? [`Правило проверки: ${rule}.`] : [])],
    };
  }
  if (!v.code) {
    return {
      kind: 'WAITING',
      title: WAIT_TEXT.handedToSpecialist,
      details: ['ИИ-проверка сейчас недоступна, причину по вашему тексту определить не удалось. Отказа из-за этого нет: ответ посмотрит специалист банка.'],
    };
  }
  return {
    kind: 'WAITING',
    title: WAIT_TEXT.handedToSpecialist,
    details: [...how, v.check?.explanation ?? 'Эту причину нельзя проверить данными: ответ посмотрит специалист банка.'],
  };
}

export function answerAuthor(spot: Spot, answer: SpotAnswer | null | undefined = spot.lastAnswer): Author | null {
  if (!answer) return null;
  if (answer.anomalyVerdict) return anomalyAuthor(spot, answer.anomalyVerdict);
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