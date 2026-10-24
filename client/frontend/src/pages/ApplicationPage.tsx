import { useMemo, useState } from 'react';
import { Button } from '@alfalab/core-components/button';
import { Status } from '@alfalab/core-components/status';
import { Text } from '@alfalab/core-components/text';
import { Typography } from '@alfalab/core-components/typography';
import { ForkBar } from '../components/ForkBar';
import { SpotCard } from '../components/SpotCard';
import { coffeeShop } from '../mocks/coffeeShop';
import { FORK_SCALE } from '../constants';
import { STATUS_META } from '../statuses';
import '../App.css';

const round = (v: number) => Math.round(v * 10) / 10;
const fmt = (v: number) => String(v).replace('.', ',');
const SECONDARY = 'var(--color-light-text-secondary)';

export default function ApplicationPage() {
  const { application } = coffeeShop;
  const baseFork = coffeeShop.fork!;
  const [spots, setSpots] = useState(coffeeShop.spots);

  const fork = useMemo(() => {
    let from = baseFork.rateFrom;
    let to = baseFork.rateTo;
    for (const s of spots) {
      if (s.status === 'CLOSED') {
        to -= s.pricePp * 0.87;
        from += s.pricePp * 0.13;
      }
    }
    return { ...baseFork, rateFrom: round(from), rateTo: round(to) };
  }, [spots, baseFork]);

  const closeSpot = (id: string) =>
    setSpots((prev) => prev.map((s) => (s.id === id ? { ...s, status: 'CLOSED' } : s)));

  const reset = () => setSpots(coffeeShop.spots);

  const openSpots = spots.filter((s) => s.status !== 'CLOSED');

  const { approveMax, rejectMin } = fork.thresholds;

  const zone =
    fork.rateFrom >= rejectMin ? 'bad' : fork.rateTo <= approveMax ? 'good' : 'mid';

  const exact = fork.rateFrom === fork.rateTo;

  const noticeText = {
    bad: 'Даже если всё неизвестное окажется хорошим, условия не подходят: будет отказ.',
    mid: 'Заявку может посмотреть специалист. Закройте ещё пятна — решение будет быстрее.',
    good: 'Даже в худшем случае условия подходят: решение будет принято автоматически.',
  }[zone];

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
          approveMax={approveMax}
          rejectMin={rejectMin}
        />
        <div className={`notice notice--${zone}`}>{noticeText}</div>
      </section>

      <section className="card__section">
        <h2 className="spots__title">
          {openSpots.length > 0
            ? `Что банк о вас пока не знает: ${openSpots.length} пятна`
            : 'Все пятна закрыты'}
        </h2>
        {spots.map((s) => (
          <SpotCard key={s.id} spot={s} onClose={closeSpot} />
        ))}
      </section>

      <footer className="card__footer">
        <Button view="accent" size={48}>Отправить заявку</Button>
        <Button view="secondary" size={48} onClick={reset}>Начать заново</Button>
      </footer>
    </div>
  );
}
