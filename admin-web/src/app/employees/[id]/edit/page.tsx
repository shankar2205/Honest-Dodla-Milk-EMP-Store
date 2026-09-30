"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import AdminShell from "../../../components/AdminShell";
import { supabase } from "../../../../lib/supabase";

export default function EditEmployee() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const [name, setName] = useState("");
  const [dept, setDept] = useState("");
  const [phone, setPhone] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    supabase
      .from("employees")
      .select("name,department,phone")
      .eq("id", params.id)
      .single()
      .then(({ data, error: loadError }) => {
        if (loadError) {
          setError(loadError.message);
          return;
        }
        setName(data.name);
        setDept(data.department || "");
        setPhone(data.phone || "");
      });
  }, [params.id]);

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    const { error: saveError } = await supabase
      .from("employees")
      .update({ name, department: dept || null, phone: phone || null })
      .eq("id", params.id);

    if (saveError) {
      setError(saveError.message);
    } else {
      router.replace("/employees");
    }
    setSaving(false);
  }

  return (
    <AdminShell>
      <div className="admin-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">Honest Milk · Admin</div>
      </aside>
      <main className="main">
        <header className="topbar">
          <div>
            <h1>Edit Employee</h1>
            <p>Update employee details.</p>
          </div>
        </header>
        <section className="panel form-panel">
          <form onSubmit={save}>
            <div className="field">
              <label>Name</label>
              <input value={name} onChange={(e) => setName(e.target.value)} required />
            </div>
            <div className="field">
              <label>Department</label>
              <input value={dept} onChange={(e) => setDept(e.target.value)} />
            </div>
            <div className="field">
              <label>Phone</label>
              <input value={phone} onChange={(e) => setPhone(e.target.value)} />
            </div>
            {error && <p className="error-text">{error}</p>}
            <div className="form-actions">
              <Link className="secondary-button" href="/employees">Cancel</Link>
              <button className="primary-button" disabled={saving}>
                {saving ? "Saving..." : "Save changes"}
              </button>
            </div>
          </form>
        </section>
      </main>
      </div>
    </AdminShell>
  );
}
