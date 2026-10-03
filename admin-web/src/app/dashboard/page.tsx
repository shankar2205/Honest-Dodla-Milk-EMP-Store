"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import AdminShell from "../../components/AdminShell";
import { supabase } from "../../lib/supabase";

type Tx = {
  id: string;
  employee_name: string;
  employee_identifier: string;
  id_type: "EMPLOYEE" | "GUEST";
  product_name: string;
  variant_name: string;
  quantity: number;
  total_volume_litres: number;
  total_amount: number;
  transaction_at: string;
};

type ProductSummary = {
  label: string;
  quantity: number;
  litres: number;
  amount: number;
};

export default function DashboardPage() {
  const [stats, setStats] = useState({
    employees: 0,
    guests: 0,
    quantity: 0,
    litres: 0,
    value: 0,
  });
  const [rows, setRows] = useState<Tx[]>([]);
  const [productSummary, setProductSummary] = useState<ProductSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function loadDashboard() {
      const start = new Date();
      start.setHours(0, 0, 0, 0);

      const { data, error: queryError } = await supabase
        .from("transaction_report")
        .select(
          "id,employee_name,employee_identifier,id_type,product_name,variant_name,quantity,total_volume_litres,total_amount,transaction_at"
        )
        .gte("transaction_at", start.toISOString())
        .order("transaction_at", { ascending: false });

      if (queryError) {
        setError(queryError.message);
        setLoading(false);
        return;
      }

      const list = (data ?? []) as Tx[];
      const employeeIds = new Set(
        list.filter((row) => row.id_type === "EMPLOYEE").map((row) => row.employee_identifier)
      );
      const guestIds = new Set(
        list.filter((row) => row.id_type === "GUEST").map((row) => row.employee_identifier)
      );

      const summary = new Map<string, ProductSummary>();
      for (const row of list) {
        const label = row.product_name + " — " + row.variant_name;
        const existing = summary.get(label) ?? {
          label,
          quantity: 0,
          litres: 0,
          amount: 0,
        };
        existing.quantity += row.quantity;
        existing.litres += Number(row.total_volume_litres);
        existing.amount += Number(row.total_amount);
        summary.set(label, existing);
      }

      setRows(list.slice(0, 8));
      setStats({
        employees: employeeIds.size,
        guests: guestIds.size,
        quantity: list.reduce((sum, row) => sum + row.quantity, 0),
        litres: list.reduce((sum, row) => sum + Number(row.total_volume_litres), 0),
        value: list.reduce((sum, row) => sum + Number(row.total_amount), 0),
      });
      setProductSummary(
        Array.from(summary.values()).sort((a, b) => b.amount - a.amount)
      );
      setLoading(false);
    }

    loadDashboard();
  }, []);

  return (
    <AdminShell>
      <div className="main">
        <header className="topbar">
          <div>
            <h1>Dashboard</h1>
            <p>Honest Milk - Dodla Employee Store</p>
          </div>
          <strong>Today</strong>
        </header>

        <section className="stats">
          <article className="stat">
            <div className="stat-label">Employees Served</div>
            <div className="stat-value">{stats.employees}</div>
          </article>
          <article className="stat">
            <div className="stat-label">Guests Served</div>
            <div className="stat-value">{stats.guests}</div>
          </article>
          <article className="stat">
            <div className="stat-label">Quantity Issued</div>
            <div className="stat-value">{stats.quantity}</div>
          </article>
          <article className="stat">
            <div className="stat-label">Milk Volume</div>
            <div className="stat-value">{stats.litres.toFixed(2)} L</div>
          </article>
          <article className="stat">
            <div className="stat-label">Total Value</div>
            <div className="stat-value">₹{stats.value.toFixed(2)}</div>
          </article>
        </section>

        {error ? <p className="error-text">{error}</p> : null}

        <section className="panel table-panel">
          <div className="panel-heading">
            <h2>Today by product / pack</h2>
            <Link href="/transactions">View all transactions</Link>
          </div>
          <table>
            <thead>
              <tr>
                <th>Product / Pack</th>
                <th>Qty</th>
                <th>Volume</th>
                <th>Value</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={4}>Loading...</td></tr>
              ) : productSummary.length === 0 ? (
                <tr><td colSpan={4}>No transactions recorded today.</td></tr>
              ) : (
                productSummary.map((item) => (
                  <tr key={item.label}>
                    <td><strong>{item.label}</strong></td>
                    <td>{item.quantity}</td>
                    <td>{item.litres.toFixed(2)} L</td>
                    <td>₹{item.amount.toFixed(2)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </section>

        <section className="panel table-panel">
          <div className="panel-heading">
            <h2>Recent transactions</h2>
            <Link href="/transactions">View all</Link>
          </div>
          <table>
            <thead>
              <tr>
                <th>Time</th>
                <th>Employee</th>
                <th>Type</th>
                <th>Product</th>
                <th>Qty</th>
                <th>Amount</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={6}>Loading...</td></tr>
              ) : rows.length === 0 ? (
                <tr><td colSpan={6}>No transactions recorded today.</td></tr>
              ) : (
                rows.map((row) => (
                  <tr key={row.id}>
                    <td>
                      {new Date(row.transaction_at).toLocaleTimeString([], {
                        hour: "2-digit",
                        minute: "2-digit",
                      })}
                    </td>
                    <td>
                      <strong>{row.employee_name}</strong>
                      <br />
                      <span className="muted-cell">{row.employee_identifier}</span>
                    </td>
                    <td>{row.id_type === "GUEST" ? "Guest" : "Employee"}</td>
                    <td>{row.product_name} — {row.variant_name}</td>
                    <td>{row.quantity}</td>
                    <td>₹{Number(row.total_amount).toFixed(2)}</td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </section>
      </div>
    </AdminShell>
  );
}
