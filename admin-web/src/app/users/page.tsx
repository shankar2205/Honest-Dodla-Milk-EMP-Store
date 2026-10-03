"use client";

import AdminShell from "../../components/AdminShell";
import { useEffect, useState } from "react";
import { supabase } from "../../lib/supabase";

type User = {
  id: string;
  display_name: string;
  username: string | null;
  role: "ADMIN" | "OPERATOR";
  active: boolean;
};

export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);
  const [currentUserId, setCurrentUserId] = useState("");

  const load = () => {
    setLoading(true);
    supabase.from("profiles").select("id,display_name,username,role,active").order("display_name")
      .then(({ data, error: e }) => {
        if (e) setError(e.message);
        setUsers((data ?? []) as User[]);
        setLoading(false);
      });
  };

  useEffect(() => {
    supabase.auth.getUser().then(({ data }) => setCurrentUserId(data.user?.id ?? ""));
    load();
  }, []);

  async function update(id: string, patch: Partial<User>) {
    if (id === currentUserId && ("active" in patch || "role" in patch)) {
      setError("You cannot deactivate your own account or change your own role.");
      return;
    }
    setError("");
    const { error: e } = await supabase.from("profiles").update(patch).eq("id", id);
    if (e) setError(e.message);
    else load();
  }

  return (
    <AdminShell>
      <div className="main">
        <header className="topbar">
          <div><h1>Users</h1><p>Manage Admin and Operator application access.</p></div>
        </header>
        {error && <p className="error-text">{error}</p>}
        <section className="panel">
          <p className="helper">Create login accounts in Supabase Authentication, then add the matching profile. Your own account is protected from role or status changes.</p>
        </section>
        <section className="panel table-panel">
          <table>
            <thead><tr><th>Name</th><th>Username</th><th>Role</th><th>Status</th><th>Action</th></tr></thead>
            <tbody>
              {loading ? <tr><td colSpan={5}>Loading...</td></tr> : users.map((u) => {
                const isSelf = u.id === currentUserId;
                return (
                  <tr key={u.id}>
                    <td><strong>{u.display_name}</strong>{isSelf ? <div className="muted-cell">Current account</div> : null}</td>
                    <td>{u.username || "—"}</td>
                    <td>
                      <select value={u.role} disabled={isSelf} onChange={(e) => update(u.id, { role: e.target.value as User["role"] })}>
                        <option value="ADMIN">Admin</option><option value="OPERATOR">Operator</option>
                      </select>
                    </td>
                    <td><span className={`status ${u.active ? "status-active" : "status-inactive"}`}>{u.active ? "Active" : "Inactive"}</span></td>
                    <td>
                      <button className="secondary-button" disabled={isSelf} onClick={() => update(u.id, { active: !u.active })}>
                        {u.active ? "Deactivate" : "Activate"}
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </section>
      </div>
    </AdminShell>
  );
}
