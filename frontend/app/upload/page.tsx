"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, getToken } from "@/lib/api";
import { t, type Lang } from "@/lib/i18n";
import type { Journey } from "@/lib/types";

// Registers the uploaded report with the backend. Binary storage + AI extraction
// (Sarvam Vision / Gemini) land in phase 3; this proves the upload path end to end.
export default function UploadPage() {
  const router = useRouter();
  const [journey, setJourney] = useState<Journey | null>(null);
  const [milestoneId, setMilestoneId] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [message, setMessage] = useState("");
  const lang: Lang = "en";

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    (async () => {
      const list = await api<{ journeys: { journey_id: string }[] }>("/journeys");
      if (list.journeys.length === 0) return;
      setJourney(await api<Journey>(`/journey/${list.journeys[0].journey_id}`));
    })();
  }, [router]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!journey || !file) return;
    const res = await api<{ document_id: string }>("/documents", {
      method: "POST",
      body: JSON.stringify({
        journey_id: journey.journey_id,
        milestone_id: milestoneId || null,
        filename: file.name,
        content_type: file.type || "application/pdf",
      }),
    });
    setMessage(`✅ ${file.name} (${res.document_id.slice(0, 8)}…)`);
    setFile(null);
  }

  return (
    <main className="pt-6">
      <a href="/" className="text-sm text-brand">← {t("appName", lang)}</a>
      <h1 className="mt-2 text-xl font-bold">{t("uploadReport", lang)}</h1>
      <form onSubmit={onSubmit} className="mt-6 space-y-4">
        <input type="file" accept="application/pdf,image/*" capture="environment"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
          className="w-full rounded-xl border border-ink/15 bg-white p-3 text-sm" required />
        <select value={milestoneId} onChange={(e) => setMilestoneId(e.target.value)}
          className="w-full rounded-xl border border-ink/15 bg-white p-3 text-sm">
          <option value="">— milestone (optional) —</option>
          {journey?.milestones.map((m) => <option key={m.id} value={m.id}>{m.title}</option>)}
        </select>
        <button className="w-full rounded-xl bg-brand p-3 font-semibold text-white">{t("uploadReport", lang)}</button>
      </form>
      {message && <p className="mt-4 rounded-xl bg-white p-3 text-sm text-brand shadow-sm">{message}</p>}
    </main>
  );
}
