export const GATEWAY_URL = "http://localhost:8443";

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

export function getToken(): string | null {
  return sessionStorage.getItem("o-bank.jwt");
}

export function setToken(token: string): void {
  sessionStorage.setItem("o-bank.jwt", token);
}

export function clearToken(): void {
  sessionStorage.removeItem("o-bank.jwt");
}

let sessionRedirecting = false;

function endExpiredSession(): void {
  if (sessionRedirecting) return;
  sessionRedirecting = true;
  clearToken();
  sessionStorage.removeItem("o-bank.email");
  sessionStorage.removeItem("o-bank.role");
  window.location.assign("index.html?ojr=login&sessionExpired=true");
}

export async function request<T>(
  path: string,
  method: "GET" | "POST" | "PUT" | "PATCH" | "DELETE" = "GET",
  body?: unknown,
  requiresAuth = true
): Promise<T> {
  const headers: Record<string, string> = {
    Accept: "application/json"
  };
  const token = getToken();
  if (requiresAuth && token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";

  const response = await fetch(`${GATEWAY_URL}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  });

  const raw = await response.text();
  let payload: unknown = raw;
  try { payload = raw ? JSON.parse(raw) : undefined; } catch { /* non-JSON response */ }

  if (!response.ok) {
    // The gateway returns either 401 or 403 when its JWT validation rejects an
    // expired or invalid customer session. Clear the stale session and show the
    // sign-in page's readable session-expired notice instead of raw HTTP text.
    if (requiresAuth && (response.status === 401 || response.status === 403)) {
      endExpiredSession();
      throw new ApiError(response.status, "Your session has ended. Please sign in again.");
    }
    const message = typeof payload === "object" && payload !== null && "message" in payload
      ? String((payload as { message: unknown }).message)
      : raw || `Request failed with status ${response.status}.`;
    throw new ApiError(response.status, message);
  }
  return payload as T;
}

export const api = {
  get: <T>(path: string, authenticated = true) => request<T>(path, "GET", undefined, authenticated),
  post: <T>(path: string, body?: unknown, authenticated = true) => request<T>(path, "POST", body, authenticated),
  put: <T>(path: string, body?: unknown, authenticated = true) => request<T>(path, "PUT", body, authenticated),
  patch: <T>(path: string, body?: unknown, authenticated = true) => request<T>(path, "PATCH", body, authenticated),
  delete: <T>(path: string, authenticated = true) => request<T>(path, "DELETE", undefined, authenticated)
};
