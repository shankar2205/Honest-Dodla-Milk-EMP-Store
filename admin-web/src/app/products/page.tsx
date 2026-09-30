"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { supabase } from "../../lib/supabase";

type Variant = { id: string; variant_name: string; unit_volume_ml: number; price: number; active: boolean; products?: { name: string } | { name: string }[] | null };

export default function ProductsPage() {
  const [variants, setVariants] = useState<Variant[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    supabase.from("product_variants").select("id,variant_name,unit_volume_ml,price,active,products(name)").order("variant_name").then(({ data, error: queryError }) => {
      if (queryError) setError(queryError.message);
      setVariants((data ?? []) as unknown as Variant[]);
      setLoading(false);
    });
  }, []);

  function productName(v: Variant) { const p = v.products; return Array.isArray(p) ? p[0]?.name : p?.name; }

  return (
    <div className="admin-shell"><aside className="sidebar"><div className="sidebar-brand">Honest Milk · Admin</div><nav className="nav">{[["/dashboard","Dashboard"],["/employees","Employees"],["/products","Products & Prices"],["/transactions","Transactions"]].map(([href,label]) => <Link className={`nav-item ${href === "/products" ? "active" : ""}`} href={href} key={href}>{label}</Link>)}</nav></aside>
      <main className="main"><header className="topbar"><div><h1>Products & Prices</h1><p>Manage product variants, volume and pricing.</p></div><Link className="primary-button small-button" href="/products/new">+ Add product</Link></header>
      {error ? <p className="error-text">{error}</p> : null}
      <section className="panel table-panel"><table><thead><tr><th>Product</th><th>Variant</th><th>Volume</th><th>Price</th><th>Status</th></tr></thead><tbody>{loading ? <tr><td colSpan={5}>Loading...</td></tr> : variants.map((v) => <tr key={v.id}><td><strong>{productName(v) || "—"}</strong></td><td>{v.variant_name}</td><td>{v.unit_volume_ml >= 1000 ? `${v.unit_volume_ml / 1000} L` : `${v.unit_volume_ml} ML`}</td><td>₹{Number(v.price).toFixed(2)}</td><td><span className={`status ${v.active ? "status-active" : "status-inactive"}`}>{v.active ? "Active" : "Inactive"}</span></td></tr>)}</tbody></table></section>
      </main></div>
  );
}
