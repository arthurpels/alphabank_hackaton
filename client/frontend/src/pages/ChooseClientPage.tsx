import { useNavigate } from 'react-router-dom';
import { Typography } from '@alfalab/core-components/typography';
import { ArrowRightMIcon } from '@alfalab/icons-glyph/ArrowRightMIcon';
import { demoUsers } from '../mocks/demoUsers';

export default function ChooseClientPage() {
  const navigate = useNavigate();
  const clients = demoUsers.filter((u) => u.role === 'CLIENT');

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
          Выберите компанию
        </Typography.TitleResponsive>
      </div>
      <div className="hero-card">
        {clients.map((u) => (
          <button
            key={u.id}
            type="button"
            className="tile"
            onClick={() => navigate('/client/application')}
          >
            <span className="tile__text">
              <span className="tile__title">{u.personaTitle ?? u.company?.name ?? u.fio}</span>
              <span className="tile__sub">{u.fio}</span>
            </span>
            <ArrowRightMIcon className="tile__arrow" />
          </button>
        ))}
      </div>
    </div>
  );
}