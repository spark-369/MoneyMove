# MoneyMove — Frontend

A production-grade web client for the **MoneyMove** money-movement backend (a demo banking / peer-to-peer payments API). Built with **React 19**, **Vite**, **React Router**, raw **CSS** (no UI framework), and the native **Fetch API** (no axios).

The app lets users register, log in, view balances, send money, request money, accept/decline requests, and browse transaction history — all wired to the backend REST API.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Scripts](#scripts)
- [Environment Variables](#environment-variables)
- [API Integration](#api-integration)
- [Routing & Authentication](#routing--authentication)
- [State Management](#state-management)
- [UI / UX](#ui--ux)
- [Styling](#styling)
- [Error Handling](#error-handling)
- [Building for Production](#building-for-production)
- [Backend Contract](#backend-contract)
- [Troubleshooting](#troubleshooting)

---

## Features

- **Authentication** — Register and login with JWT stored in `localStorage`.
- **Dashboard** — Live balance card plus recent activity preview.
- **Send money** — Transfer to any username with amount, description, and optional idempotency key; includes recipient username search.
- **Transfer lookup** — Retrieve any transfer by its reference ID.
- **Money requests** — Create requests, view incoming/outgoing, and accept (pay) or decline pending requests.
- **History** — Full, sortable-by-time transaction table.
- **Protected routes** — Unauthenticated users are redirected to login.
- **Light / Dark theme** — Toggle persisted in `localStorage`.
- **Loading & empty states** — Shimmer skeletons and friendly empty states.
- **Responsive** — Mobile-first layout with collapsing navigation.

---

## Tech Stack

| Concern        | Choice                                            |
| -------------- | ------------------------------------------------- |
| Framework      | React 19                                          |
| Build tool     | Vite 8                                            |
| Routing        | React Router 7 (`react-router-dom`)               |
| HTTP client    | Native `fetch` (no axios)                         |
| Language       | JavaScript (`.jsx` / `.js`) — no TypeScript       |
| Styling        | Raw CSS with CSS custom properties (design tokens)|
| Linting        | ESLint (flat config)                              |
| State          | React Context + hooks (no Redux)                  |

---

## Project Structure

```
frontend/
├── index.html                 # Vite HTML entry
├── vite.config.js            # Vite + React plugin
├── package.json
├── public/
│   └── favicon.svg
└── src/
    ├── main.jsx              # React root, mounts <App/>
    ├── App.jsx               # Router + AuthProvider + Layout composition
    ├── index.css             # Global design system (tokens, components, themes)
    ├── api/                  # API layer (fetch wrappers)
    │   ├── client.js         # Base fetch client, auth headers, ApiError, token storage
    │   ├── auth.js           # register(), login()
    │   ├── account.js        # balance, history, user search
    │   ├── transfer.js       # transfer(), getByReference()
    │   └── requests.js       # create/accept/decline/list requests
    ├── context/
    │   ├── authContext.js    # createContext instance
    │   ├── AuthContext.jsx   # AuthProvider (state + actions)
    │   └── useAuth.js        # useAuth() hook
    ├── components/
    │   ├── Layout.jsx        # Topbar, nav, theme toggle, footer
    │   └── ProtectedRoute.jsx# Redirects unauthenticated users
    ├── pages/
    │   ├── LoginPage.jsx
    │   ├── RegisterPage.jsx
    │   ├── DashboardPage.jsx
    │   ├── TransferPage.jsx
    │   ├── RequestsPage.jsx
    │   ├── HistoryPage.jsx
    │   └── NotFoundPage.jsx
    └── utils/
        └── format.js         # Money / date / status-badge formatters
```

---

## Getting Started

### Prerequisites

- **Node.js** >= 18 (developed on Node 24)
- The **MoneyMove backend** running and reachable (default `http://localhost:8080`). See the backend README for setup (PostgreSQL + Redis required by the API).

### Install & Run

```bash
# from the frontend directory
npm install
npm run dev
```

The app starts on **http://localhost:5173** (Vite default). Open it in a browser and register a new account (the backend seeds a starting balance on registration).

---

## Scripts

| Command           | Description                                  |
| ----------------- | -------------------------------------------- |
| `npm run dev`     | Start the Vite dev server with HMR           |
| `npm run build`   | Production build into `dist/`                |
| `npm run preview` | Preview the production build locally         |
| `npm run lint`    | Run ESLint over the source tree              |

---

## Environment Variables

The API base URL is configurable via Vite env vars (`.env` files or inline). All variables are read at build/runtime through `import.meta.env`.

| Variable             | Default                       | Purpose                              |
| -------------------- | ----------------------------- | ------------------------------------ |
| `VITE_API_BASE_URL`  | `http://localhost:8080/api`   | Base URL for all backend API calls   |

Example `.env`:

```bash
# .env.local
VITE_API_BASE_URL=http://localhost:8080/api
```

> The trailing `/api` is required — the backend exposes endpoints under `/api/*`.

---

## API Integration

All network access goes through `src/api/client.js`, a thin `fetch` wrapper that:

- Prefixes requests with `VITE_API_BASE_URL`.
- Adds `Content-Type: application/json` and, when authenticated, an `Authorization: Bearer <token>` header.
- Serializes the request body to JSON.
- Supports query-string parameters via a `query` option.
- Normalizes errors into a typed `ApiError` (with `status` and `data`) and extracts a human-readable `message` from the backend's `ApiError` JSON shape (`{ status, error, message }`).
- On `401`, automatically clears the stored token (forcing re-login).
- Throws a friendly "Network error" when the backend is unreachable.

Per-domain helpers live in `src/api/*.js` and mirror the backend controllers:

| Module            | Function                              | Method & Path                                  |
| ----------------- | ------------------------------------- | ---------------------------------------------- |
| `auth.js`         | `register(payload)`                   | `POST /api/auth/register`                      |
| `auth.js`         | `login({username, password})`         | `POST /api/auth/login`                         |
| `account.js`      | `getMyBalance()`                      | `GET  /api/account/me/balance`                |
| `account.js`      | `getBalance(username)`                | `GET  /api/account/balance/{username}`         |
| `account.js`      | `getHistory()`                        | `GET  /api/account/me/history`                |
| `account.js`      | `searchUsers(q)`                      | `GET  /api/account/users/search?q=`            |
| `transfer.js`     | `transfer(payload)`                   | `POST /api/transfers`                          |
| `transfer.js`     | `getTransferByReference(ref)`         | `GET  /api/transfers/{referenceId}`            |
| `requests.js`     | `createRequest(payload)`              | `POST /api/requests`                           |
| `requests.js`     | `acceptRequest(id)`                   | `POST /api/requests/{id}/accept`               |
| `requests.js`     | `declineRequest(id)`                  | `POST /api/requests/{id}/decline`              |
| `requests.js`     | `listIncomingRequests()`              | `GET  /api/requests/incoming`                  |
| `requests.js`     | `listOutgoingRequests()`              | `GET  /api/requests/outgoing`                  |

### Example call

```js
import * as transferApi from '../api/transfer';

const tx = await transferApi.transfer({
  toUsername: 'alice',
  amount: '25.00',
  description: 'Dinner',
  idempotencyKey: 'unique-key-123', // optional, prevents duplicate transfers
});
console.log(tx.referenceId, tx.status);
```

---

## Routing & Authentication

Routing is defined in `src/App.jsx` using `react-router-dom`. Authenticated routes are wrapped in `<ProtectedRoute>`, which redirects to `/login` (preserving the intended destination via router `state`) when no token is present.

| Path         | Page              | Auth required |
| ------------ | ----------------- | ------------- |
| `/login`     | LoginPage         | No            |
| `/register`  | RegisterPage      | No            |
| `/`          | DashboardPage     | Yes           |
| `/transfer`  | TransferPage      | Yes           |
| `/requests`  | RequestsPage      | Yes           |
| `/history`   | HistoryPage       | Yes           |
| `/404`       | NotFoundPage      | No            |
| `*`          | → redirect `/404` | —             |

The JWT is stored in `localStorage` under the key `mm_token` (see `src/api/client.js`). It is attached to every authenticated request and cleared on logout or on a `401` response.

---

## State Management

Global auth state is provided by `AuthProvider` (`src/context/AuthContext.jsx`) and consumed via the `useAuth()` hook (`src/context/useAuth.js`). The context exposes:

```js
{
  user,            // { username, email, fullName, balance, currency }
  token,           // raw JWT string or null
  loading,         // in-flight auth request flag
  isAuthenticated, // Boolean(token)
  login,           // (username, password) => Promise<AuthResponse>
  register,        // (payload) => Promise<AuthResponse>
  logout,          // () => void
  updateBalance,   // (balance, currency) => void  (keeps UI in sync after transfers)
}
```

After a successful transfer or request payment, pages call `updateBalance(...)` with a fresh `getMyBalance()` result so the topbar and dashboard reflect the new balance without a full reload.

---

## UI / UX

- **Theme toggle** — Light/dark switch in the topbar, persisted under `mm_theme` in `localStorage` and applied via `data-theme` on `<html>`.
- **Loading states** — Shimmer skeletons while lists/balances load.
- **Empty states** — Centered illustrations and helper copy when there is no data.
- **Buttons** — Inline spinners during async actions; disabled while submitting.
- **Alerts** — Color-coded success/danger banners with status dots.
- **Accessibility** — Semantic HTML, focus-visible rings, `aria-label` on icon buttons, and `prefers-reduced-motion` support.

---

## Styling

Styling is **raw CSS** (no Tailwind/Bootstrap). The single stylesheet `src/index.css` defines:

- **Design tokens** as CSS custom properties (`:root` for dark, `:root[data-theme='light']` for light) — colors, radii, shadows, spacing, easing.
- **Component classes** — `.card`, `.btn*`, `.field`, `.badge*`, `.table`, `.tabs`, `.skeleton`, `.empty`, `.alert*`, etc.
- **Motion** — `fade-up`, `shimmer`, and `spin` keyframes; all disabled under `prefers-reduced-motion`.
- **Responsive** — A `max-width: 720px` breakpoint collapses the nav and tightens spacing.

To retheme, edit the custom properties at the top of `index.css`; no component markup changes are required.

---

## Error Handling

- Network/HTTP errors are caught in the API layer and surfaced as `ApiError`.
- Pages catch these and render an `.alert.alert-danger` banner with the backend's message (e.g. "Insufficient balance", "User not found").
- Validation errors from the backend (`MethodArgumentNotValidException`) are flattened into a single readable string via `client.js`'s `extractErrorMessage`.
- A `401` automatically logs the user out and redirects to login.

---

## Building for Production

```bash
npm run build      # outputs to dist/
npm run preview    # serve the built bundle locally
```

Deploy the contents of `dist/` to any static host (Netlify, Vercel, GitHub Pages, S3 + CloudFront, Nginx, etc.). For client-side routing on a static host, configure a catch-all rewrite to `index.html` so deep links (e.g. `/transfer`) resolve correctly.

> **CORS:** The backend (`SecurityConfig`) allows all origins during development. For production, restrict `allowedOriginPatterns` to your frontend's domain.

---

## Backend Contract

The frontend expects the backend to expose the endpoints listed in [API Integration](#api-integration) under `/api`. Key response shapes:

- **AuthResponse** — `{ token, username, email, fullName, balance, currency }`
- **BalanceResponse** — `{ balance, currency }`
- **TransactionResponse** — `{ referenceId, type, status, senderUsername, receiverUsername, amount, currency, description, createdAt }`
- **MoneyRequestResponse** — `{ id, requesterUsername, debtorUsername, amount, currency, note, status, createdAt, resolvedAt }`
- **ApiError** — `{ status, error, message }`

The backend caches balances in Redis (TTL 60s) and evicts the cache on transfers/request settlements, so the UI's balance refreshes are eventually consistent within that window.

---

## Troubleshooting

| Symptom                                  | Likely cause / fix                                                                 |
| ---------------------------------------- | ---------------------------------------------------------------------------------- |
| `Network error. Is the backend running?` | Backend not started or wrong `VITE_API_BASE_URL`. Verify it responds on `:8080/api`. |
| `401` immediately after login            | JWT secret / token mismatch, or API base URL missing `/api`.                        |
| CORS error in console                    | Backend CORS not permitting the frontend origin; check `SecurityConfig`.            |
| Styles look unstyled                     | `index.css` not imported in `main.jsx` (it is, by default).                        |
| Deep link returns 404 on static host     | Add an SPA fallback rewrite to `index.html`.                                        |

---

## License

This project is part of the MoneyMove demo and is provided as-is for hackathon / educational use.
