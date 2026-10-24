import { useCallback, useEffect, useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import { Status } from '@alfalab/core-components/status';
import { Text } from '@alfalab/core-components/text';
import { Typography } from '@alfalab/core-components/typography';
import { ApiError, USE_MOCKS, api } from '../api';
import { ForkBar } from '../components/ForkBar';
import { CloseSpotDialog } from '../components/CloseSpotDialog';
import { SpotCard } from '../components/SpotCard';
import {
  ApprovedBlock,
  CancelButton,
  CancelledBlock,
  CheckingBlock,
  CounterOfferBlock,
  InfoRequestedBlock,
  IssuedBlock,
  RejectedBlock,
  SigningBlock,
  SpecialistBlock,
} from '../components/StatusBlocks';
import { FORK_SCALE, SOURCE_LABELS } from '../constants';
import { STATUS_META } from '../statuses';
import type { ApplicationDetails, ApplicationStatus, DecisionZone, Spot, SpotCloseRequest } from '../types';
import '../App.css';
const DOCUMENT_KIND = 'CONTRACT_GP';

const FALLBACK_ID = 'coffee-application';

const POLL_STATUSES: ApplicationStatus[] = ['SUBMITTED', 'AUTO_CHECK', 'UNDERWRITING', 'INFO_REQUESTED'];
const POLL_MS = 3000;

const fmt = (v: number) => String(v).replace('.', ',');
const SECONDARY = 'var(--color-light-text-secondary)';

const NOTICE: Record<DecisionZone, { tone: 'good' | 'mid' | 'bad'; text: string }> = {
  APPROVE: { tone: 'good', text: 'Даже в худшем случае условия подходят: решение будет принято автоматически.' },
  UNDERWRITER: { tone: 'mid', text: 'Заявку может посмотреть специалист. Закройте ещё пятна — решение будет быстрее.' },
  REJECT: { tone: 'bad', text: 'Даже если всё неизвестное окажется хорошим, условия не подходят: будет отказ.' },
};

function spotsWord(n: number) {
  const last = n % 10;
  const tens = n % 100;
  if (tens >= 11 && tens <= 14) return 'пятен';
  if (last === 1) return 'пятно';
  if (last >= 2 && last <= 4) return 'пятна';
  return 'пятен';
}

function errorText(e: unknown): string {
  if (e instanceof ApiError) {
    if (e.code === 'NETWORK') return 'Нет связи с сервером. Попробуйте ещё раз.';
    if (e.code === 'SOURCE_UNAVAILABLE') return 'Источник данных временно недоступен. Попробуйте позже.';
    if (e.code === 'INVALID_TRANSITION') return 'Это действие сейчас недоступно: заявка уже в другом статусе.';
    return e.message;
  }
  return 'Что-то пошло не так. Попробуйте ещё раз.';
}

export default function ApplicationPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const applicationId = params.get('id') ?? (USE_MOCKS ? FALLBACK_ID : null);
  const [data, setData] = useState<ApplicationDetails | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null); // 'submit', 'accept', 'sign', 'cancel', 'reset' или id пятна
  const [dialogSpot, setDialogSpot] = useState<Spot | null>(null); 

  const run = useCallback(async (key: string, call: () => Promise<ApplicationDetails>) => {
    setBusy(key);
    setError(null);
    try {
      setData(await call());
      return true;
    } catch (e) {
      setError(errorText(e));
      return false;
    } finally {
      setBusy(null);
    }
  }, []);

  useEffect(() => {
    if (!applicationId) return;
    api
      .getApplication(applicationId)
      .then(setData)
      .catch((e) => setError(errorText(e)));
  }, [applicationId]);

  const status = data?.application.status;
  useEffect(() => {
    if (!applicationId || !status || !POLL_STATUSES.includes(status)) return;
    const timer = setInterval(() => {
      api
        .getApplication(applicationId)
        .then(setData)
        .catch(() => {
        });
    }, POLL_MS);
    return () => clearInterval(timer);
  }, [status, applicationId]);

  if (!applicationId) return <Navigate to="/client/home" replace />;

  if (!data) {
    return (
      <div className="card">
        <Text tag="p" color={SECONDARY}>{error ?? 'Загружаем заявку…'}</Text>
      </div>
    );
  }

  const { application, fork, spots } = data;
  const goHome = () => navigate('/client/home');
  const submitClose = async (spot: Spot, body: SpotCloseRequest, file?: File): Promise<string | null> => {
    try {
      let request = body;
      if (file) {
        const doc = await api.uploadDocument(application.id, file, DOCUMENT_KIND);
        request = { ...body, documentId: doc.id };
      }
      setData(await api.closeSpot(application.id, spot.id, request));
      setDialogSpot(null);
      return null;
    } catch (e) {
      if (e instanceof ApiError && e.code === 'NOT_FOUND' && body.method === 'CONNECT_SOURCE' && body.sourceCode) {
        return `Источник «${SOURCE_LABELS[body.sourceCode]}» не нашёл данных по вашему ИНН. Пятно осталось открытым.`;
      }
      return errorText(e);
    }
  };
  const accept = (offerId: string) => run('accept', () => api.acceptOffer(application.id, offerId));
  const cancel = () => run('cancel', () => api.cancelApplication(application.id));

  const openSpots = spots.filter((s) => s.status !== 'CLOSED');
  const openStops = openSpots.filter((s) => s.type === 'STOP_FACTOR');
  const blocked = openStops.some((s) => s.closeMethod === null);
  const debtOpen = openStops.some((s) => s.closeMethod !== null);
  const canCloseSpots = application.availableActions.includes('CLOSE_SPOT');
  const exact = fork !== null && fork.rateFrom === fork.rateTo; // вилка сошлась в одну точку
  const notice = fork ? NOTICE[fork.zone] : null;

  const spotsSection = (
    <>
      <section className="card__section">
        <h2 className="spots__title">
          {openSpots.length > 0
            ? `Что банк о вас пока не знает: ${openSpots.length} ${spotsWord(openSpots.length)}`
            : 'Все пятна закрыты'}
        </h2>
        {spots.map((s) => (
          <SpotCard
            key={s.id}
            spot={s}
            disabled={!canCloseSpots || busy !== null}
            onClose={() => setDialogSpot(s)}
          />
        ))}
      </section>
    </>
  );

  const collecting = (
    <>
      {blocked ? (
        <section className="card__section">
          <div className="notice notice--bad">
            Заявка не может быть одобрена: есть условия, которые устранить нельзя. Они отмечены красным ниже.
          </div>
        </section>
      ) : fork && notice ? (
        <section className="card__section">
          <div className="rate-row">
            <Text tag="span" color={SECONDARY}>{exact ? 'Ставка' : 'Ставка сейчас'}</Text>
            <Typography.TitleResponsive tag="div" view="large" font="system" defaultMargins={false}>
              {exact ? `${fmt(fork.rateFrom)}%` : `от ${fmt(fork.rateFrom)}% до ${fmt(fork.rateTo)}%`}
            </Typography.TitleResponsive>
          </div>
          <Text tag="div" color={SECONDARY} align="center" className="hint">
            {exact ? (
              <div>Вилка сошлась: банк знает достаточно и называет точную ставку.</div>
            ) : (
              <>
                <div>Левый край — если всё неизвестное окажется хорошим.</div>
                <div>Правый — если плохим: так банк вынужден считать сейчас.</div>
              </>
            )}
          </Text>
          <ForkBar
            from={fork.rateFrom}
            to={fork.rateTo}
            scaleMin={FORK_SCALE.min}
            scaleMax={FORK_SCALE.max}
            approveMax={fork.thresholds.approveMax}
            rejectMin={fork.thresholds.rejectMin}
          />
          <div className={`notice notice--${notice.tone}`}>{notice.text}</div>
          <div className="checks-link">
            <Button view="text" size={32} onClick={() => navigate(`/client/check?id=${application.id}`)}>
              Что банк проверил о компании
            </Button>
          </div>
        </section>
      ) : (
        <section className="card__section">
          <Text tag="p" color={SECONDARY}>Ставка появится после расчёта.</Text>
        </section>
      )}

      {spotsSection}

      {debtOpen && !blocked && (
        <section className="card__section">
          <div className="notice notice--bad">
            Пока не устранено условие, отмеченное красным, отправленная заявка получит отказ.
          </div>
        </section>
      )}

      <footer className="card__footer">
        <Button
          view="accent"
          size={48}
          loading={busy === 'submit'}
          disabled={!application.availableActions.includes('SUBMIT') || busy !== null}
          onClick={() => run('submit', () => api.submitApplication(application.id))}
        >
          Отправить заявку
        </Button>
        {application.availableActions.includes('CANCEL') && (
          <CancelButton busy={busy === 'cancel'} onCancel={cancel} />
        )}
        {}
        {USE_MOCKS && (
          <Button
            view="secondary"
            size={48}
            disabled={busy !== null}
            onClick={() => run('reset', () => api.evaluateApplication(application.id))}
          >
            Начать заново
          </Button>
        )}
      </footer>
    </>
  );

  const body: Record<ApplicationStatus, React.ReactNode> = {
    DRAFT: (
      <section className="card__section">
        <Text tag="p" color={SECONDARY}>Заявка ещё не рассчитана.</Text>
        {application.availableActions.includes('EVALUATE') && (
          <Button
            view="accent"
            size={48}
            loading={busy === 'evaluate'}
            disabled={busy !== null}
            onClick={() =>
              run('evaluate', () => api.evaluateApplication(application.id)).then(
                (ok) => ok && navigate(`/client/check?id=${application.id}`),
              )
            }
          >
            Рассчитать
          </Button>
        )}
      </section>
    ),
    COLLECTING: collecting,
    INFO_REQUESTED: (
      <>
        <InfoRequestedBlock data={data} busy={busy === 'cancel'} onCancel={cancel} />
        {spotsSection}
      </>
    ),
    SUBMITTED: <CheckingBlock status={application.status} />,
    AUTO_CHECK: <CheckingBlock status={application.status} />,
    UNDERWRITING: <SpecialistBlock data={data} />,
    APPROVED: <ApprovedBlock data={data} busy={busy} onAccept={accept} onCancel={cancel} />,
    COUNTER_OFFER: <CounterOfferBlock data={data} busy={busy} onAccept={accept} onCancel={cancel} />,
    REJECTED: <RejectedBlock data={data} onHome={goHome} />,
    SIGNING: <SigningBlock data={data} busy={busy} onSign={() => run('sign', () => api.signContract(application.id))} />,
    ISSUED: <IssuedBlock contract={data.contract} onHome={goHome} />,
    CANCELLED: <CancelledBlock onHome={goHome} />,
  };

  return (
    <div className="card">
      <header className="card__head">
        <div>
          <Typography.TitleResponsive tag="h1" view="small" font="system" defaultMargins={false}>
            {application.product.name}
          </Typography.TitleResponsive>
          <Text tag="div" color={SECONDARY} className="card__sub">
            {application.company.name} · {(application.amount / 100).toLocaleString('ru-RU')} ₽ ·{' '}
            {application.termMonths} мес.
          </Text>
        </div>
        <Status color={STATUS_META[application.status].color} size={32} shape="rounded" uppercase={false}>
          {STATUS_META[application.status].label}
        </Status>
      </header>

      {error && (
        <section className="card__section">
          <div className="notice notice--bad">{error}</div>
        </section>
      )}

      {body[application.status]}

      {dialogSpot && (
        <CloseSpotDialog
          spot={dialogSpot}
          onDismiss={() => setDialogSpot(null)}
          onSubmit={(b, file) => submitClose(dialogSpot, b, file)}
        />
      )}
    </div>
  );
}