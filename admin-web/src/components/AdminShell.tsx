"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { supabase } from "../lib/supabase";

const navItems = [
  ["/dashboard", "Dashboard"],
  ["/employees", "Employees"],
  ["/products", "Products & Prices"],
  ["/transactions", "Transactions"],
  ["/users", "Users"],
] as const;

export default function AdminShell({ children }: { children: React.ReactNode }) {
  const r = useRouter();
  const pathname = usePathname();
  const [ready, setReady] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    const checkAccess = async () => {
      try {
        const sessionResult = await Promise.race([
          supabase.auth.getSession(),
          new Promise<never>((_, reject) =>
            setTimeout(() => reject(new Error("Authentication check timed out.")), 10000)
          ),
        ]);

        const session = sessionResult.data.session;

        if (!session) {
          r.replace("/login");
          return;
        }

        const profileResult = await Promise.race([
          supabase
            .from("profiles")
            .select("role,active")
            .eq("id", session.user.id)
            .single(),
          new Promise<never>((_, reject) =>
            setTimeout(() => reject(new Error("Profile access check timed out.")), 10000)
          ),
        ]);

        if (profileResult.error) {
          throw profileResult.error;
        }

        const p = profileResult.data;

        if (!p?.active || p.role !== "ADMIN") {
          r.replace("/transactions");
          return;
        }

        if (mounted) setReady(true);
      } catch (err) {
        console.error("Admin access check failed:", err);
        if (mounted) {
          setError(
            err instanceof Error
              ? err.message
              : "Unable to verify your admin access."
          );
        }
      }
    };

    checkAccess();

    return () => {
      mounted = false;
    };
  }, [r]);

  if (!ready) {
    return (
      <main className="login-shell">
        <section className="login-card">
          <h1>{error ? "Unable to check access" : "Checking access…"}</h1>
          {error ? (
            <>
              <p>{error}</p>
              <button className="btn" onClick={() => window.location.reload()}>
                Try again
              </button>
              <button
                className="btn secondary"
                onClick={() => r.replace("/login")}
              >
                Back to login
              </button>
            </>
          ) : (
            <p>Please wait while we verify your admin account.</p>
          )}
        </section>
      </main>
    );
  }

  return (
    <div className="admin-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">Honest Milk · Admin</div>
        <nav className="nav">
          {navItems.map(([href, label]) => {
            const active =
              pathname === href ||
              (href !== "/dashboard" && pathname.startsWith(href + "/"));

            return (
              <Link
                className={"nav-item " + (active ? "active" : "")}
                href={href}
                key={href}
              >
                {label}
              </Link>
            );
          })}
        </nav>
      </aside>

      <main>{children}</main>
    </div>
  );
}
