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
  const [newName, setNewName] = useState("");
  const [newEmail, setNewEmail] = useState("");
  const [creating, setCreating] = useState(false);
  const [message, setMessage] = useState("");

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

  async function createOperator(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");
    setCreating(true);

    const { data: sessionData, error: sessionError } = await supabase.auth.getSession();
    const accessToken = sessionData.session?.access_token;
    if (sessionError || !accessToken) {
      setError("Your session has expired. Please sign in again.");
      setCreating(false);
      return;
    }

    try {
      const response = await fetch("/api/admin/users", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({ displayName: newName, email: newEmail }),
      });
      const result = await response.json();
      if (!response.ok) {
        setError(result.error || "Could not create the Operator account.");
      } else {
        setMessage(result.message || "Operator invitation sent.");
        setNewName("");
        setNewEmail("");
        load();
      }
    } catch {
      setError("Could not contact the server. Check your connection and try again.");
    } finally {
      setCreating(false);
    }
  }

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
        <section className="panel form-panel">
          <h2>Create Operator login</h2>
          <p className="helper">Send an invitation email. The Operator will use the secure link to set their own password. Only an Admin can create Operator accounts.</p>
          <form onSubmit={createOperator}>
            <div className="field">
              <label htmlFor="operator-name">Full name</label>
              <input id="operator-name" value={newName} onChange={(e) => setNewName(e.target.value)} maxLength={120} required />
            </div>
            <div className="field">
              <label htmlFor="operator-email">Login email</label>
              <input id="operator-email" type="email" autoComplete="email" value={newEmail} onChange={(e) => setNewEmail(e.target.value)} maxLength={254} required />
            </div>
            <div className="form-actions">
              <button className="primary-button" type="submit" disabled={creating}>
                {creating ? "Sending invitation..." : "Create Operator & Send Invite"}
              </button>
            </div>
          </form>
          {message && <p className="helper" role="status">{message}</p>}
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
