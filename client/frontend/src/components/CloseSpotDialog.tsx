import { useState } from 'react';
import { Attach } from '@alfalab/core-components/attach';
import { Button } from '@alfalab/core-components/button';
import { Checkbox } from '@alfalab/core-components/checkbox';
import { Input } from '@alfalab/core-components/input';
import { Modal } from '@alfalab/core-components/modal';
import { Radio } from '@alfalab/core-components/radio';
import { RadioGroup } from '@alfalab/core-components/radio-group';
import { Textarea } from '@alfalab/core-components/textarea';
import { SOURCE_LABELS } from '../constants';
import type { CollateralType, SourceCode, Spot, SpotCloseRequest } from '../types';
import { WAIT_TEXT } from '../verdictLabels';
import './CloseSpotDialog.css';

type Props = {
  spot: Spot;
  initialText?: string;
  onDismiss: () => void;
  onSubmit: (body: SpotCloseRequest, file?: File) => Promise<string | null>;
};

const REVENUE_SOURCES: SourceCode[] = ['OFD', 'MARKETPLACE', 'ACQUIRING'];
const COLLATERAL_OPTIONS: { code: CollateralType; label: string }[] = [
  { code: 'GUARANTEE', label: 'Поручительство собственника' },
  { code: 'PLEDGE', label: 'Залог имущества' },
  { code: 'DECLINED', label: 'Не готов предложить обеспечение' },
];

const TITLES = {
  CONNECT_SOURCE: 'Подключить источник данных',
  ANSWER: 'Ответ банку',
  UPLOAD: 'Загрузить документ',
  PAY: 'Погасить задолженность',
  ADD_COLLATERAL: 'Залог или поручительство',
} as const;
const SUBMIT_LABELS = {
  CONNECT_SOURCE: 'Подключить',
  ANSWER: 'Отправить ответ',
  UPLOAD: 'Загрузить',
  PAY: 'Погасить',
  ADD_COLLATERAL: 'Добавить',
} as const;

const MAX_FILE_MB = 10;
const ALLOWED_TYPES = ['application/pdf', 'image/jpeg', 'image/png'];
const digits = (v: string) => v.replace(/\D/g, '');
const OTHER_OPTION = '__text';

