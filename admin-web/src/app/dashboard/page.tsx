"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import AdminShell from "../../components/AdminShell";
import { supabase } from "../../lib/supabase";

type Tx={id:string;employee_name:string;employee_identifier:string;product_name:string;variant_name:string;quantity:number;total_volume_litres:number;total_amount:number;transaction_at:string};

export default function DashboardPage(){
 const [stats,setStats]=useState({employees:0,products:0,litres:0,value:0}); const [rows,setRows]=useState<Tx[]>([]); const [loading,setLoading]=useState(true); const [error,setError]=useState("");
 useEffect(()=>{
   const start=new Date(); start.setHours(0,0,0,0);
   supabase.from("transaction_report").select("id,employee_name,employee_identifier,product_name,variant_name,quantity,total_volume_litres,total_amount,transaction_at").gte("transaction_at",start.toISOString()).order("transaction_at",{ascending:false}).then(({data,error:e})=>{
     if(e){setError(e.message);setLoading(false);return;} const list=(data??[]) as Tx[]; setRows(list.slice(0,8)); setStats({employees:new Set(list.map(x=>x.employee_identifier)).size,products:list.reduce((s,x)=>s+x.quantity,0),litres:list.reduce((s,x)=>s+Number(x.total_volume_litres),0),value:list.reduce((s,x)=>s+Number(x.total_amount),0)}); setLoading(false);
   });
 },[]);
 return <AdminShell><div className="main"><header className="topbar"><div><h1>Dashboard</h1><p>Honest Milk - Dodla Employee Store</p></div><strong>Today</strong></header>
 <section className="stats"><article className="stat"><div className="stat-label">Employees Served Today</div><div className="stat-value">{stats.employees}</div></article><article className="stat"><div className="stat-label">Products Issued Today</div><div className="stat-value">{stats.products}</div></article><article className="stat"><div className="stat-label">Volume Today</div><div className="stat-value">{stats.litres.toFixed(2)} L</div></article><article className="stat"><div className="stat-label">Value Today</div><div className="stat-value">₹{stats.value.toFixed(2)}</div></article></section>
 {error&&<p className="error-text">{error}</p>}<section className="panel table-panel"><div className="panel-heading"><h2>Recent transactions</h2><Link href="/transactions">View all</Link></div><table><thead><tr><th>Time</th><th>Employee</th><th>Product</th><th>Qty</th><th>Amount</th></tr></thead><tbody>{loading?<tr><td colSpan={5}>Loading...</td></tr>:rows.length===0?<tr><td colSpan={5}>No transactions recorded today.</td></tr>:rows.map(r=><tr key={r.id}><td>{new Date(r.transaction_at).toLocaleTimeString([], {hour:"2-digit",minute:"2-digit"})}</td><td><strong>{r.employee_name}</strong><br/><span className="muted-cell">{r.employee_identifier}</span></td><td>{r.product_name} — {r.variant_name}</td><td>{r.quantity}</td><td>₹{Number(r.total_amount).toFixed(2)}</td></tr>)}</tbody></table></section>
 </div></AdminShell>;
}
