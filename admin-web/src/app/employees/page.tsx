"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { supabase } from "../../lib/supabase";

type Employee = { id: string; name: string; employee_code: string | null; guest_code: string | null; department: string | null; phone: string | null; active: boolean };

export default function EmployeesPage() {
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    supabase.from("employees").select("id,name,employee_code,guest_code,department,phone,active").order("name").then(({ data, error: queryError }) => {
      if (queryError) setError(queryError.message);
      setEmployees((data ?? []) as Employee[]);
      setLoading(false);
    });
  }, []);

  return (
    <div className="admin-shell"><aside className="sidebar"><div className="sidebar-brand">Honest Milk · Admin</div><nav className="nav">{[["/dashboard","Dashboard"],["/employees","Employees"],["/products","Products & Prices"],["/transactions","Transactions"]].map(([href,label]) => <Link className={`nav-item ${href === "/employees" ? "active" : ""}`} href={href} key={href}>{label}</Link>)}</nav></aside>
      <main className="main"><header className="topbar"><div><h1>Employees</h1><p>Manage permanent employee and guest identifiers.</p></div><Link className="primary-button small-button" href="/employees/new">+ Add employee</Link></header>
      {error ? <p className="error-text">{error}</p> : null}
      <section className="panel table-panel"><table><thead><tr><th>Name</th><th>ID</th><th>Department</th><th>Phone</th><th>Status</th></tr></thead><tbody>{loading ? <tr><td colSpan={5}>Loading...</td></tr> : employees.map((e) => <tr key={e.id}><td><strong>{e.name}</strong></td><td>{e.employee_code || e.guest_code}</td><td>{e.department || "—"}</td><td>{e.phone || "—"}</td><td><span className={`status ${e.active ? "status-active" : "status-inactive"}`}>{e.active ? "Active" : "Inactive"}</span></td></tr>)}</tbody></table></section>
      </main></div>
  );
}
