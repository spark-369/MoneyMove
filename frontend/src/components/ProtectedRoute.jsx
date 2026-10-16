import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/useAuth';

export default function ProtectedRoute({ children }) {
  const { isAuthenticated, initializing } = useAuth();
  const location = useLocation();

  // While the stored session is being verified we must not redirect (the token
  // may still be valid) nor render protected data with a null profile.
  if (initializing) {
    return (
      <div className="page-narrow">
        <div className="card">
          <div className="skeleton skeleton-block" />
          <div className="skeleton skeleton-block" />
          <div className="skeleton skeleton-block" />
        </div>
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }
  return children;
}
