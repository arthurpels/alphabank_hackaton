import { Alert } from '@alfalab/core-components/alert';
import { Button } from '@alfalab/core-components/button';
import { Text } from '@alfalab/core-components/text';
import { CheckmarkCircleMIcon } from '@alfalab/icons-glyph/CheckmarkCircleMIcon';
import { CLOSE_METHOD_LABELS } from '../constants';
import type { Spot } from '../types';
import { answerAuthor, causeLabel } from '../verdictLabels';
import { AuthorBadge } from './AuthorBadge';
import './SpotCard.css';

type Props = {
  spot: Spot;
  onClose: (id: string) => void;
  busy?: boolean;
  disabled?: boolean;
};

const fmt = (v: number) => String(v).replace('.', ',');
const SECONDARY = 'var(--color-light-text-secondary)';

export function SpotCard({ spot, onClose, busy = false, disabled = false }: Props) {
  const isClosed = spot.status === 'CLOSED';
  const isReview = spot.status === 'ON_REVIEW';
  const rejectedComment =
    spot.status === 'OPEN' && spot.lastAnswer?.verdict === 'REJECTED' ? spot.lastAnswer.reviewComment : null;
    const author = answerAuthor(spot);
    const recognized = causeLabel(spot, spot.lastAnswer?.anomalyVerdict?.code);

  if (spot.type === 'STOP_FACTOR' && !isClosed) {
    return (
      <Alert view="negative" title={spot.title} className="spot-alert">
        <div>{spot.clientHint}</div>
        {rejectedComment && <div className="spot__rejected">Ответ не принят: {rejectedComment}</div>}
        {author && <AuthorBadge author={author} />}
        <div className="spot-alert__foot">
          {!spot.closeMethod ? (
            <span className="spot__review">Устранить нельзя</span>
          ) : isReview ? (
            <span className="spot__review">Передано на проверку специалисту</span>
          ) : (
            <>
              {spot.pricePp > 0 && <span className="spot__price">до −{fmt(spot.pricePp)} п.п.</span>}
              <Button view="secondary" size={40} loading={busy} disabled={disabled} onClick={() => onClose(spot.id)}>
                {CLOSE_METHOD_LABELS[spot.closeMethod]}
              </Button>
            </>
          )}
        </div>
      </Alert>
    );
  }

  return (
    <div className={`spot ${isClosed ? 'spot--closed' : ''}`}>
      <div className="spot__text">
        <div className="spot__title">
          {isClosed && <CheckmarkCircleMIcon className="spot__check" />}
          <span>{spot.title}</span>
        </div>
        {!isClosed && (
          <Text tag="div" color={SECONDARY} className="spot__desc">
            {spot.clientHint}
          </Text>
        )}
        {rejectedComment && <div className="spot__rejected">Ответ не принят: {rejectedComment}</div>}
        {author && <AuthorBadge author={author} />}
      </div>

        {recognized && <div className="spot__recognized">Мы поняли ответ так: «{recognized}»</div>}

      {!isClosed && (
        <div className="spot__side">
          {spot.closeMethod && spot.pricePp > 0 && <span className="spot__price">до −{fmt(spot.pricePp)} п.п.</span>}
          {!spot.closeMethod ? (
            <span className="spot__review">Устранить нельзя</span>
          ) : isReview ? (
            <span className="spot__review">Передано на проверку специалисту</span>
          ) : (
            <Button view="secondary" size={40} loading={busy} disabled={disabled} onClick={() => onClose(spot.id)}>
              {CLOSE_METHOD_LABELS[spot.closeMethod]}
            </Button>
          )}
        </div>
      )}
    </div>
  );
}