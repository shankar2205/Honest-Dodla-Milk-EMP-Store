"use client";

import Link from "next/link";
import AdminShell from "../../components/AdminShell";
import { useEffect, useMemo, useState } from "react";
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

function localDateString(date: Date) {
  const offset = date.getTimezoneOffset();
  return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10);
}

export default function TransactionsPage() {
  const today = localDateString(new Date());
  const [fromDate, setFromDate] = useState(today);
  const [toDate, setToDate] = useState(today);
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [productFilter, setProductFilter] = useState("ALL");
  const [rows, setRows] = useState<Tx[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  async function loadTransactions(
    nextFromDate = fromDate,
    nextToDate = toDate,
    nextTypeFilter = typeFilter,
    nextProductFilter = productFilter
  ) {
    setLoading(true);
    setError("");

    if (nextFromDate > nextToDate) {
      setError("From date cannot be later than To date.");
      setRows([]);
      setLoading(false);
      return;
    }

    const endExclusive = new Date(nextToDate + "T00:00:00");
    endExclusive.setDate(endExclusive.getDate() + 1);

    let query = supabase
      .from("transaction_report")
      .select(
        "id,employee_name,employee_identifier,id_type,product_name,variant_name,quantity,total_volume_litres,total_amount,transaction_at"
      )
      .gte("transaction_at", new Date(nextFromDate + "T00:00:00").toISOString())
      .lt("transaction_at", endExclusive.toISOString())
      .order("transaction_at", { ascending: false })
      .limit(5000);

    if (nextTypeFilter !== "ALL") query = query.eq("id_type", nextTypeFilter);
    if (nextProductFilter !== "ALL") query = query.eq("product_name", nextProductFilter);

    const { data, error: queryError } = await query;
    if (queryError) {
      setError(queryError.message);
      setRows([]);
    } else {
      setRows((data ?? []) as Tx[]);
    }
    setLoading(false);
  }

  useEffect(() => {
    loadTransactions();
  }, []);

  const products = useMemo(
    () => Array.from(new Set(rows.map((row) => row.product_name))).sort(),
    [rows]
  );

  const totals = useMemo(
    () => ({
      quantity: rows.reduce((sum, row) => sum + row.quantity, 0),
      litres: rows.reduce((sum, row) => sum + Number(row.total_volume_litres), 0),
      amount: rows.reduce((sum, row) => sum + Number(row.total_amount), 0),
      employees: new Set(
        rows.filter((row) => row.id_type === "EMPLOYEE").map((row) => row.employee_identifier)
      ).size,
      guests: new Set(
        rows.filter((row) => row.id_type === "GUEST").map((row) => row.employee_identifier)
      ).size,
    }),
    [rows]
  );

  function exportCsv() {
    const header = [
      "Date & Time",
      "Employee",
      "ID",
      "Type",
      "Product",
      "Pack",
      "Quantity",
      "Volume (L)",
      "Amount (INR)",
    ];

    const escape = (value: string | number) =>
      '"' + String(value).replaceAll('"', '""') + '"';

    const csv = [
      header.map(escape).join(","),
      ...rows.map((row) =>
        [
          new Date(row.transaction_at).toLocaleString(),
          row.employee_name,
          row.employee_identifier,
          row.id_type,
          row.product_name,
          row.variant_name,
          row.quantity,
          Number(row.total_volume_litres).toFixed(3),
          Number(row.total_amount).toFixed(2),
        ]
          .map(escape)
          .join(",")
      ),
    ].join("\n");

    const blob = new Blob(["\ufeff" + csv], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "honest-milk-transactions-" + fromDate + "-to-" + toDate + ".csv";
    link.click();
    URL.revokeObjectURL(url);
  }

  return (
    <AdminShell>
      <div className="main">
        <header className="topbar">
          <div>
            <h1>Transactions</h1>
            <p>Filter, review, and export employee store activity.</p>
          </div>
          <Link className="primary-button" href="/transactions/new">
            New transaction
          </Link>
        </header>

        <section className="panel report-filters">
          <div className="filter-grid">
            <div className="field">
              <label htmlFor="from-date">From date</label>
              <input id="from-date" type="date" value={fromDate} onChange={(e) => setFromDate(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="to-date">To date</label>
              <input id="to-date" type="date" value={toDate} onChange={(e) => setToDate(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="type-filter">Employee type</label>
              <select id="type-filter" value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
                <option value="ALL">All</option>
                <option value="EMPLOYEE">Employees</option>
                <option value="GUEST">Guests</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="product-filter">Product</label>
              <select id="product-filter" value={productFilter} onChange={(e) => setProductFilter(e.target.value)}>
                <option value="ALL">All products</option>
                {products.map((product) => <option key={product} value={product}>{product}</option>)}
              </select>
            </div>
          </div>
          <div className="form-actions">
            <button
              className="secondary-button"
              type="button"
              onClick={() => {
                setFromDate(today);
                setToDate(today);
                setTypeFilter("ALL");
                setProductFilter("ALL");
                loadTransactions(today, today, "ALL", "ALL");
              }}
            >
              Reset
            </button>
            <button className="primary-button" type="button" onClick={loadTransactions}>
              Apply filters
            </button>
            <button className="secondary-button" type="button" onClick={exportCsv} disabled={rows.length === 0}>
              Export CSV
            </button>
          </div>
        </section>

        {error ? <p className="error-text">{error}</p> : null}

        <section className="stats report-stats">
          <article className="stat"><div className="stat-label">Transactions</div><div className="stat-value">{rows.length}</div></article>
          <article className="stat"><div className="stat-label">Employees Served</div><div className="stat-value">{totals.employees}</div></article>
          <article className="stat"><div className="stat-label">Guests Served</div><div className="stat-value">{totals.guests}</div></article>
          <article className="stat"><div className="stat-label">Quantity</div><div className="stat-value">{totals.quantity}</div></article>
          <article className="stat"><div className="stat-label">Volume</div><div className="stat-value">{totals.litres.toFixed(2)} L</div></article>
          <article className="stat"><div className="stat-label">Value</div><div className="stat-value">₹{totals.amount.toFixed(2)}</div></article>
        </section>

        <section className="panel table-panel">
          <div className="panel-heading">
            <h2>Transaction report</h2>
            <span className="muted-cell">{rows.length} records loaded</span>
          </div>
          <table>
            <thead>
              <tr><th>Date & time</th><th>Employee</th><th>ID</th><th>Type</th><th>Product</th><th>Qty</th><th>Volume</th><th>Amount</th></tr>
            </thead>
            <tbody>
              {loading ? <tr><td colSpan={8}>Loading...</td></tr> :
               rows.length === 0 ? <tr><td colSpan={8}>No transactions match the selected filters.</td></tr> :
               rows.map((row) => (
                <tr key={row.id}>
                  <td>{new Date(row.transaction_at).toLocaleString()}</td>
                  <td><strong>{row.employee_name}</strong></td>
                  <td>{row.employee_identifier}</td>
                  <td>{row.id_type === "GUEST" ? "Guest" : "Employee"}</td>
                  <td>{row.product_name} — {row.variant_name}</td>
                  <td>{row.quantity}</td>
                  <td>{Number(row.total_volume_litres).toFixed(2)} L</td>
                  <td>₹{Number(row.total_amount).toFixed(2)}</td>
                </tr>
              ))
              }
            </tbody>
          </table>
        </section>
      </div>
    </AdminShell>
  );
}
