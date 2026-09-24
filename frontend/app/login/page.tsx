"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, setToken } from "@/lib/api";
import { t, LANGS, useLang } from "@/lib/i18n";
import SupabaseSignIn from "./supabase-signin";

export default function LoginPage() {
  const router = useRouter();
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [lang, setLang] = useLang();

  async function doLogin(p: string, pw: string) {
    const res = await api<{ token: string }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ phone: p, password: pw }),
    }, false);
    setToken(res.token);
    router.push("/");
  }

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError("");
    try { await doLogin(phone.trim(), password); }
    catch (err) { setError(err instanceof Error && err.message === "Wrong phone or password" ? t("wrongLogin", lang) : t("loginFailed", lang)); setBusy(false); }
  }

  async function tryDemo() {
    setBusy(true); setError("");
    try {
      await api("/demo/seed", { method: "POST" }, false);
      await doLogin("9000000001", "demo1234");
    } catch { setError(t("demoFailed", lang)); setBusy(false); }
  }

  return (
    <main className="pt-16">
      <h1 className="text-3xl font-bold text-brand">{t("appName", lang)}</h1>
      <p className="mt-1 text-sm text-ink/60">{t("tagline", lang)}</p>
      <form onSubmit={onSubmit} className="mt-8 space-y-4">
        <input className="w-full rounded-xl border border-ink/15 bg-white p-3" placeholder={t("phone", lang)}
          value={phone} onChange={(e) => setPhone(e.target.value)} inputMode="tel" required />
        <input className="w-full rounded-xl border border-ink/15 bg-white p-3" placeholder={t("password", lang)}
          type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button disabled={busy} className="w-full rounded-xl bg-brand p-3 font-semibold text-white disabled:opacity-50">
          {t("login", lang)}
        </button>
      </form>
      <button onClick={tryDemo} disabled={busy}
        className="mt-3 w-full rounded-xl border border-brand p-3 font-semibold text-brand disabled:opacity-50">
        {t("tryDemo", lang)}
      </button>
      <p className="mt-4 text-center text-sm">
        <a className="text-brand underline" href="/signup">{t("signup", lang)}</a>
      </p>
      <SupabaseSignIn lang={lang} />
      <div className="mt-8 flex justify-center gap-2">
        {LANGS.map((l) => (
          <button key={l.code} onClick={() => setLang(l.code)}
            className={`rounded-full px-3 py-1 text-sm ${lang === l.code ? "bg-brand text-white" : "bg-white text-ink/70"}`}>
            {l.label}
          </button>
        ))}
      </div>
    </main>
  );
}
