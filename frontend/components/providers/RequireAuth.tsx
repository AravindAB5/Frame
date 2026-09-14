"use client";

import { useEffect, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/lib/auth";

const noopSubscribe = () => () => {};

/**
 * Client-only auth is fine here (no server-rendered protected data — everything protected is
 * fetched client-side via TanStack Query), but it means we can't know on the server whether the
 * user is signed in. `useSyncExternalStore` with a server snapshot of `false` gives a stable,
 * hydration-safe "have we reached the client yet" flag without the cascading-render anti-pattern
 * of calling setState from inside a useEffect body.
 */
function useHasMounted(): boolean {
  return useSyncExternalStore(
    noopSubscribe,
    () => true,
    () => false
  );
}

export function RequireAuth({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const token = useAuthStore((s) => s.token);
  const mounted = useHasMounted();

  useEffect(() => {
    if (mounted && !token) {
      router.replace("/login");
    }
  }, [mounted, token, router]);

  if (!mounted || !token) {
    return (
      <div className="flex h-screen items-center justify-center text-sm text-muted-foreground">
        Checking session…
      </div>
    );
  }

  return <>{children}</>;
}