export function CloseSpotDialog({ spot, initialText, onDismiss, onSubmit }: Props) {
  const method = spot.closeMethod;
  const [consent, setConsent] = useState(false);
  const [source, setSource] = useState<SourceCode | ''>(spot.source ?? '');
  const [option, setOption] = useState('');
  const [text, setText] = useState(spot.lastAnswer?.text ?? initialText ?? '');
  const [file, setFile] = useState<File | null>(null);
  const [amount, setAmount] = useState('');
  const [term, setTerm] = useState('');
  const [collateral, setCollateral] = useState<CollateralType | ''>('');
  const [comment, setComment] = useState('');
  const [touched, setTouched] = useState(false);
  const [loading, setLoading] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);

  if (!method) return null;

  const hasOptions = !!spot.answerOptions?.length;
  const contractRequired = spot.scoringFactor === 'debt_load';

  const errors: Record<string, string> = {};
  if (method === 'CONNECT_SOURCE' && !source) errors.source = 'Выберите, откуда взять данные';
  if (method === 'CONNECT_SOURCE' && !consent) errors.consent = 'Нужно согласие, чтобы запросить данные';
  const ownText = !hasOptions || option === OTHER_OPTION;
  if (method === 'ANSWER' && !option && hasOptions) errors.answer = 'Выберите причину или опишите своими словами';
  else if (method === 'ANSWER' && ownText && !text.trim()) errors.answer = 'Напишите ответ';
  if (method === 'UPLOAD') {
    if (!file) errors.file = 'Выберите файл';
    else if (!ALLOWED_TYPES.includes(file.type)) errors.file = 'Подойдёт PDF, JPEG или PNG';
    else if (file.size > MAX_FILE_MB * 1024 * 1024) errors.file = `Файл больше ${MAX_FILE_MB} МБ`;
    const hasAmount = !!Number(amount);
    const hasTerm = !!Number(term);
    if ((contractRequired || hasAmount || hasTerm) && (!hasAmount || !hasTerm)) {
      errors.contract = 'Укажите сумму и срок договора';
    }
  }
  if (method === 'ADD_COLLATERAL' && !collateral) errors.collateral = 'Выберите вариант';

  const submit = async () => {
    setTouched(true);
    if (Object.keys(errors).length > 0) return;

    const body: SpotCloseRequest = { method };
    if (method === 'CONNECT_SOURCE' && source) {
      body.sourceCode = source;
      body.consent = true;
    }
    if (method === 'ANSWER') {
      if (ownText) body.text = text.trim();
      else body.optionCode = option;
    }
    if (method === 'UPLOAD' && Number(amount) && Number(term)) {
      body.contractAmount = Number(amount) * 100;
      body.contractTermMonths = Number(term);
    }
    if (method === 'ADD_COLLATERAL' && collateral) {
      body.collateralType = collateral;
      if (comment.trim()) body.collateralComment = comment.trim();
    }

    setLoading(true);
    setServerError(null);
    const err = await onSubmit(body, method === 'UPLOAD' ? (file ?? undefined) : undefined);
    setLoading(false);
    if (err) setServerError(err);
  };

  const rejected = spot.lastAnswer?.verdict === 'REJECTED' ? spot.lastAnswer.reviewComment : null;

  return (
    <Modal open onClose={onDismiss} size={500}>
      <Modal.Header hasCloser title={TITLES[method]} />
      <Modal.Content>
        <div className="close-dialog">
          <p className="close-dialog__text"><b>{spot.title}</b></p>

          {rejected && <div className="close-dialog__prev">Прошлый ответ не принят: {rejected}</div>}

          {method === 'CONNECT_SOURCE' && (
            <>
              {spot.source ? (
                <p className="close-dialog__hint">
                  Банк запросит данные в источнике «{SOURCE_LABELS[spot.source]}».
                  Они нужны, чтобы закрыть это пятно и сузить вилку.
                </p>
              ) : (
                <>
                  <p className="close-dialog__hint">
                    Выберите, откуда банку взять данные о выручке. Они нужны, чтобы закрыть это пятно и сузить вилку.
                  </p>
                  <RadioGroup
                    value={source}
                    onChange={(_, payload) => setSource((payload?.value ?? '') as SourceCode | '')}
                    direction="vertical"
                  >
                    {REVENUE_SOURCES.map((code) => (
                      <Radio key={code} value={code} label={SOURCE_LABELS[code]} />
                    ))}
                  </RadioGroup>
                  {touched && errors.source && <div className="notice notice--bad">{errors.source}</div>}
                </>
              )}
              <Checkbox
                checked={consent}
                label="Согласен на получение данных из источника"
                onChange={(_, { checked }) => setConsent(checked)}
                block
              />
              {touched && errors.consent && <div className="notice notice--bad">{errors.consent}</div>}
            </>
          )}

          {method === 'ANSWER' && (
            <>
              <p className="close-dialog__hint">{spot.clientHint}</p>
                            {hasOptions && (
                <RadioGroup value={option} onChange={(_, payload) => setOption(payload?.value ?? '')} direction="vertical">
                  {spot.answerOptions!.map((o) => (
                    <Radio key={o.code} value={o.code} label={o.label} />
                  ))}
                  <Radio value={OTHER_OPTION} label="Другое — опишу своими словами" />
                </RadioGroup>
              )}
              {ownText && (
                <Textarea
                  block
                  label="Ваш ответ"
                  minRows={3}
                  maxLength={2000}
                  value={text}
                  onChange={(_, { value }) => setText(value)}
                />
              )}
              {touched && errors.answer && <div className="notice notice--bad">{errors.answer}</div>}
            </>
          )}

          {method === 'UPLOAD' && (
            <>
              <p className="close-dialog__hint">
                {spot.clientHint} PDF, JPEG или PNG, не больше {MAX_FILE_MB} МБ.
              </p>
              <Attach
                accept=".pdf,.jpg,.jpeg,.png"
                buttonContent="Выберите файл"
                noFileText="Файл не выбран"
                value={file ? [file] : null}
                onChange={(_, { files }) => setFile(files[0] ?? null)}
                onClear={() => setFile(null)}
              />
              {touched && errors.file && <div className="notice notice--bad">{errors.file}</div>}
              <div>
                <p className="close-dialog__label">
                  Сумма и срок договора{contractRequired ? '' : ' (по желанию)'}
                </p>
                <div className="close-dialog__row">
                  <Input
                    block
                    label="Сумма, ₽"
                    inputMode="numeric"
                    value={amount}
                    onChange={(_, { value }) => setAmount(digits(value))}
                  />
                  <Input
                    block
                    label="Срок, мес."
                    inputMode="numeric"
                    value={term}
                    onChange={(_, { value }) => setTerm(digits(value))}
                  />
                </div>
              </div>
              {touched && errors.contract && <div className="notice notice--bad">{errors.contract}</div>}
              <p className="close-dialog__hint">Документ проверит специалист банка, пока пятно будет «на проверке».</p>
            </>
          )}

          {method === 'PAY' && (
            <p className="close-dialog__hint">
              {spot.clientHint} После погашения банк заново запросит данные в источнике и пересчитает ставку.
            </p>
          )}

          {method === 'ADD_COLLATERAL' && (
            <>
              <p className="close-dialog__hint">{spot.clientHint}</p>
              <RadioGroup
                value={collateral}
                onChange={(_, payload) => setCollateral((payload?.value ?? '') as CollateralType | '')}
                direction="vertical"
              >
                {COLLATERAL_OPTIONS.map((o) => (
                  <Radio key={o.code} value={o.code} label={o.label} />
                ))}
              </RadioGroup>
              <Textarea
                block
                label="Комментарий (по желанию)"
                minRows={2}
                value={comment}
                onChange={(_, { value }) => setComment(value)}
              />
              {touched && errors.collateral && <div className="notice notice--bad">{errors.collateral}</div>}
            </>
          )}

          {loading && method === 'ANSWER' && <p className="close-dialog__hint">{WAIT_TEXT.checkingAnswer}</p>}
          {serverError && <div className="notice notice--bad">{serverError}</div>}
        </div>
      </Modal.Content>
      <Modal.Footer>
        <Button view="accent" size={48} loading={loading} onClick={submit}>
          {SUBMIT_LABELS[method]}
        </Button>
        <Button view="secondary" size={48} disabled={loading} onClick={onDismiss}>
          Отмена
        </Button>
      </Modal.Footer>
    </Modal>
  );
}