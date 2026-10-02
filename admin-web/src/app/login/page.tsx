"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { supabase } from "../../lib/supabase";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);
  const [recoveryMode, setRecoveryMode] = useState(false);

  useEffect(() => {
    let active = true;

    const { data: listener } = supabase.auth.onAuthStateChange((event) => {
      if (event === "PASSWORD_RECOVERY" && active) {
        setRecoveryMode(true);
        setError("");
        setMessage("Enter a new password for your account.");
      }
    });

    (async () => {
      const url = new URL(window.location.href);
      const code = url.searchParams.get("code");
      const recoveryHash = window.location.hash.includes("type=recovery");

      if (code) {
        const { error: exchangeError } = await supabase.auth.exchangeCodeForSession(code);
        if (exchangeError) {
          if (active) setError("This password-reset link is invalid or has expired. Please request a new link.");
        } else if (active) {
          setRecoveryMode(true);
          setMessage("Enter a new password for your account.");
          window.history.replaceState({}, document.title, url.pathname);
        }
        return;
      }

      const { data } = await supabase.auth.getSession();
      if (active && data.session && recoveryHash) {
        setRecoveryMode(true);
        setMessage("Enter a new password for your account.");
      }
    })();

    return () => {
      active = false;
      listener.subscription.unsubscribe();
    };
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");
    setLoading(true);

    const { data, error: signInError } = await supabase.auth.signInWithPassword({ email, password });
    if (signInError || !data.user) {
      setError(signInError?.message || "Unable to sign in.");
      setLoading(false);
      return;
    }

    const { data: profile, error: profileError } = await supabase
      .from("profiles")
      .select("display_name, role, active")
      .eq("id", data.user.id)
      .single();

    if (profileError || !profile || !profile.active) {
      await supabase.auth.signOut();
      setError("Your account is not active or is not configured for this application.");
      setLoading(false);
      return;
    }

    router.replace(String(profile.role).toUpperCase() === "ADMIN" ? "/dashboard" : "/transactions");
  }

  async function handlePasswordUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");

    if (newPassword.length < 6) {
      setError("Password must be at least 6 characters.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);
    const { error: updateError } = await supabase.auth.updateUser({ password: newPassword });

    if (updateError) {
      setError(updateError.message);
      setLoading(false);
      return;
    }

    await supabase.auth.signOut();
    setNewPassword("");
    setConfirmPassword("");
    setRecoveryMode(false);
    setMessage("Password updated successfully. You can now sign in with your new password.");
    setLoading(false);
  }

  return (
    <main className="login-shell">
      <section className="login-card">
        <div className="brand-mark">HM</div>
        <h1>Honest Milk</h1>
        <p className="subtitle">Dodla Employee Store · Admin Portal</p>

        {recoveryMode ? (
          <form onSubmit={handlePasswordUpdate}>
            <h2>Set a new password</h2>
            <div className="field">
              <label htmlFor="new-password">New password</label>
              <input id="new-password" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="Enter new password" autoComplete="new-password" required />
            </div>
            <div className="field">
              <label htmlFor="confirm-password">Confirm password</label>
              <input id="confirm-password" type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="Re-enter new password" autoComplete="new-password" required />
            </div>
            {error ? <p className="error-text">{error}</p> : null}
            {message ? <p className="helper">{message}</p> : null}
            <button className="primary-button" type="submit" disabled={loading}>{loading ? "Updating..." : "Update password"}</button>
          </form>
        ) : (
          <>
            <form onSubmit={handleSubmit}>
              <div className="field"><label htmlFor="email">Email</label><input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Enter your email" autoComplete="username" required /></div>
              <div className="field"><label htmlFor="password">Password</label><input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Enter your password" autoComplete="current-password" required /></div>
              {error ? <p className="error-text">{error}</p> : null}
              {message ? <p className="helper">{message}</p> : null}
              <button className="primary-button" type="submit" disabled={loading}>{loading ? "Signing in..." : "Sign in"}</button>
            </form>
            <p className="helper">Authorized Admin and Operator access only.</p>
          </>
        )}
      </section>
    </main>
  );
}
