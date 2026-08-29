import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/useAuth';
import { ApiError } from '../api/client';

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({
    username: '',
    email: '',
    password: '',
    fullName: '',
    phoneNumber: '',
  });
  const [error, setError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function update(field, value) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setSubmitting(true);
    try {
      await register({
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
        fullName: form.fullName.trim(),
        phoneNumber: form.phoneNumber.trim(),
      });
      navigate('/', { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Registration failed');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="auth-wrap">
      <form className="card auth-card" onSubmit={handleSubmit}>
        <div className="auth-hero">
          <div className="brand-mark">₿</div>
          <h1 className="card-title">Create account</h1>
          <p className="muted">Get a free account with starting balance</p>
        </div>

        {error && <div className="alert alert-danger">{error}</div>}

        <label className="field">
          <span>Username</span>
          <input
            value={form.username}
            onChange={(e) => update('username', e.target.value)}
            placeholder="min 3 characters"
            minLength={3}
            maxLength={50}
            required
          />
        </label>

        <label className="field">
          <span>Full name</span>
          <input
            value={form.fullName}
            onChange={(e) => update('fullName', e.target.value)}
            placeholder="John Doe"
            maxLength={100}
            required
          />
        </label>

        <label className="field">
          <span>Email</span>
          <input
            type="email"
            value={form.email}
            onChange={(e) => update('email', e.target.value)}
            placeholder="you@example.com"
            required
          />
        </label>

        <label className="field">
          <span>Phone number</span>
          <input
            value={form.phoneNumber}
            onChange={(e) => update('phoneNumber', e.target.value)}
            placeholder="+8801XXXXXXXXX"
          />
        </label>

        <label className="field">
          <span>Password</span>
          <input
            type="password"
            value={form.password}
            onChange={(e) => update('password', e.target.value)}
            placeholder="min 8 characters"
            minLength={8}
            maxLength={128}
            required
          />
        </label>

        <button className="btn btn-primary btn-block" type="submit" disabled={submitting}>
          {submitting ? <span className="spinner" /> : 'Create account'}
        </button>

        <p className="muted center">
          Already have an account?{' '}
          <a href="/login" onClick={(e) => { e.preventDefault(); navigate('/login'); }}>Sign in</a>
        </p>
      </form>
    </div>
  );
}
