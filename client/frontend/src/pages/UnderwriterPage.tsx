import { Typography } from '@alfalab/core-components/typography';

export default function UnderwriterPage() {
  return (
    <div className="hero-wrap">
      <div className="hero hero--compact">
        <Typography.TitleResponsive
          tag="h1"
          view="large"
          font="system"
          color="primary-inverted"
          defaultMargins={false}
          className="hero__title"
        >
          Очередь заявок
        </Typography.TitleResponsive>
      </div>
      <div className="hero-card">
        <p className="hero-card__empty">Здесь появится очередь заявок для андеррайтера.</p>
      </div>
    </div>
  );
}