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
  const [evidenceUrl, setEvidenceUrl] = useState("");
  const [evidenceText, setEvidenceText] = useState("");
  const [confirmChecked, setConfirmChecked] = useState(false);
  const [error, setError] = useState("");
  const [lang] = useLang();
  const tr = useTr(lang);

  useEffect(() => {
    return () => { if (evidenceUrl) URL.revokeObjectURL(evidenceUrl); };
  }, [evidenceUrl]);
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
      setConfirmChecked(false);
      // The local File is the exact uploaded evidence; it stays beside the proposed values.
      setEvidenceUrl(URL.createObjectURL(file));
      setEvidenceText(file.type.startsWith("text/") ? (await file.text()).slice(0, 5000) : "");
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
    if (!confirmChecked) { setError("Check the original report and confirm these values first."); return; }
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
    setStep("pick"); setFile(null); setDocumentId(""); setValues([]); setFlags([]); setError(""); setExtractMessage(""); setEvidenceUrl(""); setEvidenceText(""); setConfirmChecked(false);
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
          <section className="rounded-xl border border-amber-300 bg-white p-3">
            <h2 className="font-semibold">Original report: {file?.name}</h2>
            <p className="mt-1 text-xs">Compare every value, unit and date with the original before saving. Blurry, conflicting or missing-unit results should not be confirmed.</p>
            {file?.type.startsWith("image/") && evidenceUrl ? <img className="mt-2 max-h-80 w-full object-contain" alt="Original uploaded report" src={evidenceUrl} /> : null}
            {file?.type === "application/pdf" && evidenceUrl ? <iframe className="mt-2 h-80 w-full" title="Original uploaded PDF" src={evidenceUrl} /> : null}
            {file?.type.startsWith("text/") && evidenceText ? <pre className="mt-2 max-h-48 overflow-auto whitespace-pre-wrap break-words rounded-lg bg-ink/5 p-2 text-xs">{evidenceText}</pre> : null}
            {file?.type.startsWith("text/") && evidenceUrl ? <a className="mt-2 inline-block text-brand underline" href={evidenceUrl} target="_blank" rel="noreferrer">Open full original text report</a> : null}
          </section>
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
                <select value={v.unit} aria-label={`Unit for ${v.label}`}
                  onChange={(e) => setValues(values.map((x, j) => j === i ? { ...x, unit: e.target.value } : x))}
                  className="rounded-lg border border-ink/15 p-2 text-sm">
                  <option value="">Select unit</option>
                  {(v.code === "hb" ? ["g/dL", "g/L"] : v.code.startsWith("bp") ? ["mmHg"] : ["mg/dL", "mmol/L"]).map((unit) => <option key={unit} value={unit}>{unit}</option>)}
                </select>
                {v.reference_range && <span className="ml-auto text-xs text-ink/50">{tr("normal range")}: {v.reference_range}</span>}
              </div>
              <input type="date" value={v.observed_on}
                onChange={(e) => setValues(values.map((x, j) => (j === i ? { ...x, observed_on: e.target.value } : x)))}
                className="mt-2 rounded-lg border border-ink/15 p-2 text-sm" />
            </div>
          ))}
          <label className="flex items-start gap-2 rounded-xl bg-amber-50 p-3 text-sm">
            <input type="checkbox" checked={confirmChecked} onChange={e => setConfirmChecked(e.target.checked)} />
            I compared the selected values, units and dates with the original report.
          </label>
          <div className="flex gap-3">
            <button onClick={reset} className="flex-1 rounded-xl border border-ink/15 bg-white p-3 text-sm font-semibold">{tr("Cancel")}</button>
            <button onClick={onConfirm} disabled={values.length === 0 || !confirmChecked}
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
