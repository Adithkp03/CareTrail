"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, setToken } from "@/lib/api";
import { getSupabase } from "@/lib/supabase";

export default function SupabaseSignIn() {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [consent, setConsent] = useState(false);
  const [msg, setMsg] = useState("");
  const [busy, setBusy] = useState(false);
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  const supabase = mounted ? getSupabase() : null;

  // Magic-link return: Supabase puts the session in the URL hash; exchange it.
  useEffect(() => {
    if (!supabase) return;
    supabase.auth.getSession().then(async ({ data }) => {
      const access = data.session?.access_token;
      if (!access) return;
      setBusy(true);
      try {
        // consent was captured before the magic link was sent
        const agreed = localStorage.getItem("caretrail_consent") === "yes";
        const res = await api<{ token: string }>("/auth/supabase", {
          method: "POST",
          body: JSON.stringify({ access_token: access, consent: agreed }),
        }, false);
        setToken(res.token);
        await supabase.auth.signOut();
        router.push("/");
      } catch (e) {
        setMsg(e instanceof Error ? e.message : "Sign-in failed");
        setBusy(false);
      }
    });
  }, [supabase, router]);

  if (!supabase) return null;

  async function sendLink(e: React.FormEvent) {
    e.preventDefault();
    if (!consent) { setMsg("Please tick the consent box first."); return; }
    setBusy(true); setMsg("");
    const { error } = await supabase!.auth.signInWithOtp({
      email: email.trim(),
      options: { emailRedirectTo: `${window.location.origin}/login` },
    });
    if (error) { setMsg(error.message); setBusy(false); return; }
    localStorage.setItem("caretrail_consent", "yes");
    setMsg("Check your email for the sign-in link.");
    setBusy(false);
  }

  return (
    <form onSubmit={sendLink} className="mt-6 rounded-2xl border border-ink/10 bg-white p-4">
      <p className="text-sm font-semibold text-ink/70">Or sign in with email</p>
      <input className="mt-2 w-full rounded-xl border border-ink/15 bg-white p-3" placeholder="Email"
        type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      <label className="mt-2 flex items-start gap-2 text-xs text-ink/60">
        <input type="checkbox" className="mt-0.5" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
        I consent to CareTrail storing my pregnancy data to personalize my care.
      </label>
      {msg && <p className="mt-2 text-sm text-brand">{msg}</p>}
      <button disabled={busy} className="mt-2 w-full rounded-xl bg-brand p-3 font-semibold text-white disabled:opacity-50">
        Email me a sign-in link
      </button>
    </form>
  );
}
