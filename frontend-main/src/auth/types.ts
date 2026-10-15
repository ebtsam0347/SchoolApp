// Mirrors the auth-service OpenAPI: TokenResponse, MeResponse, LoginRequest,
// PasswordChangeRequest. Kept as plain TS types — Zod parsing happens on the
// http boundary (problem-detail) and at form submit (login/change-password
// schemas). These response shapes are server-controlled; we trust them.
//
// Source: docs/services/auth.openapi.json (#/components/schemas).

export type LoginRequest = {
  username: string;
  password: string;
};

export type PasswordChangeRequest = {
  oldPassword: string;
  newPassword: string;
};

export type RefreshRequest = {
  refreshToken: string;
};

export type TokenResponse = {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  sessionId: number;
  mustChangePassword: boolean;
};

export type UserIdentity = {
  userId: number;
  username: string;
  fullName: string;
  email: string | null;
  employeeLinked: boolean;
  mustChangePassword: boolean;
  roles: string[];
  permissions: string[];
  lastLoginAt: string | null;
};

export type AuthStatus =
  | 'loading' // bootstrapping from storage / awaiting /auth/me
  | 'guest' // no valid session
  | 'must-change-password' // logged in but blocked until /auth/password/change
  | 'authed';

export type AuthState =
  | { status: 'loading'; user: null }
  | { status: 'guest'; user: null }
  | { status: 'must-change-password'; user: UserIdentity }
  | { status: 'authed'; user: UserIdentity };
