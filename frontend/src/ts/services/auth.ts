import { api, clearToken, getToken, setToken } from "./api";

export interface LoginResponse {
  token?: string;
  accessToken?: string;
  jwt?: string;
  email?: string;
  fullName?: string;
  role?: string;
}

export async function sendLoginOtp(email: string, password: string, loginAsAdmin: boolean): Promise<unknown> {
  return api.post("/api/auth/login/send-otp", { email, password, loginAsAdmin }, false);
}

export async function resendLoginOtp(email: string, password: string, loginAsAdmin: boolean): Promise<unknown> {
  return api.post("/api/auth/login/resend-otp", { email, password, loginAsAdmin }, false);
}

export async function sendPasswordResetOtp(email: string): Promise<{ message?: string }> {
  return api.post("/api/auth/forgot-password/send-otp", { email }, false);
}

export async function resetPassword(email: string, otp: string, newPassword: string, confirmPassword: string): Promise<{ message?: string }> {
  return api.post("/api/auth/forgot-password/reset", { email, otp, newPassword, confirmPassword }, false);
}

export async function startRegistration(fullName: string, email: string): Promise<{ message: string; nextStep: string; demoPassword: string }> {
  return api.post("/api/auth/register", { fullName, email }, false);
}

export async function completeRegistration(email: string, demoPassword: string, newPassword: string, confirmPassword: string): Promise<unknown> {
  return api.post("/api/auth/complete-registration", { email, demoPassword, newPassword, confirmPassword }, false);
}

export async function login(email: string, password: string, otp: string, loginAsAdmin: boolean): Promise<LoginResponse> {
  const response = await api.post<LoginResponse>("/api/auth/login", { email, password, otp, loginAsAdmin }, false);
  const token = response.token ?? response.accessToken ?? response.jwt;
  if (!token) throw new Error("The login response did not contain a JWT token.");
  setToken(token);
  sessionStorage.setItem("o-bank.email", email);
  sessionStorage.setItem("o-bank.role", response.role ?? (loginAsAdmin ? "ADMIN" : "CUSTOMER"));
  return response;
}

export function logout(): void {
  clearToken();
  sessionStorage.removeItem("o-bank.email");
  sessionStorage.removeItem("o-bank.role");
}

export function isSignedIn(): boolean { return getToken() !== null; }
export function currentEmail(): string { return sessionStorage.getItem("o-bank.email") ?? "Secure customer"; }
export function currentRole(): string { return sessionStorage.getItem("o-bank.role") ?? "CUSTOMER"; }
