"use client";
import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api, getToken } from "@/lib/api";
import { t, type Lang } from "@/lib/i18n";
import type { Milestone } from "@/lib/types";

const STATUS_COLOR = { done: "bg-green-100 text-green-800", now: "bg-brand text-white", upcoming: "bg-blue-100 text-blue-800", next: "bg-ink/10 text-ink/60" } as const;

export default function MilestonePage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const [m, setM] = useState<Milestone | null>(null);
  const [lang] = useState<Lang>("en");
  const [scheduleDate, setScheduleDate] = useState("");
  const [error, setError] = useState("");

  async function load() { setM(await api<Milestone>(`/milestones/${id}`)); }
  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    load().catch((e) => setError(e instanceof Error ? e.message : "Failed"));
  }, [id, router]);

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
  if (!m) return <main className="pt-16 text-ink/50">Loading…</main>;

  return (
    <main className="pt-6">
      <button onClick={() => router.back()} className="text-sm text-brand">← {t("appName", lang)}</button>
      <div className="mt-2 flex items-start justify-between gap-2">
        <h1 className="text-xl font-bold">{m.title}</h1>
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
          {m.signoff.note ? <p className="mt-1 text-ink/70">{m.signoff.note}</p> : null}
        </div>
      ) : null}

      {m.prep_notes ? (
        <section className="mt-4 rounded-2xl bg-white p-4 shadow-sm">
          <h2 className="text-sm font-semibold">{t("prepNotes", lang)}</h2>
          <p className="mt-1 text-sm text-ink/70">{m.prep_notes}</p>
        </section>
      ) : null}

      <div className="mt-4 grid grid-cols-2 gap-2">
        <button disabled title={t("comingVoice", lang)} className="rounded-xl bg-white p-3 text-sm font-medium text-ink/40 shadow-sm">
          🔊 {t("listen", lang)}*
        </button>
        <button disabled title={t("comingVoice", lang)} className="rounded-xl bg-white p-3 text-sm font-medium text-ink/40 shadow-sm">
          🎙️ {t("ask", lang)}*
        </button>
      </div>
      <p className="mt-1 text-center text-xs text-ink/40">* {t("comingVoice", lang)}</p>

      {m.observations && m.observations.length > 0 ? (
        <section className="mt-4 rounded-2xl bg-white p-4 shadow-sm">
          <h2 className="text-sm font-semibold">{t("results", lang)}</h2>
          <ul className="mt-2 space-y-1 text-sm">
            {m.observations.map((o) => (
              <li key={o.id} className="flex justify-between">
                <span className="uppercase text-ink/60">{o.code.replace(/_/g, " ")}</span>
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
            {m.documents.map((d) => <li key={d.id}>📄 {d.filename} ({d.status})</li>)}
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
