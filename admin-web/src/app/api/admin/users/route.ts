import { createClient } from "@supabase/supabase-js";
import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";

export async function POST(request: NextRequest) {
  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const anonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY;
  // Prefer the current Supabase secret key; retain legacy service-role compatibility.
  const serverSecretKey = process.env.SUPABASE_SECRET_KEY || process.env.SUPABASE_SERVICE_ROLE_KEY;

  if (!supabaseUrl || !anonKey || !serverSecretKey) {
    return NextResponse.json(
      { error: "User creation is not configured on the server. Ask the deployment administrator to configure the secure Supabase server key." },
      { status: 503 },
    );
  }

  const authorization = request.headers.get("authorization") ?? "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  if (!token) {
    return NextResponse.json({ error: "Please sign in as an Admin and try again." }, { status: 401 });
  }

  const callerClient = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: `Bearer ${token}` } },
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: authData, error: authError } = await callerClient.auth.getUser(token);
  if (authError || !authData.user) {
    return NextResponse.json({ error: "Your session is invalid or expired. Please sign in again." }, { status: 401 });
  }

  const { data: callerProfile, error: profileError } = await callerClient
    .from("profiles")
    .select("role,active")
    .eq("id", authData.user.id)
    .single();

  if (profileError || !callerProfile?.active || String(callerProfile.role).toUpperCase() !== "ADMIN") {
    return NextResponse.json({ error: "Only an active Admin can create Operator accounts." }, { status: 403 });
  }

  let body: { displayName?: unknown; email?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "Enter the Operator's name and email address." }, { status: 400 });
  }

  const displayName = typeof body.displayName === "string" ? body.displayName.trim() : "";
  const email = typeof body.email === "string" ? body.email.trim().toLowerCase() : "";
  if (!displayName || displayName.length > 120) {
    return NextResponse.json({ error: "Name is required and must be 120 characters or fewer." }, { status: 400 });
  }
  if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    return NextResponse.json({ error: "Enter a valid email address for the Operator login." }, { status: 400 });
  }

  const adminClient = createClient(supabaseUrl, serverSecretKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: inviteData, error: inviteError } = await adminClient.auth.admin.inviteUserByEmail(email, {
    redirectTo: new URL("/login", request.url).toString(),
    data: { display_name: displayName },
  });

  if (inviteError || !inviteData.user) {
    return NextResponse.json(
      { error: "Could not send the invitation. Check whether this email is already registered and whether Supabase email invitations are configured." },
      { status: 400 },
    );
  }

  const { error: insertError } = await adminClient.from("profiles").insert({
    id: inviteData.user.id,
    display_name: displayName,
    username: email,
    role: "OPERATOR",
    active: true,
  });

  if (insertError) {
    await adminClient.auth.admin.deleteUser(inviteData.user.id);
    return NextResponse.json(
      { error: "The Operator profile could not be created, so the new login was rolled back. Please try again or contact support." },
      { status: 500 },
    );
  }

  return NextResponse.json({ ok: true, message: `Invitation sent to ${email}. The Operator can use the email link to set a password.` });
}
