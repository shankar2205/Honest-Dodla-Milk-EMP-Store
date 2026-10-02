"use client";

import AdminShell from "../../components/AdminShell";
import { useEffect, useState } from "react";
import { supabase } from "../../lib/supabase";

type Tx = { id: string; employee_name: string; employee_identifier: string; product_name: string; variant_name: string; quantity: number; total_volume_litres: number; total_amount: number; transaction_at: string };

export default function TransactionsPage() {
  const [rows, setRows] = useState<Tx[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    supabase.from("transaction_report").select("id,employee_name,employee_identifier,product_name,variant_name,quantity,total_volume_litres,total_amount,transaction_at").order("transaction_at", { ascending: false }).limit(100).then(({ data, error: queryError }) => {
      if (queryError) setError(queryError.message);
      setRows((data ?? []) as Tx[]);
      setLoading(false);
    });
  }, []);

  return (
    <AdminShell>
      <div className="main">
        <header className="topbar">
          <div><h1>Transactions</h1><p>Latest employee store activity.</p></div>
        </header>
        {error ? <p className="error-text">{error}</p> : null}
        <section className="panel table-panel"><table><thead><tr><th>Date & time</th><th>Employee</th><th>ID</th><th>Product</th><th>Qty</th><th>Volume</th><th>Amount</th></tr></thead><tbody>{loading ? <tr><td colSpan={7}>Loading...</td></tr> : rows.map((r) => <tr key={r.id}><td>{new Date(r.transaction_at).toLocaleString()}</td><td><strong>{r.employee_name}</strong></td><td>{r.employee_identifier}</td><td>{r.product_name} — {r.variant_name}</td><td>{r.quantity}</td><td>{Number(r.total_volume_litres).toFixed(2)} L</td><td>₹{Number(r.total_amount).toFixed(2)}</td></tr>)}</tbody></table></section>
      </div>
    </AdminShell>
  );
}
