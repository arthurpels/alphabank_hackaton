import { Link, useNavigate } from 'react-router-dom';
import { Typography } from '@alfalab/core-components/typography';
import { ArrowRightMIcon } from '@alfalab/icons-glyph/ArrowRightMIcon';
import { BankMIcon } from '@alfalab/icons-glyph/BankMIcon';
import { BriefcaseMIcon } from '@alfalab/icons-glyph/BriefcaseMIcon';
import { ForkDecor } from '../components/ForkDecor';

export default function StartPage() {
  const navigate = useNavigate();

  return (
    <div className="hero-wrap">
      <div className="hero">
        <Typography.TitleResponsive
          tag="h1"
          view="large"
          font="system"
          color="primary-inverted"
          defaultMargins={false}
          className="hero__title"
        >
          Кредитный кабинет предпринимателя
        </Typography.TitleResponsive>
        <ForkDecor />
      </div>
      <div className="hero-card">
        <button type="button" className="tile" onClick={() => navigate('/client')}>
          <span className="tile__icon"><BriefcaseMIcon /></span>
          <span className="tile__text">
            <span className="tile__title">Действующий пользователь</span>
            <span className="tile__sub">Заявка на кредит и условия по ней</span>
          </span>
          <ArrowRightMIcon className="tile__arrow" />
        </button>
        <button type="button" className="tile" onClick={() => navigate('/underwriter')}>
          <span className="tile__icon"><BankMIcon /></span>
          <span className="tile__text">
            <span className="tile__title">Сотрудник банка</span>
            <span className="tile__sub">Очередь заявок и решения</span>
          </span>
          <ArrowRightMIcon className="tile__arrow" />
        </button>
        <Link to="/rules" className="hero-card__link">Как мы считаем ставку</Link>
      </div>
    </div>
  );
}