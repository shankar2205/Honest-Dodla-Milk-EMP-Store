"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { supabase } from "../../lib/supabase";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setLoading(true);
    const { data, error: signInError } = await supabase.auth.signInWithPassword({ email, password });
    if (signInError || !data.user) {
      setError(signInError?.message || "Unable to sign in.");
      setLoading(false);
      return;
    }
    const { data: profile, error: profileError } = await supabase.from("profiles").select("display_name, role, active").eq("id", data.user.id).single();
    if (profileError || !profile || !profile.active) {
      await supabase.auth.signOut();
      setError("Your account is not active or is not configured for this application.");
      setLoading(false);
      return;
    }
    router.replace(profile.role === "ADMIN" ? "/dashboard" : "/transactions");
  }

  return (
    <main className="login-shell">
      <section className="login-card">
        <div className="brand-mark">HM</div>
        <h1>Honest Milk</h1>
        <p className="subtitle">Dodla Employee Store · Admin Portal</p>
        <form onSubmit={handleSubmit}>
          <div className="field"><label htmlFor="email">Email</label><input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Enter your email" autoComplete="username" required /></div>
          <div className="field"><label htmlFor="password">Password</label><input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Enter your password" autoComplete="current-password" required /></div>
          {error ? <p className="error-text">{error}</p> : null}
          <button className="primary-button" type="submit" disabled={loading}>{loading ? "Signing in..." : "Sign in"}</button>
        </form>
        <p className="helper">Authorized Admin and Operator access only.</p>
      </section>
    </main>
  );
}
