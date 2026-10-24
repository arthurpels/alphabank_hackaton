import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { Button } from '@alfalab/core-components/button';
import StartPage from './pages/StartPage';
import ChooseClientPage from './pages/ChooseClientPage';
import ClientHomePage from './pages/ClientHomePage';
import NewApplicationPage from './pages/NewApplicationPage';
import ApplicationPage from './pages/ApplicationPage';
import CheckPage from './pages/CheckPage';
import UnderwriterPage from './pages/UnderwriterPage';
import UnderwriterApplicationPage from './pages/UnderwriterApplicationPage';
import './pages/pages.css';

export default function App() {
  const { pathname } = useLocation();
  const navigate = useNavigate();

  return (
    <>
      {pathname !== '/' && (
        <div className={`topbar ${pathname.startsWith('/underwriter') ? 'topbar--wide' : ''}`}>
          <Button view="text" size={40} onClick={() => navigate('/')}>
            ← На главную
          </Button>
        </div>
      )}
      <Routes>
        <Route path="/" element={<StartPage />} />
        <Route path="/client" element={<ChooseClientPage />} />
        <Route path="/client/home" element={<ClientHomePage />} />
        <Route path="/client/new" element={<NewApplicationPage />} />
        <Route path="/client/check" element={<CheckPage />} />
        <Route path="/client/application" element={<ApplicationPage />} />
        <Route path="/underwriter" element={<UnderwriterPage />} />
        <Route path="/underwriter/application" element={<UnderwriterApplicationPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}