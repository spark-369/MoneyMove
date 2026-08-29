import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="page-narrow center">
      <h1 className="page-title">404</h1>
      <p className="muted">The page you are looking for does not exist.</p>
      <Link to="/" className="btn btn-primary">
        Go home
      </Link>
    </div>
  );
}
