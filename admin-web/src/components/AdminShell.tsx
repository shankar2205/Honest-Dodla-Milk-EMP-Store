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

  useEffect(() => {
    let mounted = true;
    supabase.auth.getSession().then(async ({ data }) => {
      if (!data.session) {
        r.replace("/login");
        return;
      }

      const { data: p } = await supabase
        .from("profiles")
        .select("role,active")
        .eq("id", data.session.user.id)
        .single();

      if (!p?.active || p.role !== "ADMIN") {
        r.replace("/transactions");
        return;
      }

      if (mounted) setReady(true);
    });

    return () => {
      mounted = false;
    };
  }, [r]);

  if (!ready) {
    return (
      <main className="login-shell">
        <section className="login-card">
          <h1>Checking access…</h1>
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
