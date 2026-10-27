"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import AdminShell from "../../../../components/AdminShell";
import { supabase } from "../../../../lib/supabase";

type VariantRecord = {
  variant_name: string;
  unit_volume_ml: number;
  price: number;
  products: { name: string } | { name: string }[] | null;
};

export default function EditProduct() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const [name, setName] = useState("");
  const [variant, setVariant] = useState("");
  const [volume, setVolume] = useState("");
  const [price, setPrice] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    supabase
      .from("product_variants")
      .select("variant_name,unit_volume_ml,price,products(name)")
      .eq("id", params.id)
      .single()
      .then(({ data, error: loadError }) => {
        if (loadError || !data) {
          setError("Could not load this product variant. Refresh and try again.");
          return;
        }
        const record = data as unknown as VariantRecord;
        const product = Array.isArray(record.products) ? record.products[0] : record.products;
        setVariant(record.variant_name);
        setVolume(String(record.unit_volume_ml));
        setPrice(String(record.price));
        setName(product?.name ?? "");
      });
  }, [params.id]);

  async function save(event: React.FormEvent) {
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
      p_variant_id: params.id,
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
            <h1>Edit Product Variant</h1>
            <p>Update product, size, volume and price.</p>
          </div>
        </header>
        <section className="panel form-panel">
          <form onSubmit={save}>
            <div className="field">
              <label htmlFor="product-name">Product name</label>
              <input id="product-name" value={name} onChange={(event) => setName(event.target.value)} required />
            </div>
            <div className="field">
              <label htmlFor="variant-name">Variant</label>
              <input id="variant-name" value={variant} onChange={(event) => setVariant(event.target.value)} required />
            </div>
            <div className="field">
              <label htmlFor="unit-volume">Unit volume (ML)</label>
              <input id="unit-volume" type="number" min="1" step="1" value={volume} onChange={(event) => setVolume(event.target.value)} required />
            </div>
            <div className="field">
              <label htmlFor="unit-price">Price (₹)</label>
              <input id="unit-price" type="number" min="0" step="0.01" value={price} onChange={(event) => setPrice(event.target.value)} required />
            </div>
            {error && <p className="error-text">{error}</p>}
            <div className="form-actions">
              <Link className="secondary-button" href="/products">Cancel</Link>
              <button className="primary-button" disabled={saving}>{saving ? "Saving..." : "Save changes"}</button>
            </div>
          </form>
        </section>
      </div>
    </AdminShell>
  );
}
