"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, getToken, setToken } from "@/lib/api";
import { t, LANGS, type Lang } from "@/lib/i18n";
import type { Journey, Milestone, MilestoneStatus, NextUp } from "@/lib/types";

const TYPE_ICON: Record<Milestone["type"], string> = {
  visit: "🩺", scan: "🖥️", test: "🧪", vaccination: "💉", review: "📋",
};
const ZONE_ORDER: MilestoneStatus[] = ["now", "upcoming", "next", "done"];

function MilestoneCard({ m, lang }: { m: Milestone; lang: Lang }) {
  const router = useRouter();
  return (
    <button onClick={() => router.push(`/milestone?id=${m.id}`)}
      className="flex w-full items-center gap-3 rounded-2xl bg-white p-3 text-left shadow-sm">
      <span className="text-xl">{TYPE_ICON[m.type]}</span>
      <span className="flex-1">
        <span className="block font-medium">{m.title}</span>
        <span className="block text-xs text-ink/50">
          {m.status === "done" && m.completed_at ? `✓ ${m.completed_at}` : null}
          {m.status === "upcoming" && m.scheduled_date ? `📅 ${m.scheduled_date}` : null}
          {m.status === "now" ? `${t("window", lang)}: ${m.window_weeks[0]}-${m.window_weeks[1]} ${t("weeks", lang)}` : null}
          {m.status === "next" ? `${m.window_weeks[0]}-${m.window_weeks[1]} ${t("weeks", lang)}` : null}
        </span>
      </span>
      {m.signoff ? <span title={`${t("signedOffBy", lang)} ${m.signoff.doctor_name}`}>✅</span> : null}
      {m.overdue ? <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs text-red-700">{t("overdue", lang)}</span> : null}
    </button>
  );
}

export default function HomePage() {
  const router = useRouter();
  const [journey, setJourney] = useState<Journey | null>(null);
  const [nextUp, setNextUp] = useState<NextUp | null>(null);
  const [lang, setLang] = useState<Lang>("en");
  const [error, setError] = useState("");

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    (async () => {
      try {
        const list = await api<{ journeys: { journey_id: string }[] }>("/journeys");
        if (list.journeys.length === 0) { router.replace("/new-journey"); return; }
        const j = await api<Journey>(`/journey/${list.journeys[0].journey_id}`);
        setJourney(j);
        api<NextUp>(`/journey/${j.journey_id}/next-up`).then(setNextUp).catch(() => {});
        setLang((["en", "ml", "hi"].includes(j.patient.language) ? j.patient.language : "en") as Lang);
      } catch (err) { setError(err instanceof Error ? err.message : "Failed to load"); }
    })();
  }, [router]);

  if (error) return <main className="pt-16 text-red-600">{error}</main>;
  if (!journey) return <main className="pt-16 text-ink/50">Loading…</main>;

  const zones: Record<MilestoneStatus, Milestone[]> = { now: [], upcoming: [], next: [], done: [] };
  for (const m of journey.milestones) zones[m.status].push(m);

  return (
    <main className="pt-6">
      <header className="flex items-center justify-between">
        <div>
          <p className="text-sm text-ink/60">{journey.patient.name}</p>
          <h1 className="text-2xl font-bold">
            {journey.gestational_age.weeks} {t("weeks", lang)}
            {journey.gestational_age.plus_days > 0 ? ` + ${journey.gestational_age.plus_days} ${t("days", lang)}` : ""}
          </h1>
        </div>
        <div className="flex gap-1">
          {LANGS.map((l) => (
            <button key={l.code} onClick={() => setLang(l.code)}
              className={`rounded-full px-2 py-1 text-xs ${lang === l.code ? "bg-brand text-white" : "bg-white text-ink/70"}`}>
              {l.code.toUpperCase()}
            </button>
          ))}
        </div>
      </header>

      {journey.next_appointment ? (
        <div className="mt-4 rounded-2xl bg-brand p-4 text-white shadow">
          <p className="text-xs uppercase tracking-wide opacity-80">{t("nextAppointment", lang)}</p>
          <p className="mt-1 font-semibold">{journey.next_appointment.title}</p>
          <p className="text-sm opacity-90">📅 {journey.next_appointment.scheduled_date}</p>
        </div>
      ) : null}

      {nextUp?.milestone ? (
        <div className="mt-3 rounded-2xl bg-white p-4 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-ink/50">Get ready: {nextUp.milestone.title}</p>
          {nextUp.purpose ? <p className="mt-2 text-sm text-ink/80">{nextUp.purpose}</p> : null}
          {nextUp.what_to_bring ? <p className="mt-2 text-sm">🎒 <span className="text-ink/80">{nextUp.what_to_bring}</span></p> : null}
          {nextUp.fasting ? <p className="mt-2 text-sm">🍽️ <span className="font-medium text-amber-800">{nextUp.fasting}</span></p> : null}
          {nextUp.questions && nextUp.questions.length > 0 ? (
            <details className="mt-2 text-sm">
              <summary className="cursor-pointer text-brand">Questions worth asking</summary>
              <ul className="mt-1 list-disc pl-5 text-ink/70">
                {nextUp.questions.map((q) => <li key={q}>{q}</li>)}
              </ul>
            </details>
          ) : null}
        </div>
      ) : null}

      {journey.flags.length > 0 ? (
        <a href="/doctor" className="mt-3 block rounded-2xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          ⚠️ {journey.flags.length} {t("flagsQueue", lang)}
        </a>
      ) : null}

      <div className="mt-6 space-y-6">
        {ZONE_ORDER.map((zone) =>
          zones[zone].length === 0 ? null : (
            <section key={zone}>
              <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-ink/50">
                {t(zone, lang)} · {zones[zone].length}
              </h2>
              <div className={`space-y-2 ${zone === "done" ? "opacity-70" : ""}`}>
                {zones[zone].map((m) => <MilestoneCard key={m.id} m={m} lang={lang} />)}
              </div>
            </section>
          )
        )}
      </div>

      <section className="mt-8 rounded-2xl border border-red-200 bg-red-50 p-4">
        <h2 className="text-sm font-semibold text-red-800">{t("dangerSigns", lang)}:</h2>
        <ul className="mt-1 list-inside list-disc text-sm text-red-700">
          {journey.danger_signs.map((s) => <li key={s}>{s}</li>)}
        </ul>
      </section>

      <nav className="mt-8 grid grid-cols-3 gap-2 text-center text-sm">
        <a href="/upload" className="rounded-xl bg-white p-3 font-medium text-brand shadow-sm">{t("uploadReport", lang)}</a>
        <a href="/doctor" className="rounded-xl bg-white p-3 font-medium text-brand shadow-sm">{t("doctorView", lang)}</a>
        <button onClick={() => { setToken(null); router.push("/login"); }}
          className="rounded-xl bg-white p-3 font-medium text-ink/60 shadow-sm">{t("logout", lang)}</button>
      </nav>
    </main>
  );
}
