"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const navItems = [
  ["/dashboard", "Dashboard"],
  ["/employees", "Employees"],
  ["/products", "Products & Prices"],
  ["/transactions", "Transactions"],
  ["/users", "Users"],
] as const;

export default function AdminShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();

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
