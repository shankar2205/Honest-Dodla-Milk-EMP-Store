"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import AdminShell from "../../../components/AdminShell";
import { supabase } from "../../../lib/supabase";

export default function NewProductPage() {
  const router = useRouter();
  const [name, setName] = useState("Honest Milk");
  const [variant, setVariant] = useState("");
  const [volume, setVolume] = useState("");
  const [price, setPrice] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");

    const unitVolumeMl = Number(volume);
    const unitPrice = Number(price);
    if (!Number.isInteger(unitVolumeMl) || unitVolumeMl < 1 || !Number.isFinite(unitPrice) || unitPrice < 0) {
      setError("Enter a positive whole-number volume and a non-negative price.");
      return;
    }

    setSaving(true);
    const { error: saveError } = await supabase.rpc("save_product_variant", {
      p_variant_id: null,
      p_product_name: name.trim(),
      p_variant_name: variant.trim(),
      p_unit_volume_ml: unitVolumeMl,
      p_price: unitPrice,
    });

    if (saveError) {
      setError("Could not save the product variant. Check the details and administrator access, then try again.");
      setSaving(false);
      return;
    }

    router.replace("/products");
  }

  return (
    <AdminShell>
      <div className="main">
        <header className="topbar">
          <div>
            <h1>Add Product Variant</h1>
            <p>Add a product and its sellable size and price.</p>
          </div>
        </header>
        <section className="panel form-panel">
          <form onSubmit={submit}>
            <div className="field">
              <label htmlFor="product-name">Product name</label>
              <input id="product-name" value={name} onChange={(event) => setName(event.target.value)} required />
            </div>
            <div className="field">
              <label htmlFor="variant-name">Variant</label>
              <input id="variant-name" value={variant} onChange={(event) => setVariant(event.target.value)} placeholder="500 ML" required />
            </div>
            <div className="field">
              <label htmlFor="unit-volume">Unit volume (ML)</label>
              <input id="unit-volume" type="number" min="1" step="1" value={volume} onChange={(event) => setVolume(event.target.value)} placeholder="500" required />
            </div>
            <div className="field">
              <label htmlFor="unit-price">Price (₹)</label>
              <input id="unit-price" type="number" min="0" step="0.01" value={price} onChange={(event) => setPrice(event.target.value)} placeholder="38" required />
            </div>
            {error && <p className="error-text">{error}</p>}
            <div className="form-actions">
              <Link className="secondary-button" href="/products">Cancel</Link>
              <button className="primary-button" disabled={saving}>{saving ? "Saving..." : "Save product"}</button>
            </div>
          </form>
        </section>
      </div>
    </AdminShell>
  );
}
