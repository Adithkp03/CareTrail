"use client";
import { useEffect, useState } from "react";
import { Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { api, apiForm, getToken } from "@/lib/api";
import { t, useLang, useTr } from "@/lib/i18n";
import type { Milestone } from "@/lib/types";

const STATUS_COLOR = { done: "bg-green-100 text-green-800", now: "bg-brand text-white", upcoming: "bg-blue-100 text-blue-800", next: "bg-ink/10 text-ink/60" } as const;

function MilestoneView() {
  const id = useSearchParams().get("id") ?? "";
  const router = useRouter();
  const [m, setM] = useState<Milestone | null>(null);
  const [lang] = useLang();
  const tr = useTr(lang);
  const [scheduleDate, setScheduleDate] = useState("");
  const [error, setError] = useState("");
  const [explanation, setExplanation] = useState<{ text: string; provider: string } | null>(null);
  const [listenMsg, setListenMsg] = useState("");
  const [askOpen, setAskOpen] = useState(false);
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState<{ answer: string; urgent: boolean; note?: string; citation?: { source: string; topic: string } | null } | null>(null);
  const [asking, setAsking] = useState(false);

  async function load() {
    // Offline fallback: serve last-loaded content from localStorage on failure.
    try {
      const fresh = await api<Milestone>(`/milestones/${id}`);
      setM(fresh);
      localStorage.setItem(`ct:ms:${id}`, JSON.stringify(fresh));
    } catch {
      const cached = localStorage.getItem(`ct:ms:${id}`);
      if (cached) setM(JSON.parse(cached));
    }
    try {
      const ex = await api<{ text: string; provider: string }>(`/milestones/${id}/explanation?lang=${lang}`);
      setExplanation(ex);
      localStorage.setItem(`ct:expl:${id}:${lang}`, JSON.stringify(ex));
    } catch {
      const cached = localStorage.getItem(`ct:expl:${id}:${lang}`);
      if (cached) setExplanation(JSON.parse(cached));
    }
  }

  async function listen() {
    setListenMsg("");
    try {
      const token = getToken();
      const res = await fetch(`${process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8000"}/milestones/${id}/explanation/audio?lang=${lang}`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok) throw new Error("no-audio");
      const blob = await res.blob();
      new Audio(URL.createObjectURL(blob)).play();
    } catch {
      setListenMsg(tr("Audio is not available right now. Please try again."));
    }
  }

  async function ask() {
    if (!question.trim()) return;
    setAsking(true);
    setAnswer(null);
    try {
      setAnswer(await api("/ask", { method: "POST", body: JSON.stringify({ question, lang }) }));
    } catch (e) {
      setAnswer({ answer: e instanceof Error ? e.message : tr("Something went wrong. Please try again."), urgent: false });
    } finally {
      setAsking(false);
    }
  }

  async function askVoice() {
    setListenMsg("");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const rec = new MediaRecorder(stream);
      const chunks: Blob[] = [];
      rec.ondataavailable = (e) => chunks.push(e.data);
      const done = new Promise<void>((resolve) => { rec.onstop = () => resolve(); });
      rec.start();
      setListenMsg(tr("Listening… speak your question now"));
      await new Promise((r) => setTimeout(r, 4000));
      rec.stop();
      await done;
      stream.getTracks().forEach((tr) => tr.stop());
      const form = new FormData();
      form.append("file", new Blob(chunks, { type: rec.mimeType }), "question.webm");
      form.append("lang", lang);
      setAsking(true);
      const res = await apiForm<{ transcript: string; answer: string; urgent: boolean; audio_base64: string | null }>("/ask/voice", form);
      setQuestion(res.transcript);
      setAnswer({ answer: res.answer, urgent: res.urgent });
      if (res.audio_base64) new Audio(`data:audio/wav;base64,${res.audio_base64}`).play();
      setListenMsg("");
    } catch {
      setListenMsg(tr("Voice questions are not available right now. You can type your question instead."));
    } finally {
      setAsking(false);
    }
  }
  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    load().catch((e) => setError(e instanceof Error ? e.message : "Failed"));
  }, [id, router, lang]);

  async function complete() {
    await api(`/milestones/${id}/complete`, { method: "POST", body: JSON.stringify({}) });
    await load();
  }
  async function schedule() {
    if (!scheduleDate) return;
    await api(`/milestones/${id}/schedule`, { method: "POST", body: JSON.stringify({ scheduled_date: scheduleDate }) });
    await load();
  }

  if (error) return <main className="pt-16 text-red-600">{error}</main>;
  if (!m) return <main className="pt-16 text-ink/50">{t("loading", lang)}</main>;

  return (
    <main className="pt-6">
      <button onClick={() => router.back()} className="text-sm text-brand">← {t("appName", lang)}</button>
      <div className="mt-2 flex items-start justify-between gap-2">
        <h1 className="text-xl font-bold">{tr(m.title)}</h1>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${STATUS_COLOR[m.status]}`}>
          {t(m.status, lang)}{m.overdue ? ` · ${t("overdue", lang)}` : ""}
        </span>
      </div>
      <p className="mt-1 text-sm text-ink/60">
        {t("window", lang)}: {m.window_weeks[0]}-{m.window_weeks[1]} {t("weeks", lang)}
        {m.scheduled_date ? ` · 📅 ${m.scheduled_date}` : ""}
        {m.completed_at ? ` · ✓ ${m.completed_at}` : ""}
      </p>

      {m.signoff ? (
        <div className="mt-4 rounded-2xl border border-green-300 bg-green-50 p-3 text-sm">
          ✅ {t("signedOffBy", lang)} <b>{m.signoff.doctor_name}</b>
          {m.signoff.note ? <p className="mt-1 text-ink/70">{tr(m.signoff.note)}</p> : null}
        </div>
      ) : null}

      {m.prep_notes ? (
        <section className="mt-4 rounded-2xl bg-white p-4 shadow-sm">
          <h2 className="text-sm font-semibold">{t("prepNotes", lang)}</h2>
          <p className="mt-1 text-sm text-ink/70">{tr(m.prep_notes)}</p>
        </section>
      ) : null}

      {explanation ? (
        <section className="mt-4 rounded-2xl bg-white p-4 shadow-sm">
          <p className="text-sm text-ink/80">{explanation.text}</p>
        </section>
      ) : null}

      <div className="mt-4 grid grid-cols-2 gap-2">
        <button onClick={listen} className="rounded-xl bg-white p-3 text-sm font-medium shadow-sm">
          🔊 {t("listen", lang)}
        </button>
        <button onClick={() => setAskOpen(!askOpen)} className="rounded-xl bg-white p-3 text-sm font-medium shadow-sm">
          🎙️ {t("ask", lang)}
        </button>
      </div>
      {listenMsg ? <p className="mt-1 text-center text-xs text-ink/50">{listenMsg}</p> : null}

      {askOpen ? (
        <section className="mt-3 rounded-2xl bg-white p-4 shadow-sm">
          <div className="flex gap-2">
            <input value={question} onChange={(e) => setQuestion(e.target.value)}
              placeholder={tr("Ask about this check-up…")}
              className="flex-1 rounded-xl border border-ink/15 p-3 text-sm" />
            <button onClick={ask} disabled={asking} className="rounded-xl bg-brand px-4 text-sm font-semibold text-white disabled:opacity-40">
              {asking ? "…" : t("ask", lang)}
            </button>
            <button onClick={askVoice} disabled={asking} title={tr("Ask by voice")} className="rounded-xl border border-brand px-3 text-brand disabled:opacity-40">🎙️</button>
          </div>
          {answer ? (
            <p className={`mt-3 rounded-xl p-3 text-sm ${answer.urgent ? "bg-red-50 text-red-800 ring-1 ring-red-200" : "bg-ink/5 text-ink/80"}`}>
              {answer.answer}
              {answer.note ? <span className="block mt-1 text-xs text-ink/50">{tr(answer.note)}</span> : null}
              {answer.citation ? <span className="block mt-1 text-xs text-ink/50">{tr("Source")}: {tr(answer.citation.source)}</span> : null}
            </p>
          ) : null}
        </section>
      ) : null}

      {m.observations && m.observations.length > 0 ? (
        <section className="mt-4 rounded-2xl bg-white p-4 shadow-sm">
          <h2 className="text-sm font-semibold">{t("results", lang)}</h2>
          <ul className="mt-2 space-y-1 text-sm">
            {m.observations.map((o) => (
              <li key={o.id} className="flex justify-between">
                <span className="uppercase text-ink/60">{tr(o.code.replace(/_/g, " "))}</span>
                <span className="font-medium">{o.value} {o.unit}</span>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      {m.documents && m.documents.length > 0 ? (
        <section className="mt-4 rounded-2xl bg-white p-4 shadow-sm">
          <h2 className="text-sm font-semibold">{t("reports", lang)}</h2>
          <ul className="mt-2 space-y-1 text-sm text-ink/70">
            {m.documents.map((d) => <li key={d.id}>📄 {d.filename} ({tr(d.status)})</li>)}
          </ul>
        </section>
      ) : null}

      {m.status !== "done" ? (
        <section className="mt-6 space-y-2">
          <button onClick={complete} className="w-full rounded-xl bg-brand p-3 font-semibold text-white">
            {t("markDone", lang)}
          </button>
          <div className="flex gap-2">
            <input type="date" value={scheduleDate} onChange={(e) => setScheduleDate(e.target.value)}
              className="flex-1 rounded-xl border border-ink/15 bg-white p-3" />
            <button onClick={schedule} className="rounded-xl border border-brand px-4 font-medium text-brand">
              {t("schedule", lang)}
            </button>
          </div>
        </section>
      ) : null}
    </main>
  );
}

export default function MilestonePage() {
  return (
    <Suspense fallback={<main className="pt-16 text-ink/50">…</main>}>
      <MilestoneView />
    </Suspense>
  );
}
