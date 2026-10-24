import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Typography } from '@alfalab/core-components/typography';
import { ArrowRightMIcon } from '@alfalab/icons-glyph/ArrowRightMIcon';
import { api, setCurrentUser } from '../api';
import type { DemoUser } from '../types';

export default function ChooseClientPage() {
  const navigate = useNavigate();
  const [clients, setClients] = useState<DemoUser[] | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    api
      .listDemoUsers('CLIENT')
      .then(setClients)
      .catch(() => setFailed(true));
  }, []);

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
        {failed && <p className="hero-card__empty">Не удалось загрузить список компаний. Обновите страницу.</p>}
        {!failed && !clients && <p className="hero-card__empty">Загружаем…</p>}
        {clients?.map((u) => (
          <button
            key={u.id}
            type="button"
            className="tile"
            onClick={() => {
              setCurrentUser(u.id);
              navigate('/client/application');
            }}
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