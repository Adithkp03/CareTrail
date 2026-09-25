"use client";
import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense } from "react";
import { api, apiForm, getToken } from "@/lib/api";
import { t, useLang, useTr } from "@/lib/i18n";
import type { Flag, Journey } from "@/lib/types";

type ProposedValue = {
  code: string;
  label: string;
  value: number;
  unit: string;
  reference_range: string;
  observed_on: string;
  confidence: number;
  include: boolean;
};

type Step = "pick" | "extracting" | "confirm" | "done";

// Phase 3: upload a report, the backend proposes values (Sarvam Vision / Gemini when
// keys exist, offline parser otherwise), the mother reviews the "Is this right?" card,
// and only then do values land on her timeline. Flags come from the rules engine.
function UploadView() {
  const selectedMilestone = useSearchParams().get("milestone") ?? "";
  const router = useRouter();
  const [journey, setJourney] = useState<Journey | null>(null);
  const [milestoneId, setMilestoneId] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [step, setStep] = useState<Step>("pick");
  const [documentId, setDocumentId] = useState("");
  const [provider, setProvider] = useState("");
  const [language, setLanguage] = useState("");
  const [values, setValues] = useState<ProposedValue[]>([]);
  const [extractMessage, setExtractMessage] = useState("");
  const [savedCount, setSavedCount] = useState(0);
  const [flags, setFlags] = useState<Flag[]>([]);
  const [error, setError] = useState("");
  const [lang] = useLang();
  const tr = useTr(lang);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    (async () => {
      const list = await api<{ journeys: { journey_id: string }[] }>("/journeys");
      if (list.journeys.length === 0) return;
      const j = await api<Journey>(`/journey/${list.journeys[0].journey_id}`);
      setJourney(j);
      if (j.milestones.some((m) => m.id === selectedMilestone)) setMilestoneId(selectedMilestone);
    })();
  }, [router, selectedMilestone]);

  async function onUpload(e: React.FormEvent) {
    e.preventDefault();
    if (!journey || !file) return;
    setError("");
    setStep("extracting");
    try {
      const form = new FormData();
      form.append("file", file);
      if (milestoneId) form.append("milestone_id", milestoneId);
      const up = await apiForm<{ document_id: string }>(`/journeys/${journey.journey_id}/documents/upload`, form);
      setDocumentId(up.document_id);
      const ext = await api<{ language: string; provider: string; proposed: Omit<ProposedValue, "include">[]; message: string | null }>(
        `/documents/${up.document_id}/extract`, { method: "POST" }
      );
      setLanguage(ext.language);
      setProvider(ext.provider);
      setValues(ext.proposed.map((v) => ({ ...v, include: true })));
      setExtractMessage(ext.message ?? "");
      setStep("confirm");
    } catch (err) {
      setError(err instanceof Error ? err.message : tr("Upload failed"));
      setStep("pick");
    }
  }

  async function onConfirm() {
    setError("");
    const chosen = values.filter((v) => v.include);
    if (chosen.length === 0) { setError(tr("Nothing selected to save.")); return; }
    try {
      const res = await api<{ observations: unknown[]; flags: Flag[] }>(`/documents/${documentId}/confirm`, {
        method: "POST",
        body: JSON.stringify({
          milestone_id: milestoneId || null,
          values: chosen.map(({ code, value, unit, observed_on }) => ({ code, value, unit, observed_on })),
        }),
      });
      setSavedCount(res.observations.length);
      setFlags(res.flags);
      setStep("done");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Confirm failed");
    }
  }

  function reset() {
    setStep("pick"); setFile(null); setDocumentId(""); setValues([]); setFlags([]); setError(""); setExtractMessage("");
  }

  const input = "w-full rounded-xl border border-ink/15 bg-white p-3 text-sm";

  return (
    <main className="pt-6">
      <a href="/" className="text-sm text-brand">← {t("appName", lang)}</a>
      <h1 className="mt-2 text-xl font-bold">{t("uploadReport", lang)}</h1>

      {step === "pick" && (
        <form onSubmit={onUpload} className="mt-6 space-y-4">
          <input type="file" accept="application/pdf,image/*,text/plain" capture="environment"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)} className={input} required />
          <select value={milestoneId} onChange={(e) => setMilestoneId(e.target.value)} className={input}>
            <option value="">{`— ${tr("Link to a check-up (optional)")} —`}</option>
            {journey?.milestones.map((m) => <option key={m.id} value={m.id}>{tr(m.title)}</option>)}
          </select>
          <button className="w-full rounded-xl bg-brand p-3 font-semibold text-white">{t("uploadReport", lang)}</button>
        </form>
      )}

      {step === "extracting" && <p className="mt-8 text-center text-sm text-ink/60">{tr("Reading your report…")}</p>}

      {step === "confirm" && (
        <div className="mt-4 space-y-4">
          <p className="rounded-xl bg-white p-3 text-sm shadow-sm">
            {tr("Is this right? Check the values before they go on your timeline.")}
            {provider === "offline-parser"
              ? ` (${tr("Read on this device.")})`
              : ` (${tr("Read by AI")}: ${provider}.)`}
            {language && language !== "en" && ` ${tr("Detected language")}: ${language}.`}
          </p>
          {extractMessage && <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-800">{tr(extractMessage)}</p>}
          {values.map((v, i) => (
            <div key={v.code}
              className={`rounded-xl p-3 shadow-sm ${v.confidence < 0.7 ? "bg-amber-50 ring-1 ring-amber-300" : "bg-white"}`}>
              <label className="flex items-center gap-2 text-sm font-semibold">
                <input type="checkbox" checked={v.include}
                  onChange={(e) => setValues(values.map((x, j) => (j === i ? { ...x, include: e.target.checked } : x)))} />
                {tr(v.label)}
                {v.confidence < 0.7 && <span className="text-xs font-normal text-amber-700">{tr("please double-check")}</span>}
              </label>
              <div className="mt-2 flex items-center gap-2">
                <input type="number" step="any" value={v.value}
                  onChange={(e) => setValues(values.map((x, j) => (j === i ? { ...x, value: Number(e.target.value) } : x)))}
                  className="w-28 rounded-lg border border-ink/15 p-2 text-sm" />
                <span className="text-sm text-ink/60">{v.unit}</span>
                {v.reference_range && <span className="ml-auto text-xs text-ink/50">{tr("normal range")}: {v.reference_range}</span>}
              </div>
              <input type="date" value={v.observed_on}
                onChange={(e) => setValues(values.map((x, j) => (j === i ? { ...x, observed_on: e.target.value } : x)))}
                className="mt-2 rounded-lg border border-ink/15 p-2 text-sm" />
            </div>
          ))}
          <div className="flex gap-3">
            <button onClick={reset} className="flex-1 rounded-xl border border-ink/15 bg-white p-3 text-sm font-semibold">{tr("Cancel")}</button>
            <button onClick={onConfirm} disabled={values.length === 0}
              className="flex-1 rounded-xl bg-brand p-3 font-semibold text-white disabled:opacity-40">{tr("Save to timeline")}</button>
          </div>
        </div>
      )}

      {step === "done" && (
        <div className="mt-4 space-y-4">
          <p className="rounded-xl bg-white p-3 text-sm text-brand shadow-sm">✅ {savedCount} {tr(savedCount === 1 ? "value saved to your timeline." : "values saved to your timeline.")}</p>
          {flags.length > 0 && (
            <div className="space-y-2">
              {flags.map((f) => (
                <p key={f.code} className="rounded-xl bg-red-50 p-3 text-sm text-red-800 ring-1 ring-red-200">
                  ⚠️ {tr(f.label)}: {f.value} {f.unit}. {tr(f.message)}
                </p>
              ))}
              <p className="text-xs text-ink/60">{tr(flags.length === 1 ? "Your doctor will see this flag in the review queue." : "Your doctor will see these flags in the review queue.")}</p>
            </div>
          )}
          <div className="flex gap-3">
            <a href="/" className="flex-1 rounded-xl bg-brand p-3 text-center font-semibold text-white">{tr("View timeline")}</a>
            <button onClick={reset} className="flex-1 rounded-xl border border-ink/15 bg-white p-3 text-sm font-semibold">{tr("Upload another")}</button>
          </div>
        </div>
      )}

      {error && <p className="mt-4 rounded-xl bg-red-50 p-3 text-sm text-red-700">{tr(error)}</p>}
    </main>
  );
}

export default function UploadPage() {
  return <Suspense fallback={<main className="pt-16">Loading…</main>}><UploadView /></Suspense>;
}
