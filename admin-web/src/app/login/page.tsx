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

    const isPasswordSetupType = (value: string | null) =>
      value === "recovery" || value === "invite" || value === "signup" || value === "magiclink";

    const showPasswordSetup = () => {
      if (!active) return;
      setRecoveryMode(true);
      setError("");
      setMessage("Enter a new password for your account.");
    };

    const { data: listener } = supabase.auth.onAuthStateChange((event) => {
      if (!active) return;
      if (event === "PASSWORD_RECOVERY") {
        showPasswordSetup();
      }
    });

    (async () => {
      const url = new URL(window.location.href);
      const callbackError = url.searchParams.get("error_description") || url.searchParams.get("error");
      if (callbackError) {
        if (active) {
          setError(callbackError.replace(/\+/g, " "));
        }
        return;
      }

      const code = url.searchParams.get("code");
      const tokenHash = url.searchParams.get("token_hash");
      const queryType = url.searchParams.get("type");

      if (code) {
        const { error: exchangeError } = await supabase.auth.exchangeCodeForSession(code);
        if (!active) return;
        if (exchangeError) {
          setError("This invitation or password-reset link is invalid or has expired. Please request a fresh link.");
          return;
        }
        if (isPasswordSetupType(queryType)) showPasswordSetup();
        window.history.replaceState({}, document.title, url.pathname);
        return;
      }

      if (tokenHash && (queryType === "recovery" || queryType === "invite" || queryType === "signup" || queryType === "magiclink")) {
        const { error: verifyError } = await supabase.auth.verifyOtp({
          token_hash: tokenHash,
          type: queryType,
        });
        if (!active) return;
        if (verifyError) {
          setError("This invitation or password-reset link is invalid or has expired. Please request a fresh link.");
          return;
        }
        if (queryType === "recovery" || queryType === "invite" || queryType === "signup") {
          showPasswordSetup();
        }
        window.history.replaceState({}, document.title, url.pathname);
        return;
      }

      // Supabase's default email templates can return access_token and type in the URL hash.
      const hashParams = new URLSearchParams(url.hash.replace(/^#/, ""));
      const hashType = hashParams.get("type");
      const hasAuthHash = hashParams.has("access_token") && hashParams.has("refresh_token");

      if (hasAuthHash && isPasswordSetupType(hashType)) {
        const { error: sessionError } = await supabase.auth.setSession({
          access_token: hashParams.get("access_token")!,
          refresh_token: hashParams.get("refresh_token")!,
        });
        if (!active) return;
        if (sessionError) {
          setError("This invitation or password-reset link is invalid or has expired. Please request a fresh link.");
          return;
        }
        if (hashType === "recovery" || hashType === "invite" || hashType === "signup") {
          showPasswordSetup();
        }
        window.history.replaceState({}, document.title, url.pathname);
        return;
      }

      const { data, error: sessionError } = await supabase.auth.getSession();
      if (!active) return;
      if (sessionError) {
        setError("Could not verify the invitation link. Please open a fresh link or ask an Admin for help.");
        return;
      }

      if (data.session && (queryType === "recovery" || queryType === "invite" || hashType === "recovery" || hashType === "invite")) {
        showPasswordSetup();
        window.history.replaceState({}, document.title, url.pathname);
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
