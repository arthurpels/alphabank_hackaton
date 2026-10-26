import type {
  AnomalyCause,
  AnomalyVerdict,
  LlmMeta,
  PurposeProfile,
  PurposeVerdict,
  Spot,
  SpotAnswer,
  UnderwritingSummary,
} from './types';

export const LLM_WAIT_SECONDS = 90;
export const SUMMARY_POLL_MS = 3000;
export const SUMMARY_MAX_POLLS = Math.ceil((LLM_WAIT_SECONDS * 1000) / SUMMARY_POLL_MS);

export type Audience = 'client' | 'staff';
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

const CLIENT_AI_DETAILS = 'Ответ разобрал ИИ по правилам банка. Решение по заявке принимают правила, а не ИИ.';
const plainActivity = (a: string) => a.replace(/^ОКВЭД\s*[\d.]+\s*[—-]\s*/, '');

export function purposeAuthor(v: PurposeVerdict, audience: Audience = 'staff'): Author {
  const client = audience === 'client';
  if (v.status === 'DECIDED' && v.purpose === 'MISMATCH') {
    return {
      kind: 'WAITING',
      title: WAIT_TEXT.handedToSpecialist,
      details: [
        'Цель не похожа на выбранный продукт. Это не отказ: ответ посмотрит специалист банка.',
        ...(v.evidence ? [`В вашем тексте: «${v.evidence}».`] : []),
        ...(!client && v.reason ? [v.reason] : []),
        ...(!client && v.llm ? llmDetails(v.llm) : []),
      ],
    };
  }
  if (v.profile?.status === 'DOES_NOT_FIT') {
    const p = v.profile;
    return {
      kind: 'WAITING',
      title: WAIT_TEXT.handedToSpecialist,
      details: [
        `Цель подходит продукту, но не похожа на деятельность компании${p.activity ? ` (${client ? plainActivity(p.activity) : p.activity})` : ''}. Это не отказ: ответ посмотрит специалист банка.`,
        ...(p.evidence ? [`В вашем тексте: «${p.evidence}».`] : []),
        ...(!client && p.reason ? [p.reason] : []),
        ...(!client && p.llm ? llmDetails(p.llm) : []),
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
    if (client) return { kind: 'AI', title: 'Цель распознана ИИ', details: [CLIENT_AI_DETAILS] };
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
        ? [`Цель определена по словам из вашего ответа: ${v.matchedKeywords.join(', ')}.`]
        : ['Цель определена автоматически.'];
  return { kind: 'RULE', title: client ? 'Проверено автоматически' : 'Правило', details };
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

export function anomalyAuthor(spot: Spot, v: AnomalyVerdict, audience: Audience = 'staff'): Author {
  const client = audience === 'client';
  const cause = causeLabel(spot, v.code);
  const c = v.classification;
  const how: string[] = [];
  if (cause) {
    if (!c) how.push(`Вы выбрали вариант «${cause}».`);
    else if (c.generatedBy === 'LLM' && c.llm) how.push(`Причину из вашего текста определил ИИ: «${cause}». ${client ? '' : llmDetails(c.llm).join(' ')}`.trim());
    else if (c.matchedKeywords.length > 0) how.push(`Причина «${cause}» определена по словам: ${c.matchedKeywords.join(', ')}.`);
    else how.push(`Причина определена как «${cause}».`);
  }
  if (c?.violation) {
    how.push('Ответ похож на попытку повлиять на проверку, поэтому причину «Другая» посмотрит специалист.');
  }
  if (!client && c?.reason) how.push(c.reason);

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

export function answerAuthor(
  spot: Spot,
  answer: SpotAnswer | null | undefined = spot.lastAnswer,
  audience: Audience = 'staff',
): Author | null {
  if (!answer) return null;
  if (answer.verdict !== 'PENDING' && answer.reviewedBy && answer.reviewedBy !== 'SYSTEM') {
    return { kind: 'HUMAN', title: 'Специалист банка', details: answer.reviewComment ? [answer.reviewComment] : [] };
  }
  if (answer.anomalyVerdict) return anomalyAuthor(spot, answer.anomalyVerdict, audience);
  if (answer.purposeVerdict) return purposeAuthor(answer.purposeVerdict, audience);
  if (answer.verdict === 'ACCEPTED' && answer.reviewedBy === 'SYSTEM') {
    return {
      kind: 'RULE',
      title: audience === 'client' ? 'Проверено автоматически' : 'Правило',
      details: ['Банк проверил ответ по данным из источников и принял его без участия специалиста.'],
    };
  }
  return null;
}

const PURPOSE_VERDICT_LABELS: Record<'MATCH' | 'MISMATCH', string> = {
  MATCH: 'цель подходит продукту',
  MISMATCH: 'цель не подходит продукту',
};

const PROFILE_VERDICT_LABELS: Record<PurposeProfile['status'], string> = {
  FITS: 'похожа на деятельность компании',
  DOES_NOT_FIT: 'не похожа на деятельность компании',
  UNCLEAR: 'связь с деятельностью неочевидна',
  NOT_CHECKED: 'с деятельностью не сверяли',
};

const VIOLATION_LABELS: Record<string, string> = {
  FORMAT: 'ответ модели не по формату',
  NO_EVIDENCE: 'модель не привела цитату',
  EVIDENCE_NOT_IN_GOAL: 'цитаты модели нет в тексте клиента',
  GENERIC_EVIDENCE: 'в цитате только общие слова',
  UNCERTAIN_REASON: 'модель сомневается',
  UNIVERSAL_PURCHASE: 'трата нужна любому бизнесу',
  MANIPULATION: 'похоже на попытку повлиять на проверку',
};

export interface ModelVerdict {
  title: string;
  evidence: string | null;
  reason: string | null;
  violation: string | null;
}

function purposeVerdictTitle(v: PurposeVerdict): string {
  if (v.status === 'NOT_APPLICABLE') return 'цель для продукта не учитывается';
  if (v.status === 'UNAVAILABLE') return 'ИИ-проверка недоступна';
  if (!v.purpose) return 'цель не понятна';
  const profile = v.profile && v.profile.status !== 'NOT_CHECKED' ? `, ${PROFILE_VERDICT_LABELS[v.profile.status]}` : '';
  return PURPOSE_VERDICT_LABELS[v.purpose] + profile;
}

export function modelVerdict(spot: Spot, answer: SpotAnswer): ModelVerdict | null {
  const p = answer.purposeVerdict;
  if (p) {
    const profile = p.profile?.status === 'DOES_NOT_FIT' || p.profile?.status === 'UNCLEAR' ? p.profile : null;
    const violation = p.violation ?? profile?.violation ?? null;
    return {
      title: purposeVerdictTitle(p),
      evidence: p.evidence ?? profile?.evidence ?? null,
      reason: [p.reason, profile?.reason].filter(Boolean).join(' ') || null,
      violation: violation ? VIOLATION_LABELS[violation] ?? violation : null,
    };
  }
  const c = answer.anomalyVerdict?.classification;
  if (c) {
    const cause = causeLabel(spot, c.code);
    return {
      title: cause ? `причина: ${cause}` : 'причину определить не удалось',
      evidence: c.evidence ?? null,
      reason: c.reason ?? null,
      violation: c.violation ? VIOLATION_LABELS[c.violation] ?? c.violation : null,
    };
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