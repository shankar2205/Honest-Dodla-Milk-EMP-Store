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

type CartItem = { variantId: string; quantity: number };

export default function NewTransactionPage() {
  const [employees, setEmployees] = useState<Employee[]>([]);
  const [products, setProducts] = useState<Product[]>([]);
  const [variants, setVariants] = useState<Variant[]>([]);
  const [employeeId, setEmployeeId] = useState("");
  const [variantId, setVariantId] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [cart, setCart] = useState<CartItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    async function load() {
      setError("");
      const [employeeResult, productResult, variantResult] = await Promise.all([
        supabase.from("employees").select("id,name,employee_code,guest_code,id_type,department").eq("active", true).order("name"),
        supabase.from("products").select("id,name").eq("active", true).order("name"),
        supabase.from("product_variants").select("id,product_id,variant_name,unit_volume_ml,price,products!inner(active)").eq("active", true).eq("products.active", true).order("variant_name"),
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

  const productMap = useMemo(() => new Map(products.map((product) => [product.id, product.name])), [products]);
  const variantMap = useMemo(() => new Map(variants.map((variant) => [variant.id, variant])), [variants]);
  const selectedEmployee = employees.find((employee) => employee.id === employeeId);
  const quantityNumber = Number(quantity);
  const validQuantity = Number.isInteger(quantityNumber) && quantityNumber >= 1;

  const cartDetails = cart.map((item) => {
    const variant = variantMap.get(item.variantId);
    return variant ? { ...item, variant } : null;
  }).filter((item): item is CartItem & { variant: Variant } => Boolean(item));

  const totalQuantity = cartDetails.reduce((sum, item) => sum + item.quantity, 0);
  const totalVolumeLitres = cartDetails.reduce((sum, item) => sum + (item.quantity * item.variant.unit_volume_ml) / 1000, 0);
  const totalAmount = cartDetails.reduce((sum, item) => sum + item.quantity * Number(item.variant.price), 0);

  function addToCart() {
    setError("");
    setSuccess("");
    if (!employeeId) {
      setError("Select an employee or guest before adding products.");
      return;
    }
    if (!variantId || !validQuantity) {
      setError("Select a product and enter a quantity of at least 1.");
      return;
    }
    setCart((current) => {
      const existing = current.find((item) => item.variantId === variantId);
      if (existing) {
        return current.map((item) => item.variantId === variantId ? { ...item, quantity: item.quantity + quantityNumber } : item);
      }
      return [...current, { variantId, quantity: quantityNumber }];
    });
    setVariantId("");
    setQuantity("1");
  }

  function updateCartQuantity(itemVariantId: string, nextQuantity: string) {
    const parsed = Math.floor(Number(nextQuantity));
    if (!Number.isFinite(parsed) || parsed < 1) return;
    setCart((current) => current.map((item) => item.variantId === itemVariantId ? { ...item, quantity: parsed } : item));
  }

  function removeFromCart(itemVariantId: string) {
    setCart((current) => current.filter((item) => item.variantId !== itemVariantId));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    setSuccess("");
    if (!employeeId || cart.length === 0) {
      setError("Select an employee and add at least one product.");
      return;
    }
    if (cart.some((item) => !Number.isInteger(item.quantity) || item.quantity < 1)) {
      setError("Quantity must be a whole number of at least 1.");
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

    const { data: currentEmployee } = await supabase.from("employees").select("id").eq("id", employeeId).eq("active", true).maybeSingle();
    if (!currentEmployee) {
      setError("The selected employee is no longer active. Refresh and try again.");
      setSaving(false);
      return;
    }

    const variantIds = cart.map((item) => item.variantId);
    const { data: currentVariants, error: variantError } = await supabase
      .from("product_variants").select("id,price").in("id", variantIds).eq("active", true);

    if (variantError) {
      setError(variantError.message);
      setSaving(false);
      return;
    }
    if (!currentVariants || currentVariants.length !== variantIds.length) {
      setError("One or more selected products are no longer active. Refresh and try again.");
      setSaving(false);
      return;
    }

    const currentPriceMap = new Map(currentVariants.map((variant) => [variant.id, Number(variant.price)]));
    const rows = cart.map((item) => ({
      employee_id: currentEmployee.id,
      product_variant_id: item.variantId,
      quantity: item.quantity,
      unit_price: currentPriceMap.get(item.variantId),
      operator_id: operatorId,
    }));

    if (rows.some((row) => row.unit_price === undefined)) {
      setError("Could not verify one or more product prices. Refresh and try again.");
      setSaving(false);
      return;
    }

    const { error: insertError } = await supabase.from("transactions").insert(rows);
    if (insertError) {
      setError(insertError.message);
      setSaving(false);
      return;
    }

    setSuccess(`${selectedEmployee?.name ?? "Employee"}: ${cart.length} product variant${cart.length === 1 ? "" : "s"} recorded successfully.`);
    setCart([]);
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
            <p>Add multiple milk variants for one employee in a single transaction.</p>
          </div>
          <Link className="secondary-button" href="/transactions">View transactions</Link>
        </header>

        {error ? <p className="error-text">{error}</p> : null}
        {success ? <p className="success-text">{success}</p> : null}

        <section className="panel form-panel">
          {loading ? <p className="helper">Loading employees and products...</p> : (
            <form onSubmit={submit}>
              <div className="field">
                <label htmlFor="employee">Employee / Guest</label>
                <select id="employee" value={employeeId} onChange={(event) => { setEmployeeId(event.target.value); setCart([]); setSuccess(""); }} required>
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
                <select id="variant" value={variantId} onChange={(event) => setVariantId(event.target.value)}>
                  <option value="">Select product</option>
                  {variants.map((variant) => (
                    <option key={variant.id} value={variant.id}>
                      {productMap.get(variant.product_id) ?? "Product"} — {variant.variant_name} · ₹{Number(variant.price).toFixed(2)}
                    </option>
                  ))}
                </select>
              </div>

              <div className="field">
                <label htmlFor="quantity">Quantity</label>
                <input id="quantity" type="number" min="1" step="1" value={quantity} onChange={(event) => setQuantity(event.target.value)} />
              </div>

              <div className="form-actions">
                <button className="secondary-button" type="button" onClick={addToCart}>+ Add product</button>
              </div>

              {cartDetails.length > 0 ? (
                <section className="panel table-panel" style={{ marginTop: 18 }}>
                  <div className="panel-heading">
                    <div>
                      <h2>Products in this transaction</h2>
                      <p className="helper">Add different variants before recording the transaction.</p>
                    </div>
                  </div>
                  <table>
                    <thead><tr><th>Product</th><th>Pack</th><th>Qty</th><th>Unit price</th><th>Amount</th><th></th></tr></thead>
                    <tbody>
                      {cartDetails.map((item) => (
                        <tr key={item.variantId}>
                          <td>{productMap.get(item.variant.product_id) ?? "Product"}</td>
                          <td>{item.variant.variant_name}</td>
                          <td>
                            <input type="number" min="1" step="1" value={item.quantity} onChange={(event) => updateCartQuantity(item.variantId, event.target.value)} style={{ maxWidth: 90 }} />
                          </td>
                          <td>₹{Number(item.variant.price).toFixed(2)}</td>
                          <td>₹{(item.quantity * Number(item.variant.price)).toFixed(2)}</td>
                          <td><button className="secondary-button" type="button" onClick={() => removeFromCart(item.variantId)}>Remove</button></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </section>
              ) : <p className="helper" style={{ marginTop: 14 }}>No products added yet.</p>}

              <div className="transaction-summary">
                <div><span>Total line items</span><strong>{cartDetails.length}</strong></div>
                <div><span>Total quantity</span><strong>{totalQuantity}</strong></div>
                <div><span>Total volume</span><strong>{totalVolumeLitres.toFixed(3)} L</strong></div>
                <div><span>Total amount</span><strong>₹{totalAmount.toFixed(2)}</strong></div>
              </div>

              <div className="form-actions">
                <Link className="secondary-button" href="/transactions">Cancel</Link>
                <button className="primary-button" type="submit" disabled={saving || cartDetails.length === 0}>
                  {saving ? "Saving..." : "Record all products"}
                </button>
              </div>
            </form>
          )}
        </section>
      </div>
    </AdminShell>
  );
}
