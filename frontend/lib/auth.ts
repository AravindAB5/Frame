import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { User } from "./types";

/**
 * Token lives in localStorage (via zustand's persist middleware), not an httpOnly cookie.
 * Trade-off: simpler to build (no CSRF handling, no server-side session), but readable by any
 * script on the page (XSS risk) — acceptable for a portfolio demo, called out explicitly in
 * ARCHITECTURE.md as something a production deployment should revisit (httpOnly cookie + CSRF
 * token, or a short-lived token refreshed via a same-site cookie).
 */
interface AuthState {
  token: string | null;
  user: User | null;
  setAuth: (token: string, user: User) => void;
  clearAuth: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      clearAuth: () => set({ token: null, user: null }),
    }),
    { name: "frame-auth" }
  )
);
