"use client";

import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { supabase } from "../../lib/supabase";

type AuthView = "login" | "forgot" | "recovery";

export default function LoginPage() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [view, setView] = useState<AuthView>("login");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let active = true;
    const url = new URL(window.location.href);

    async function processAuthCallback() {
      const code = url.searchParams.get("code");
      const tokenHash = url.searchParams.get("token_hash");
      const type = url.searchParams.get("type");
      const hashParams = new URLSearchParams(url.hash.replace(/^#/, ""));
      const accessToken = hashParams.get("access_token");
      const refreshToken = hashParams.get("refresh_token");
      const hashType = hashParams.get("type");
      const isRecovery = type === "recovery" || hashType === "recovery";

      // Supabase may return a PKCE code, a token hash, or session tokens in the URL.
      if (code) {
        const { error: callbackError } = await supabase.auth.exchangeCodeForSession(code);
        if (callbackError) {
          if (active) setError("This link is invalid or expired. Request a new password-reset link and open the newest email.");
          // Clear callback query/hash values even when validation fails.
          window.history.replaceState({}, document.title, url.pathname);
          return;
        }
        if (active && isRecovery) {
          setView("recovery");
          setMessage("Enter and confirm your new password.");
        }
        window.history.replaceState({}, document.title, url.pathname);
        return;
      }

      if (tokenHash || type) {
        if (!tokenHash || !type) {
          if (active) setError("This link is invalid or expired. Request a new password-reset link and open the newest email.");
          // A partial callback is malformed; do not leave callback parameters in the address bar.
          window.history.replaceState({}, document.title, url.pathname);
          return;
        }
        const allowedType = ["recovery", "invite", "signup", "magiclink", "email"].includes(type);
        if (!allowedType) {
          if (active) setError("This link is invalid or expired. Request a new password-reset link and open the newest email.");
          // Remove unsupported callback parameters so token material is not left in the address bar.
          window.history.replaceState({}, document.title, url.pathname);
          return;
        }
        const { error: verifyError } = await supabase.auth.verifyOtp({
          token_hash: tokenHash,
          type: type as "recovery" | "invite" | "signup" | "magiclink" | "email",
        });
        if (verifyError) {
          if (active) setError("This link is invalid or expired. Request a new password-reset link and open the newest email.");
          // Clear callback query/hash values even when validation fails.
          window.history.replaceState({}, document.title, url.pathname);
          return;
        }
        if (active && type === "recovery") {
          setView("recovery");
          setMessage("Enter and confirm your new password.");
        }
        window.history.replaceState({}, document.title, url.pathname);
        return;
      }

      if (accessToken || refreshToken) {
        if (!accessToken || !refreshToken) {
          if (active) setError("This link is invalid or expired. Request a new password-reset link and open the newest email.");
          // Do not leave a partial session token in the address bar.
          window.history.replaceState({}, document.title, url.pathname);
          return;
        }
        const { error: sessionError } = await supabase.auth.setSession({
          access_token: accessToken,
          refresh_token: refreshToken,
        });
        if (sessionError) {
          if (active) setError("This link is invalid or expired. Request a new password-reset link and open the newest email.");
          // Clear callback query/hash values even when validation fails.
          window.history.replaceState({}, document.title, url.pathname);
          return;
        }
        if (active && isRecovery) {
          setView("recovery");
          setMessage("Enter and confirm your new password.");
        }
        window.history.replaceState({}, document.title, url.pathname);
        return;
      }

      const { data, error: sessionError } = await supabase.auth.getSession();
      if (!active) return;
      if (sessionError) setError("Could not restore your session. Please open the latest link from your email.");
      if (data.session && isRecovery) {
        setView("recovery");
        setMessage("Enter and confirm your new password.");
      }
    }

    void processAuthCallback().catch(() => {
      if (active) {
        setError("Could not process this authentication link. Request a new password-reset link and open the newest email.");
        window.history.replaceState({}, document.title, url.pathname);
      }
    });
    const { data: listener } = supabase.auth.onAuthStateChange((event) => {
      if (active && event === "PASSWORD_RECOVERY") {
        setView("recovery");
        setError("");
        setMessage("Enter and confirm your new password.");
      }
    });

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

    const { data, error: signInError } = await supabase.auth.signInWithPassword({ email: email.trim(), password });
    if (signInError || !data.user) {
      setError("Sign-in failed. Check the email and password, or use Forgot password to create a fresh password.");
      setLoading(false);
      return;
    }

    const { data: profile, error: profileError } = await supabase
      .from("profiles")
      .select("role, active")
      .eq("id", data.user.id)
      .maybeSingle();

    const role = String(profile?.role ?? "").toUpperCase();
    if (profileError || !profile || !profile.active || !["ADMIN", "OPERATOR"].includes(role)) {
      await supabase.auth.signOut();
      setError("Your account is not active or does not have an authorized application role. Contact an administrator.");
      setLoading(false);
      return;
    }

    setLoading(false);
    router.replace(role === "ADMIN" ? "/dashboard" : "/transactions");
  }

  async function handleResetRequest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");
    setLoading(true);
    const redirectTo = new URL("/login", window.location.origin).toString();
    const { error: resetError } = await supabase.auth.resetPasswordForEmail(email.trim(), { redirectTo });
    setLoading(false);
    if (resetError) {
      setError("Could not send a reset email. Please try again later or contact an administrator.");
      return;
    }
    setMessage("If an account exists for that email, a password-reset link has been sent. Open the newest email on this same site.");
  }

  async function handlePasswordUpdate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");

    if (newPassword.length < 8) {
      setError("Use a password with at least 8 characters.");
      return;
    }
    if (newPassword !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }

    setLoading(true);
    const { error: updateError } = await supabase.auth.updateUser({ password: newPassword });
    if (updateError) {
      setError("Could not update the password. The reset link may have expired; request a new one and use the newest email.");
      setLoading(false);
      return;
    }

    await supabase.auth.signOut();
    setPassword("");
    setNewPassword("");
    setConfirmPassword("");
    setView("login");
    setMessage("Password updated successfully. Sign in with your new password.");
    setLoading(false);
  }

  return (
    <main className="login-shell">
      <section className="login-card">
        <div className="brand-mark">HM</div>
        <h1>Honest Milk</h1>
        <p className="subtitle">Dodla Employee Store · Admin Portal</p>

        {view === "recovery" ? (
          <form onSubmit={handlePasswordUpdate}>
            <h2>Set a new password</h2>
            <div className="field">
              <label htmlFor="new-password">New password</label>
              <input id="new-password" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="At least 8 characters" autoComplete="new-password" minLength={8} required />
            </div>
            <div className="field">
              <label htmlFor="confirm-password">Confirm password</label>
              <input id="confirm-password" type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="Re-enter new password" autoComplete="new-password" minLength={8} required />
            </div>
            {error ? <p className="error-text">{error}</p> : null}
            {message ? <p className="helper">{message}</p> : null}
            <button className="primary-button" type="submit" disabled={loading}>{loading ? "Updating..." : "Update password"}</button>
          </form>
        ) : view === "forgot" ? (
          <form onSubmit={handleResetRequest}>
            <h2>Reset your password</h2>
            <p className="helper">Enter the email address associated with your account.</p>
            <div className="field">
              <label htmlFor="reset-email">Email</label>
              <input id="reset-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Enter your email" autoComplete="email" required />
            </div>
            {error ? <p className="error-text">{error}</p> : null}
            {message ? <p className="helper">{message}</p> : null}
            <button className="primary-button" type="submit" disabled={loading}>{loading ? "Sending..." : "Send reset link"}</button>
            <button className="secondary-button" type="button" onClick={() => { setView("login"); setError(""); setMessage(""); }}>Back to sign in</button>
          </form>
        ) : (
          <>
            <form onSubmit={handleSubmit}>
              <div className="field">
                <label htmlFor="email">Email</label>
                <input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Enter your email" autoComplete="username" required />
              </div>
              <div className="field">
                <label htmlFor="password">Password</label>
                <input id="password" type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Enter your password" autoComplete="current-password" required />
              </div>
              {error ? <p className="error-text">{error}</p> : null}
              {message ? <p className="helper">{message}</p> : null}
              <button className="primary-button" type="submit" disabled={loading}>{loading ? "Signing in..." : "Sign in"}</button>
            </form>
            <button className="secondary-button" type="button" onClick={() => { setView("forgot"); setError(""); setMessage(""); }}>Forgot password?</button>
            <p className="helper">Authorized Admin and Operator access only.</p>
          </>
        )}
      </section>
    </main>
  );
}
