"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import AdminShell from "../../../components/AdminShell";
import { supabase } from "../../../lib/supabase";

export default function NewEmployeePage() {
  const router = useRouter();
  const [name,setName]=useState(""); const [idType,setIdType]=useState("EMPLOYEE"); const [code,setCode]=useState("");
  const [generated,setGenerated]=useState(""); const [department,setDepartment]=useState(""); const [phone,setPhone]=useState("");
  const [error,setError]=useState(""); const [saving,setSaving]=useState(false); const [duplicateWarning,setDuplicateWarning]=useState("");

  async function submit(e:FormEvent) {
    e.preventDefault(); setError("");
    if (idType === "EMPLOYEE" && !/^\d{6}$/.test(code.trim())) {
      setError("Employee ID must contain exactly 6 digits (0–9 only).");
      return;
    }
    setSaving(true);
    if(idType==="GUEST" && phone.trim() && !duplicateWarning) {
      const {data:matches,error:lookupError}=await supabase.rpc("find_guest_by_phone",{p_phone:phone.trim()});
      if(lookupError){setError("Could not check whether this guest is already registered. Check your connection and try again.");setSaving(false);return;}
      const match=Array.isArray(matches)?matches[0]:matches;
      if(match){setDuplicateWarning(`A guest named ${match.name} is already registered with this mobile number. Continue only if this is a different person.`);setSaving(false);return;}
    }
    if (idType === "EMPLOYEE") {
      const { error: createError } = await supabase.rpc("create_employee", {
        p_employee_code: code.trim(),
        p_name: name.trim(),
        p_phone: null,
        p_department: department || null,
      });
      if (createError) {
        setError(createError.message === "Employee ID already exists"
          ? "That employee ID is already registered."
          : "Could not save the employee. Check the employee details and confirm you have administrator access.");
        setSaving(false);
        return;
      }
    } else {
      const { data: createdGuest, error: createError } = await supabase.rpc("create_guest_employee_with_code", {
        p_name: name.trim(),
        p_phone: phone.trim() || null,
        p_department: department || null,
        p_guest_code: code.trim() || null,
      });
      if (createError) {
        setError(createError.message === "Guest ID already exists"
          ? "That guest ID is already registered."
          : "Could not save the guest. Check the guest ID and confirm you have administrator access.");
        setSaving(false);
        return;
      }
      const guestCode = createdGuest?.guest_code;
      if (guestCode) {
        setCode(guestCode);
        setGenerated(guestCode);
      }
    }
    router.replace("/employees");
  }

  return <AdminShell><div className="main">
    <header className="topbar"><div><h1>Add Employee</h1><p>Create a permanent employee or guest identifier.</p></div></header>
    <section className="panel form-panel"><form onSubmit={submit}>
      <div className="field"><label>Employee type</label><select value={idType} onChange={e=>setIdType(e.target.value)}><option value="EMPLOYEE">Employee</option><option value="GUEST">Guest</option></select></div>
      <div className="field"><label>Name</label><input value={name} onChange={e=>setName(e.target.value)} required /></div>
      <div className="field"><label>{idType==="EMPLOYEE" ? "Employee ID" : "Guest ID"}</label><input value={code} onChange={e=>setCode(idType==="EMPLOYEE" ? e.target.value.replace(/\D/g, "").slice(0, 6) : e.target.value.toUpperCase())} inputMode={idType==="EMPLOYEE" ? "numeric" : "text"} maxLength={idType==="EMPLOYEE" ? 6 : undefined} pattern={idType==="EMPLOYEE" ? "[0-9]{6}" : undefined} placeholder={idType==="EMPLOYEE" ? "Enter 6 digits" : "Leave blank to generate"} required={idType==="EMPLOYEE"} />{idType==="EMPLOYEE" && <p className="helper">Enter exactly 6 digits (0–9 only).</p>}{generated&&<p className="helper">Permanent Guest ID generated: {generated}</p>}</div>
      <div className="field"><label>Department</label><input value={department} onChange={e=>setDepartment(e.target.value)} /></div>
      {idType==="GUEST" && <div className="field"><label>Phone</label><input value={phone} onChange={e=>{setPhone(e.target.value);setDuplicateWarning("");}} /></div>}
      {duplicateWarning && <p className="error-text">{duplicateWarning}</p>}{error && <p className="error-text">{error}</p>}
      <div className="form-actions"><Link className="secondary-button" href="/employees">Cancel</Link><button className="primary-button" disabled={saving}>{saving?"Saving...":duplicateWarning?"Continue anyway":"Save employee"}</button></div>
    </form></section>
  </div></AdminShell>;
}
