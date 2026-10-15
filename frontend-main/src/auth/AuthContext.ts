import { createContext, use } from 'react';
import type {
  AuthState,
  LoginRequest,
  PasswordChangeRequest,
  TokenResponse,
} from './types';

export type AuthContextValue = AuthState & {
  login: (req: LoginRequest) => Promise<TokenResponse>;
  logout: () => Promise<void>;
  changePassword: (req: PasswordChangeRequest) => Promise<void>;
  refreshMe: () => Promise<void>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const ctx = use(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within <AuthProvider>');
  return ctx;
}
