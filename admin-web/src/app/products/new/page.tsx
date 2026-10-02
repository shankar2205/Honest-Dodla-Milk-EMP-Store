"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import AdminShell from "../../../components/AdminShell";
import { supabase } from "../../../lib/supabase";

export default function NewProductPage(){
  const router=useRouter(); const [name,setName]=useState("Honest Milk"); const [variant,setVariant]=useState(""); const [volume,setVolume]=useState(""); const [price,setPrice]=useState("");
  const [error,setError]=useState(""); const [saving,setSaving]=useState(false);

  async function submit(e:FormEvent){
    e.preventDefault(); setError(""); setSaving(true);
    let productId=""; const {data:existing,error:findError}=await supabase.from("products").select("id").eq("name",name.trim()).maybeSingle();
    if(findError){setError(findError.message);setSaving(false);return;}
    if(existing) productId=existing.id; else {
      const {data:created,error:createError}=await supabase.from("products").insert({name:name.trim()}).select("id").single();
      if(createError||!created){setError(createError?.message||"Could not create product.");setSaving(false);return;} productId=created.id;
    }
    const {error:variantError}=await supabase.from("product_variants").insert({product_id:productId,variant_name:variant.trim(),unit_volume_ml:Number(volume),price:Number(price)});
    if(variantError){setError(variantError.message);setSaving(false);return;} router.replace("/products");
  }

  return <AdminShell><div className="main">
    <header className="topbar"><div><h1>Add Product Variant</h1><p>Add a product and its sellable size and price.</p></div></header>
    <section className="panel form-panel"><form onSubmit={submit}>
      <div className="field"><label>Product name</label><input value={name} onChange={e=>setName(e.target.value)} required /></div>
      <div className="field"><label>Variant</label><input value={variant} onChange={e=>setVariant(e.target.value)} placeholder="500 ML" required /></div>
      <div className="field"><label>Unit volume (ML)</label><input type="number" min="1" value={volume} onChange={e=>setVolume(e.target.value)} placeholder="500" required /></div>
      <div className="field"><label>Price (₹)</label><input type="number" min="0" step="0.01" value={price} onChange={e=>setPrice(e.target.value)} placeholder="38" required /></div>
      {error&&<p className="error-text">{error}</p>}
      <div className="form-actions"><Link className="secondary-button" href="/products">Cancel</Link><button className="primary-button" disabled={saving}>{saving?"Saving...":"Save product"}</button></div>
    </form></section>
  </div></AdminShell>;
}
