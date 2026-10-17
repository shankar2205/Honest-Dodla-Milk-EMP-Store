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
    if (idType === "EMPLOYEE" && !/^\\d{6}$/.test(code.trim())) {
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
    let guestCode=code.trim();
    if(idType==="GUEST" && !guestCode) {
      const {data,error:e}=await supabase.rpc("next_guest_code");
      if(e){setError("Could not generate a guest ID. Check your connection and try again.");setSaving(false);return;}
      guestCode=data; setCode(guestCode); setGenerated(guestCode);
    }
    const payload = idType==="EMPLOYEE"
      ? {name,id_type:"EMPLOYEE",employee_code:code.trim(),guest_code:null,department:department||null,phone:null}
      : {name,id_type:"GUEST",employee_code:null,guest_code:guestCode,department:department||null,phone:phone||null};
    const {error}=await supabase.from("employees").insert(payload as any);
    if(error){setError("Could not save the employee. Check the employee ID or guest ID for duplicates, and confirm you have administrator access.");setSaving(false);return;}
    router.replace("/employees");
  }

  return <AdminShell><div className="main">
    <header className="topbar"><div><h1>Add Employee</h1><p>Create a permanent employee or guest identifier.</p></div></header>
    <section className="panel form-panel"><form onSubmit={submit}>
      <div className="field"><label>Employee type</label><select value={idType} onChange={e=>setIdType(e.target.value)}><option value="EMPLOYEE">Employee</option><option value="GUEST">Guest</option></select></div>
      <div className="field"><label>Name</label><input value={name} onChange={e=>setName(e.target.value)} required /></div>
      <div className="field"><label>{idType==="EMPLOYEE" ? "Employee ID" : "Guest ID"}</label><input value={code} onChange={e=>setCode(idType==="EMPLOYEE" ? e.target.value.replace(/\\D/g, "").slice(0, 6) : e.target.value.toUpperCase())} inputMode={idType==="EMPLOYEE" ? "numeric" : "text"} maxLength={idType==="EMPLOYEE" ? 6 : undefined} pattern={idType==="EMPLOYEE" ? "[0-9]{6}" : undefined} placeholder={idType==="EMPLOYEE" ? "Enter 6 digits" : "Leave blank to generate"} required={idType==="EMPLOYEE"} />{idType==="EMPLOYEE" && <p className="helper">Enter exactly 6 digits (0–9 only).</p>}{generated&&<p className="helper">Permanent Guest ID generated: {generated}</p>}</div>
      <div className="field"><label>Department</label><input value={department} onChange={e=>setDepartment(e.target.value)} /></div>
      {idType==="GUEST" && <div className="field"><label>Phone</label><input value={phone} onChange={e=>{setPhone(e.target.value);setDuplicateWarning("");}} /></div>}
      {duplicateWarning && <p className="error-text">{duplicateWarning}</p>}{error && <p className="error-text">{error}</p>}
      <div className="form-actions"><Link className="secondary-button" href="/employees">Cancel</Link><button className="primary-button" disabled={saving}>{saving?"Saving...":duplicateWarning?"Continue anyway":"Save employee"}</button></div>
    </form></section>
  </div></AdminShell>;
}
