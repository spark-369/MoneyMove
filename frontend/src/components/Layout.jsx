import { useEffect, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/useAuth';

function formatMoney(value, currency) {
  if (value === undefined || value === null) return '-';
  const num = typeof value === 'string' ? Number(value) : value;
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currency || 'USD',
    }).format(num);
  } catch {
    return `${num} ${currency || ''}`;
  }
}

function useTheme() {
  const [theme, setTheme] = useState(() => localStorage.getItem('mm_theme') || 'dark');
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('mm_theme', theme);
  }, [theme]);
  const toggle = () => setTheme((t) => (t === 'dark' ? 'light' : 'dark'));
  return { theme, toggle };
}

export default function Layout({ children }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const { theme, toggle } = useTheme();

  function handleLogout() {
    logout();
    navigate('/login');
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand" onClick={() => navigate('/')}>
          <span className="brand-mark">₿</span>
          <span className="brand-name">MoneyMove</span>
        </div>
        {user && (
          <nav className="nav">
            <NavLink to="/" end className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              Dashboard
            </NavLink>
            <NavLink to="/transfer" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              Send
            </NavLink>
            <NavLink to="/requests" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              Requests
            </NavLink>
            <NavLink to="/history" className={({ isActive }) => (isActive ? 'nav-link active' : 'nav-link')}>
              History
            </NavLink>
          </nav>
        )}
        <div className="topbar-right">
          <button
            className="theme-toggle"
            onClick={toggle}
            aria-label="Toggle theme"
            title="Toggle light / dark"
          >
            {theme === 'dark' ? '☀' : '☾'}
          </button>
          {user ? (
            <>
              <div className="user-chip">
                <span className="user-name">{user.fullName || user.username}</span>
                <span className="user-balance">{formatMoney(user.balance, user.currency)}</span>
              </div>
              <button className="btn btn-ghost" onClick={handleLogout}>
                Logout
              </button>
            </>
          ) : (
            <NavLink to="/login" className="btn btn-primary">
              Login
            </NavLink>
          )}
        </div>
      </header>
      <main className="content">{children}</main>
      <footer className="footer">
        <span>MoneyMove — secure money movement demo</span>
      </footer>
    </div>
  );
}
