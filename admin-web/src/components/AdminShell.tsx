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

type Role = "ADMIN" | "OPERATOR";

export default function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const [checkingAccess, setCheckingAccess] = useState(true);
  const [role, setRole] = useState<Role | null>(null);

  useEffect(() => {
    let active = true;

    async function checkAccess() {
      const { data: sessionData } = await supabase.auth.getSession();
      if (!sessionData.session) {
        if (active) router.replace("/login");
        return;
      }

      const { data: profile, error } = await supabase
        .from("profiles")
        .select("role, active")
        .eq("id", sessionData.session.user.id)
        .single();

      if (!profile || error || !profile.active) {
        await supabase.auth.signOut();
        if (active) router.replace("/login");
        return;
      }

      const userRole = String(profile.role).toUpperCase() as Role;
      if (active) {
        setRole(userRole);
        setCheckingAccess(false);
        if (pathname === "/users" && userRole !== "ADMIN") {
          router.replace("/transactions");
        }
      }
    }

    checkAccess();

    const { data: listener } = supabase.auth.onAuthStateChange((event, session) => {
      if (!active) return;
      if (event === "SIGNED_OUT" || !session) {
        router.replace("/login");
      }
    });

    return () => {
      active = false;
      listener.subscription.unsubscribe();
    };
  }, [pathname, router]);

  async function handleSignOut() {
    await supabase.auth.signOut();
    router.replace("/login");
  }

  if (checkingAccess || !role || (pathname === "/users" && role !== "ADMIN")) {
    return <main className="main"><p className="helper">Checking access...</p></main>;
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

        <button className="signout-button" type="button" onClick={handleSignOut}>
          Sign out
        </button>
      </aside>

      <main>{children}</main>
    </div>
  );
}
