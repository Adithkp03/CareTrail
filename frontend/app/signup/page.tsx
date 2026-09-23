"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { api, setToken } from "@/lib/api";
import { t, LANGS, type Lang } from "@/lib/i18n";

export default function SignupPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [lang, setLang] = useState<Lang>("en");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true); setError("");
    try {
      const res = await api<{ token: string }>("/auth/signup", {
        method: "POST",
        body: JSON.stringify({ name: name.trim(), phone: phone.trim(), password, language: lang }),
      }, false);
      setToken(res.token);
      router.push("/new-journey");
    } catch (err) { setError(err instanceof Error ? err.message : "Signup failed"); setBusy(false); }
  }

  return (
    <main className="pt-16">
      <h1 className="text-2xl font-bold text-brand">{t("signup", lang)}</h1>
      <form onSubmit={onSubmit} className="mt-8 space-y-4">
        <input className="w-full rounded-xl border border-ink/15 bg-white p-3" placeholder={t("yourName", lang)}
          value={name} onChange={(e) => setName(e.target.value)} required />
        <input className="w-full rounded-xl border border-ink/15 bg-white p-3" placeholder={t("phone", lang)}
          value={phone} onChange={(e) => setPhone(e.target.value)} inputMode="tel" required />
        <input className="w-full rounded-xl border border-ink/15 bg-white p-3" placeholder={t("password", lang)}
          type="password" value={password} onChange={(e) => setPassword(e.target.value)} minLength={6} required />
        <label className="block text-sm text-ink/60">{t("language", lang)}</label>
        <div className="flex gap-2">
          {LANGS.map((l) => (
            <button type="button" key={l.code} onClick={() => setLang(l.code)}
              className={`rounded-full px-3 py-1 text-sm ${lang === l.code ? "bg-brand text-white" : "bg-white text-ink/70"}`}>
              {l.label}
            </button>
          ))}
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button disabled={busy} className="w-full rounded-xl bg-brand p-3 font-semibold text-white disabled:opacity-50">
          {t("signup", lang)}
        </button>
      </form>
    </main>
  );
}
