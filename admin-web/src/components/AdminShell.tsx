"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { supabase } from "../lib/supabase";

const navItems = [
  ["/dashboard", "Dashboard"],
  ["/employees", "Employees"],
  ["/products", "Products & Prices"],
  ["/transactions", "Transactions"],
  ["/users", "Users"],
] as const;

export default function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();

  async function handleSignOut() {
    await supabase.auth.signOut();
    router.replace("/login");
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
