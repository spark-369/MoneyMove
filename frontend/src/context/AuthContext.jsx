import { useCallback, useEffect, useMemo, useState } from 'react';
import { getToken, setToken, getUser, setUser } from '../api/client';
import * as authApi from '../api/auth';
import * as accountApi from '../api/account';
import { ApiError } from '../api/client';
import { AuthContext } from './authContext';

export function AuthProvider({ children }) {
  const [user, setUserState] = useState(() => getUser());
  const [token, setTokenState] = useState(() => getToken());
  const [loading, setLoading] = useState(false);
  const [rehydrating, setRehydrating] = useState(() => Boolean(getToken()) && !getUser());

  useEffect(() => {
    if (!rehydrating) return;
    let active = true;
    (async () => {
      try {
        const bal = await accountApi.getMyBalance();
        if (!active) return;
        setUserState((prev) => {
          const next = {
            ...(prev || {}),
            balance: bal.balance,
            currency: bal.currency,
          };
          setUser(next);
          return next;
        });
      } catch (err) {
        if (err instanceof ApiError && err.status === 401) {
          setToken(null);
          setTokenState(null);
          setUserState(null);
          setUser(null);
        }
      } finally {
        if (active) setRehydrating(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [rehydrating]);

  const applyAuthResponse = useCallback((data) => {
    const nextUser = {
      username: data.username,
      email: data.email,
      fullName: data.fullName,
      balance: data.balance,
      currency: data.currency,
    };
    setToken(data.token);
    setTokenState(data.token);
    setUser(nextUser);
    setUserState(nextUser);
  }, []);

  const login = useCallback(
    async (username, password) => {
      setLoading(true);
      try {
        const data = await authApi.login({ username, password });
        applyAuthResponse(data);
        return data;
      } finally {
        setLoading(false);
      }
    },
    [applyAuthResponse]
  );

  const register = useCallback(
    async (payload) => {
      setLoading(true);
      try {
        const data = await authApi.register(payload);
        applyAuthResponse(data);
        return data;
      } finally {
        setLoading(false);
      }
    },
    [applyAuthResponse]
  );

  const logout = useCallback(() => {
    setToken(null);
    setTokenState(null);
    setUserState(null);
    setUser(null);
  }, []);

  const updateBalance = useCallback((balance, currency) => {
    setUserState((prev) => {
      if (!prev) return prev;
      const next = { ...prev, balance, currency };
      setUser(next);
      return next;
    });
  }, []);

  const value = useMemo(
    () => ({
      user,
      token,
      loading,
      isAuthenticated: Boolean(token),
      login,
      register,
      logout,
      updateBalance,
    }),
    [user, token, loading, login, register, logout, updateBalance]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
