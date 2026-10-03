"use client";

import AdminShell from "../../../components/AdminShell";
import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { supabase } from "../../../lib/supabase";

type Employee = {
  id: string;
  name: string;
  employee_code: string | null;
  guest_code: string | null;
  department: string | null;
  phone: string | null;
  active: boolean;
  id_type: "EMPLOYEE" | "GUEST";
};

type Tx = {
  id: string;
  transaction_at: string;
  product_name: string;
  variant_name: string;
  quantity: number;
  total_volume_litres: number;
  total_amount: number;
};

export default function EmployeeHistoryPage() {
  const params = useParams<{ id: string }>();
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [rows, setRows] = useState<Tx[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    async function load() {
      setLoading(true);
      setError("");

      const { data: employeeData, error: employeeError } = await supabase
        .from("employees")
        .select("id,name,employee_code,guest_code,department,phone,active,id_type")
        .eq("id", params.id)
        .single();

      if (employeeError) {
        setError(employeeError.message);
        setLoading(false);
        return;
      }

      const person = employeeData as Employee;
      setEmployee(person);

      const identifier = person.employee_code || person.guest_code;
      if (!identifier) {
        setError("This employee does not have an identifier.");
        setLoading(false);
        return;
      }

      const { data: transactionData, error: transactionError } = await supabase
        .from("transaction_report")
        .select("id,transaction_at,product_name,variant_name,quantity,total_volume_litres,total_amount")
        .eq("employee_identifier", identifier)
        .order("transaction_at", { ascending: false })
        .limit(1000);

      if (transactionError) setError(transactionError.message);
      setRows((transactionData ?? []) as Tx[]);
      setLoading(false);
    }

    load();
  }, [params.id]);

  const summary = useMemo(
    () => ({
      transactions: rows.length,
      quantity: rows.reduce((sum, row) => sum + Number(row.quantity), 0),
      litres: rows.reduce((sum, row) => sum + Number(row.total_volume_litres), 0),
      value: rows.reduce((sum, row) => sum + Number(row.total_amount), 0),
    }),
    [rows]
  );

  const identifier = employee?.employee_code || employee?.guest_code || "—";

  return (
    <AdminShell>
      <div className="main">
        <header className="topbar">
          <div>
            <h1>{employee?.name || "Employee History"}</h1>
            <p>{employee?.id_type === "GUEST" ? "Guest" : "Employee"} · {identifier}</p>
          </div>
          <div className="form-actions" style={{ marginTop: 0 }}>
            <Link className="secondary-button" href={`/employees/${params.id}/edit`}>Edit</Link>
            <Link className="primary-button small-button" href="/employees">Back to employees</Link>
          </div>
        </header>

        {error && <p className="error-text">{error}</p>}

        {employee && (
          <>
            <section className="employee-profile">
              <div><span>Name</span><strong>{employee.name}</strong></div>
              <div><span>Identifier</span><strong>{identifier}</strong></div>
              <div><span>Department</span><strong>{employee.department || "—"}</strong></div>
              <div><span>Phone</span><strong>{employee.phone || "—"}</strong></div>
              <div>
                <span>Status</span>
                <strong className={employee.active ? "profile-active" : "profile-inactive"}>
                  {employee.active ? "Active" : "Inactive"}
                </strong>
              </div>
            </section>

            <section className="stats-grid employee-history-stats">
              <div className="stat-card"><span>Transactions</span><strong>{summary.transactions}</strong></div>
              <div className="stat-card"><span>Quantity</span><strong>{summary.quantity}</strong></div>
              <div className="stat-card"><span>Milk Volume</span><strong>{summary.litres.toFixed(2)} L</strong></div>
              <div className="stat-card"><span>Total Value</span><strong>₹{summary.value.toFixed(2)}</strong></div>
            </section>

            <section className="panel table-panel">
              <div className="panel-heading">
                <h2>Transaction History</h2>
                <span className="muted-cell">{rows.length} record{rows.length === 1 ? "" : "s"} loaded</span>
              </div>
              <table>
                <thead><tr><th>Date & Time</th><th>Product</th><th>Qty</th><th>Volume</th><th>Amount</th></tr></thead>
                <tbody>
                  {loading ? (
                    <tr><td colSpan={5}>Loading...</td></tr>
                  ) : rows.length === 0 ? (
                    <tr><td colSpan={5}>No transactions recorded for this employee.</td></tr>
                  ) : (
                    rows.map((row) => (
                      <tr key={row.id}>
                        <td>{new Date(row.transaction_at).toLocaleString()}</td>
                        <td><strong>{row.product_name} — {row.variant_name}</strong></td>
                        <td>{row.quantity}</td>
                        <td>{Number(row.total_volume_litres).toFixed(2)} L</td>
                        <td>₹{Number(row.total_amount).toFixed(2)}</td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </section>
          </>
        )}
      </div>
    </AdminShell>
  );
}
