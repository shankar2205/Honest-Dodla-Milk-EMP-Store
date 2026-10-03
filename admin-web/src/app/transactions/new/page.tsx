"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import AdminShell from "../../../components/AdminShell";
import { supabase } from "../../../lib/supabase";

type Employee = {
  id: string;
  name: string;
  employee_code: string | null;
  guest_code: string | null;
  id_type: "EMPLOYEE" | "GUEST";
  department: string | null;
};

type Product = { id: string; name: string };
type Variant = {
  id: string;
  product_id: string;
  variant_name: string;
  unit_volume_ml: number;
  price: number;
};

export default function NewTransactionPage() {
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [variants, setVariants] = useState<Variant[]>([]);
  const [employeeId, setEmployeeId] = useState("");
  const [variantId, setVariantId] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    async function load() {
      setError("");
      const [employeeResult, productResult, variantResult] = await Promise.all([
        supabase
          .from("employees")
          .select("id,name,employee_code,guest_code,id_type,department")
          .eq("active", true)
          .order("name"),
        supabase.from("products").select("id,name").eq("active", true).order("name"),
        supabase
          .from("product_variants")
          .select("id,product_id,variant_name,unit_volume_ml,price")
          .eq("active", true)
          .order("variant_name"),
      ]);

      const firstError = employeeResult.error || productResult.error || variantResult.error;
      if (firstError) setError(firstError.message);
      setEmployees((employeeResult.data ?? []) as Employee[]);
      setProducts((productResult.data ?? []) as Product[]);
      setVariants((variantResult.data ?? []) as Variant[]);
      setLoading(false);
    }

    load();
  }, []);

  const productMap = useMemo(
    () => new Map(products.map((product) => [product.id, product.name])),
    [products]
  );

  const selectedEmployee = employees.find((employee) => employee.id === employeeId);
  const selectedVariant = variants.find((variant) => variant.id === variantId);
  const quantityNumber = Math.max(0, Number(quantity) || 0);
  const totalVolumeLitres = selectedVariant
    ? (quantityNumber * selectedVariant.unit_volume_ml) / 1000
    : 0;
  const totalAmount = selectedVariant ? quantityNumber * Number(selectedVariant.price) : 0;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setSuccess("");

    if (!employeeId || !variantId || quantityNumber < 1) {
      setError("Select an employee, select a product, and enter a quantity of at least 1.");
      return;
    }

    setSaving(true);

    const { data: sessionData } = await supabase.auth.getSession();
    const operatorId = sessionData.session?.user.id;
    if (!operatorId) {
      setError("Your session has expired. Please sign in again.");
      setSaving(false);
      return;
    }

    const { data: currentEmployee } = await supabase
      .from("employees")
      .select("id")
      .eq("id", employeeId)
      .eq("active", true)
      .maybeSingle();

    const { data: currentVariant } = await supabase
      .from("product_variants")
      .select("id,price")
      .eq("id", variantId)
      .eq("active", true)
      .maybeSingle();

    if (!currentEmployee || !currentVariant) {
      setError("The selected employee or product is no longer active. Refresh and try again.");
      setSaving(false);
      return;
    }

    const { error: insertError } = await supabase.from("transactions").insert({
      employee_id: currentEmployee.id,
      product_variant_id: currentVariant.id,
      quantity: quantityNumber,
      unit_price: currentVariant.price,
      operator_id: operatorId,
    });

    if (insertError) {
      setError(insertError.message);
      setSaving(false);
      return;
    }

    setSuccess(
      `Transaction recorded: ${selectedEmployee?.name ?? "Employee"} · ${productMap.get(selectedVariant?.product_id ?? "") ?? "Product"} — ${selectedVariant?.variant_name ?? ""} · Qty ${quantityNumber}`
    );
    setEmployeeId("");
    setVariantId("");
    setQuantity("1");
    setSaving(false);
  }

  return (
    <AdminShell>
      <div className="main">
        <header className="topbar">
          <div>
            <h1>New Transaction</h1>
            <p>Record milk issued to an active employee or guest.</p>
          </div>
          <Link className="secondary-button" href="/transactions">
            View transactions
          </Link>
        </header>

        {error ? <p className="error-text">{error}</p> : null}
        {success ? <p className="success-text">{success}</p> : null}

        <section className="panel form-panel">
          {loading ? (
            <p className="helper">Loading employees and products...</p>
          ) : (
            <form onSubmit={submit}>
              <div className="field">
                <label htmlFor="employee">Employee / Guest</label>
                <select
                  id="employee"
                  value={employeeId}
                  onChange={(event) => setEmployeeId(event.target.value)}
                  required
                >
                  <option value="">Select employee</option>
                  {employees.map((employee) => (
                    <option key={employee.id} value={employee.id}>
                      {employee.name} — {employee.employee_code || employee.guest_code}
                      {employee.department ? ` · ${employee.department}` : ""}
                    </option>
                  ))}
                </select>
              </div>

              <div className="field">
                <label htmlFor="variant">Product / Pack</label>
                <select
                  id="variant"
                  value={variantId}
                  onChange={(event) => setVariantId(event.target.value)}
                  required
                >
                  <option value="">Select product</option>
                  {variants.map((variant) => (
                    <option key={variant.id} value={variant.id}>
                      {productMap.get(variant.product_id) ?? "Product"} — {variant.variant_name} ·{" "}
                      ₹{Number(variant.price).toFixed(2)}
                    </option>
                  ))}
                </select>
              </div>

              <div className="field">
                <label htmlFor="quantity">Quantity</label>
                <input
                  id="quantity"
                  type="number"
                  min="1"
                  step="1"
                  value={quantity}
                  onChange={(event) => setQuantity(event.target.value)}
                  required
                />
              </div>

              <div className="transaction-summary">
                <div>
                  <span>Unit price</span>
                  <strong>₹{selectedVariant ? Number(selectedVariant.price).toFixed(2) : "0.00"}</strong>
                </div>
                <div>
                  <span>Total volume</span>
                  <strong>{totalVolumeLitres.toFixed(3)} L</strong>
                </div>
                <div>
                  <span>Total amount</span>
                  <strong>₹{totalAmount.toFixed(2)}</strong>
                </div>
              </div>

              <div className="form-actions">
                <Link className="secondary-button" href="/transactions">
                  Cancel
                </Link>
                <button className="primary-button" type="submit" disabled={saving}>
                  {saving ? "Saving..." : "Record transaction"}
                </button>
              </div>
            </form>
          )}
        </section>
      </div>
    </AdminShell>
  );
}
