import { useAuthStore } from "./auth";
import type { ApiErrorBody } from "./types";

const API_BASE = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  status: number;
  body?: ApiErrorBody;

  constructor(status: number, message: string, body?: ApiErrorBody) {
    super(message);
    this.status = status;
    this.body = body;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = useAuthStore.getState().token;
  const headers = new Headers(options.headers);
  if (token) {
    headers.set("Authorization", `Bearer ${token}`);
  }
  if (options.body && !(options.body instanceof FormData) && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const res = await fetch(`${API_BASE}${path}`, { ...options, headers });

  if (!res.ok) {
    let body: ApiErrorBody | undefined;
    try {
      body = (await res.json()) as ApiErrorBody;
    } catch {
      // response had no JSON body; fall through with just the status text
    }
    throw new ApiError(res.status, body?.message ?? res.statusText, body);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body !== undefined ? JSON.stringify(body) : undefined }),
  patch: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PATCH", body: body !== undefined ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" }),
  upload: <T>(path: string, formData: FormData) => request<T>(path, { method: "POST", body: formData }),
};

/**
 * The native <video> element can't attach an Authorization header to the requests it makes, so
 * the JWT rides along as a query param instead — the backend's JwtAuthFilter explicitly accepts
 * that fallback for exactly this reason (see JwtAuthFilter.extractToken).
 */
export function streamUrl(videoId: string): string {
  const token = useAuthStore.getState().token ?? "";
  return `${API_BASE}/api/videos/${videoId}/stream?token=${encodeURIComponent(token)}`;
}

export function apiBaseUrl(): string {
  return API_BASE;
}
