"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { t, type Lang } from "@/lib/i18n";

export default function NewJourneyPage() {
  const router = useRouter();
  const [lmp, setLmp] = useState("");
  const [edd, setEdd] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const lang: Lang = "en";

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!lmp && !edd) { setError("Give one of the two dates"); return; }
    setBusy(true); setError("");
    try {
      await api("/journeys", { method: "POST", body: JSON.stringify(lmp ? { lmp } : { edd }) });
      router.push("/");
    } catch (err) { setError(err instanceof Error ? err.message : "Could not start journey"); setBusy(false); }
  }

  return (
    <main className="pt-16">
      <h1 className="text-2xl font-bold text-brand">{t("startJourney", lang)}</h1>
      <p className="mt-2 text-sm text-ink/60">
        We build your timeline from one date. Everything after that updates on its own.
      </p>
      <form onSubmit={onSubmit} className="mt-8 space-y-5">
        <div>
          <label className="block text-sm font-medium">{t("lmpLabel", lang)}</label>
          <input type="date" value={lmp} onChange={(e) => { setLmp(e.target.value); if (e.target.value) setEdd(""); }}
            className="mt-1 w-full rounded-xl border border-ink/15 bg-white p-3" />
        </div>
        <div>
          <label className="block text-sm font-medium">{t("eddLabel", lang)}</label>
          <input type="date" value={edd} onChange={(e) => { setEdd(e.target.value); if (e.target.value) setLmp(""); }}
            className="mt-1 w-full rounded-xl border border-ink/15 bg-white p-3" />
        </div>
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button disabled={busy} className="w-full rounded-xl bg-brand p-3 font-semibold text-white disabled:opacity-50">
          {t("startJourney", lang)}
        </button>
      </form>
    </main>
  );
}
