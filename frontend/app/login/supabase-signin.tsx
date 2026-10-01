"use client";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { api, setToken } from "@/lib/api";
import { getSupabase } from "@/lib/supabase";
import { t, type Lang } from "@/lib/i18n";

const PENDING = "ct:auth-consent";
function pendingConsent() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(PENDING) || "null");
    return saved?.agreed === true && Date.now() - saved.at >= 0 && Date.now() - saved.at < 30 * 60 * 1000;
  } catch { return false; }
}

export default function SupabaseSignIn({ lang = "en" }: { lang?: Lang }) {
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [consent, setConsent] = useState(false);
  const [msg, setMsg] = useState("");
  const [busy, setBusy] = useState(false);
  const [mounted, setMounted] = useState(false);
  const [access, setAccess] = useState<string | null>(null);
  const [needsConsent, setNeedsConsent] = useState(false);
  const exchanging = useRef(false);
  const supabase = mounted ? getSupabase() : null;
  const googleEnabled = process.env.NEXT_PUBLIC_GOOGLE_AUTH_ENABLED === "true";
  useEffect(() => setMounted(true), []);

  // Auth callbacks only capture the session. Do not await Supabase calls inside
  // onAuthStateChange: those callbacks run under the auth client's lock.
  useEffect(() => {
    if (!supabase) return;
    let active = true;
    const params = new URLSearchParams(window.location.search);
    const hash = new URLSearchParams(window.location.hash.slice(1));
    if (params.has("error") || hash.has("error")) {
      setMsg("Sign-in was cancelled or could not be completed. Please try again.");
      sessionStorage.removeItem(PENDING);
      window.history.replaceState(null, "", window.location.pathname);
    }
    void supabase.auth.getSession().then(({ data, error }) => {
      if (!active) return;
      if (error) setMsg(t("signinFailed", lang));
      if (data.session) setAccess(data.session.access_token);
    }).catch(() => { if (active) setMsg(t("signinFailed", lang)); });
    const { data } = supabase.auth.onAuthStateChange((_event, session) => {
      if (active && session) setAccess(session.access_token);
    });
    return () => { active = false; data.subscription.unsubscribe(); };
  }, [supabase]);

  async function exchange(agreed: boolean) {
    if (!access || exchanging.current || !supabase) return;
    exchanging.current = true; setBusy(true); setMsg("");
    try {
      const res = await api<{ token: string }>("/auth/supabase", {
        method: "POST", body: JSON.stringify({ access_token: access, consent: agreed, language: lang }),
      }, false);
      setToken(res.token);
      sessionStorage.removeItem(PENDING);
      localStorage.removeItem("caretrail_consent"); // retire old, indefinite consent cache
      // Only clear this browser's Supabase session, not other signed-in devices.
      try { await supabase.auth.signOut({ scope: "local" }); } catch { /* CareTrail session is already ready */ }
      window.history.replaceState(null, "", window.location.pathname);
      router.replace("/");
    } catch (e) {
      if (e instanceof Error && e.message === "Consent is required to create an account") {
        setNeedsConsent(true); setMsg(t("tickConsent", lang));
      } else setMsg("Unable to finish sign-in. Try again or use phone and password.");
      setBusy(false);
    } finally { exchanging.current = false; }
  }
  useEffect(() => { if (access) void exchange(pendingConsent()); }, [access]);

  if (!supabase) return null;
  function rememberConsent() {
    sessionStorage.setItem(PENDING, JSON.stringify({ agreed: consent, at: Date.now() }));
  }
  async function sendLink(e: React.FormEvent) {
    e.preventDefault();
    if (!consent) { setMsg(t("tickConsent", lang)); return; }
    setBusy(true); setMsg(""); rememberConsent();
    try {
      const { error } = await supabase!.auth.signInWithOtp({
        email: email.trim(), options: { emailRedirectTo: `${window.location.origin}/login` },
      });
      if (error) throw error;
      setMsg(t("checkEmail", lang));
    } catch { sessionStorage.removeItem(PENDING); setMsg("Could not send the sign-in email. Please try again."); }
    setBusy(false);
  }
  async function google() {
    if (!consent) { setMsg(t("tickConsent", lang)); return; }
    setBusy(true); setMsg(""); rememberConsent();
    try {
      const { error } = await supabase!.auth.signInWithOAuth({
        provider: "google", options: { redirectTo: `${window.location.origin}/login`, queryParams: { prompt: "select_account" } },
      });
      if (error) throw error;
    } catch { sessionStorage.removeItem(PENDING); setMsg("Google sign-in is unavailable. Try again or use phone and password."); setBusy(false); }
  }

  return <section className="ct-card mt-6 rounded-3xl bg-white p-5" aria-label="Other sign-in options">
    <p className="text-sm font-semibold text-ink">{googleEnabled ? "Other ways to sign in" : t("orEmail", lang)}</p>
    <label className="mt-3 flex items-start gap-3 text-xs leading-relaxed text-ink/75">
      <input type="checkbox" className="mt-1 h-5 w-5 shrink-0 accent-brand" checked={consent} onChange={e => setConsent(e.target.checked)} disabled={busy} />
      <span>{t("emailConsent", lang)} <a href="/data-safety" className="text-brand underline">{t("dataSafety", lang)}</a></span>
    </label>
    {msg && <p role="status" className="mt-3 text-sm text-brand">{msg}</p>}
    {access ? <>
      <p className="mt-3 text-xs text-ink/70">{busy ? "Finishing sign-in..." : "Your identity is verified. Finish signing in to CareTrail."}</p>
      {!busy && <button type="button" disabled={needsConsent && !consent} onClick={() => void exchange(consent)} className="mt-3 w-full rounded-xl bg-brand p-3 font-semibold text-white disabled:opacity-50">Finish sign-in</button>}
    </> : <>
      {googleEnabled && <>
        <button type="button" onClick={() => void google()} disabled={busy} className="mt-4 min-h-12 w-full rounded-xl border border-ink/20 bg-white p-3 font-medium text-ink disabled:opacity-50">Continue with Google</button>
        <p className="mt-2 text-xs leading-relaxed text-ink/70">Google sign-in uses its own profile. To view an existing phone/password journey, use that login above.</p>
      </>}
      <form onSubmit={sendLink} className="mt-4">
        <label htmlFor="supabase-email" className="text-xs font-medium text-ink/75">{t("email", lang)}</label>
        <input id="supabase-email" className="mt-2 w-full rounded-xl border border-ink/15 bg-white p-3" autoComplete="email" type="email" value={email} onChange={e => setEmail(e.target.value)} required disabled={busy} />
        <button disabled={busy} className="mt-3 w-full rounded-xl bg-brand p-3 font-semibold text-white disabled:opacity-50">{t("sendLink", lang)}</button>
      </form>
    </>}
  </section>;
}
