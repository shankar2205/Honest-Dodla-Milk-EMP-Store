"use client";

import { useEffect, useState } from "react";
import { supabase } from "./supabase";

type Role = "ADMIN" | "OPERATOR";

export function useRole() {
  const [role, setRole] = useState<Role | null>(null);

  useEffect(() => {
    let active = true;
    async function loadRole() {
      const { data: sessionData } = await supabase.auth.getSession();
      const user = sessionData.session?.user;
      if (!user) return;
      const { data: profile, error } = await supabase
        .from("profiles")
        .select("role, active")
        .eq("id", user.id)
        .single();
      if (!active || error || !profile?.active) return;
      const normalized = String(profile.role).toUpperCase();
      if (normalized === "ADMIN" || normalized === "OPERATOR") {
        setRole(normalized as Role);
      }
    }
    void loadRole();
    const { data: listener } = supabase.auth.onAuthStateChange(() => {
      void loadRole();
    });
    return () => {
      active = false;
      listener.subscription.unsubscribe();
    };
  }, []);

  return role;
}
