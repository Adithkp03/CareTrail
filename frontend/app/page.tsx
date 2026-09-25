"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, getToken, setToken } from "@/lib/api";
import { t, LANGS, useLang, useTr, type Lang } from "@/lib/i18n";
import CareIcon from "@/components/CareIcon";
import MovementTracker from "@/components/MovementTracker";
import { firstTrimesterSteps, firstVisitTests, milestoneIcons, careText } from "@/lib/first-trimester";
import type { Journey, Milestone, MilestoneStatus, NextUp } from "@/lib/types";

const TYPE_ICON: Record<Milestone["type"], string> = {
  visit: "visit", scan: "scan", test: "test", vaccination: "vaccination", review: "review",
};
const ZONE_ORDER: MilestoneStatus[] = ["now", "upcoming", "next", "done"];
const STAGES = ["firstTrimester", "secondTrimester", "thirdTrimester"] as const;
function trimester(week: number): number { return week < 14 ? 0 : week < 28 ? 1 : 2; }
function milestoneStage(m: Milestone): number { return trimester(m.window_weeks[0]); }

function MilestoneCard({ m, lang, tr }: { m: Milestone; lang: Lang; tr: (s: string) => string }) {
  const router = useRouter();
  return (
    <button onClick={() => router.push(`/milestone?id=${m.id}`)}
      className="flex w-full items-center gap-3 rounded-2xl bg-white p-3 text-left shadow-sm">
      <span className="text-xl"><CareIcon name={milestoneIcons[m.key] ?? TYPE_ICON[m.type]} size={20} /></span>
      <span className="flex-1">
        <span className="block font-medium">{tr(m.title)}</span>
        <span className="block text-xs text-ink/50">
          {m.status === "done" && m.completed_at ? `✓ ${m.completed_at}` : null}
          {m.status === "upcoming" && m.scheduled_date ? `📅 ${m.scheduled_date}` : null}
          {m.status === "now" ? `${t("window", lang)}: ${m.window_weeks[0]}-${m.window_weeks[1]} ${t("weeks", lang)}` : null}
          {m.status === "next" ? `${m.window_weeks[0]}-${m.window_weeks[1]} ${t("weeks", lang)}` : null}
        </span>
      </span>
      {m.signoff ? <span title={t("prototypeReview", lang)}>✅ {t("signedOffBy", lang)} {m.signoff.doctor_name}</span> : null}
      {m.overdue ? <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs text-red-700">{t("overdue", lang)}</span> : null}
    </button>
  );
}

export default function HomePage() {
  const router = useRouter();
  const [journey, setJourney] = useState<Journey | null>(null);
  const [nextUp, setNextUp] = useState<NextUp | null>(null);
  const [patientLang, setPatientLang] = useState<string | undefined>(undefined);
  const [lang, setLang] = useLang(patientLang);
  const tr = useTr(lang);
  const [error, setError] = useState("");
  const [offline, setOffline] = useState(false);

  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    (async () => {
      try {
        const list = await api<{ journeys: { journey_id: string }[] }>("/journeys");
        if (list.journeys.length === 0) { router.replace("/new-journey"); return; }
        const j = await api<Journey>(`/journey/${list.journeys[0].journey_id}`);
        setJourney(j);
        localStorage.setItem("ct:journey", JSON.stringify(j));
        api<NextUp>(`/journey/${j.journey_id}/next-up`)
          .then((n) => { setNextUp(n); localStorage.setItem("ct:nextup", JSON.stringify(n)); })
          .catch(() => {
            const cached = localStorage.getItem("ct:nextup");
            if (cached) setNextUp(JSON.parse(cached));
          });
        setPatientLang(j.patient.language);
      } catch (err) {
        // Offline fallback: last-loaded timeline from localStorage.
        const cached = localStorage.getItem("ct:journey");
        if (cached) {
          const j = JSON.parse(cached) as Journey;
          setJourney(j);
          const cu = localStorage.getItem("ct:nextup");
          if (cu) setNextUp(JSON.parse(cu));
          setPatientLang(j.patient.language);
          setOffline(true);
        } else {
          setError(err instanceof Error ? err.message : "Failed to load");
        }
      }
    })();
  }, [router]);

  if (error) return <main className="pt-16 text-red-600">{error}</main>;
  if (!journey) return <main className="pt-16 text-ink/50">{t("loading", lang)}</main>;

  const zones: Record<MilestoneStatus, Milestone[]> = { now: [], upcoming: [], next: [], done: [] };
  for (const m of journey.milestones) zones[m.status].push(m);
  const week = journey.gestational_age.weeks;
  const currentStage = trimester(week);
  const total = journey.milestones.length;
  const completed = journey.summary.done;
  const unresolved = journey.flags.filter((f) => !f.signed_off);
  const overdue = journey.milestones.filter((m) => m.overdue && m.status !== "done");
  const action = overdue[0] ?? zones.now[0] ?? zones.upcoming[0] ?? zones.next[0];
  const attention = [
    ...overdue.map((m) => `${t("overdueMilestone", lang)}: ${tr(m.title)}`),
    ...unresolved.map((f) => `${t("reviewPending", lang)}: ${tr(f.label)} (${f.value} ${f.unit})`),
  ];

  return (
    <main className="pt-6">
      {offline && (
        <p className="mb-3 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
          Offline - showing your last loaded timeline.
        </p>
      )}
      <header className="flex items-center justify-between">
        <div>
          <p className="text-sm text-ink/60">{journey.patient.name}</p>
          <h1 className="text-xl font-bold sm:text-2xl">
            {journey.gestational_age.weeks} {t("weeks", lang)}
            {` + ${journey.gestational_age.plus_days} ${t("days", lang)}`}
          </h1>
        </div>
        <div className="flex shrink-0 gap-1">
          <select value={lang} onChange={(e) => setLang(e.target.value as Lang)} aria-label={t("language", lang)} className="max-w-28 rounded-xl bg-white p-2 text-xs text-brand">
            {LANGS.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}
          </select>
        </div>
      </header>

      {!["en", "ml", "hi"].includes(lang) && <p className="mt-3 rounded-xl bg-amber-50 p-2 text-xs text-amber-900">{t("translationNote", lang)}</p>}
      <section className="mt-4 rounded-2xl bg-white p-5 shadow-sm" aria-label={t("journeyTitle", lang)}>
        <h2 className="text-lg font-bold text-brand">{t("journeyTitle", lang)}</h2>
        <p className="mt-1 text-sm text-ink/60">{t("youAreHere", lang)} · {t(STAGES[currentStage], lang)}</p>
        <p className="mt-3 text-2xl font-bold">{completed}/{total} <span className="text-sm font-normal text-ink/60">{t("completedOf", lang)}</span></p>
        <div className="mt-3 h-2 overflow-hidden rounded-full bg-ink/10" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={completed} aria-label={t("completedOf", lang)}>
          <div className="h-full rounded-full bg-brand" style={{ width: `${total ? 100 * completed / total : 0}%` }} />
        </div>
      </section>

      <section className="mt-3 rounded-2xl bg-white p-4 shadow-sm" aria-label={t("journeyTitle", lang)}>
        {STAGES.map((stage, i) => {
          const items = journey.milestones.filter((m) => milestoneStage(m) === i);
          const highlighted = i === currentStage
            ? [...items.filter((m) => m.status === "now" || m.status === "upcoming"), ...items.filter((m) => m.status === "next")].slice(0, 3)
            : i < currentStage ? items : items.slice(0, 1);
          const rest = items.filter((m) => !highlighted.includes(m));
          const row = (m: Milestone) => <a key={m.id} href={`/milestone?id=${m.id}`} className="flex min-h-8 items-start gap-2 rounded-lg px-1 py-1 text-sm text-ink/80 hover:bg-brand-soft">
            <span aria-hidden="true" className="shrink-0 text-lg"><CareIcon name={milestoneIcons[m.key] ?? TYPE_ICON[m.type]} size={20} /></span><span aria-hidden="true">{m.status === "done" ? "✓" : m.status === "next" ? "○" : "●"}</span>
            <span className="min-w-0 flex-1">{tr(m.title)}</span>
            {m.overdue ? <span className="text-xs text-red-700">{t("overdue", lang)}</span> : null}
            {m.signoff ? <span className="text-xs" title={t("prototypeReview", lang)}>✓ {t("signedOffBy", lang)} {m.signoff.doctor_name}</span> : null}
          </a>;
          return <div key={stage} className="relative border-l-2 border-brand/25 pb-3 pl-5 last:border-transparent last:pb-0">
            <span className={`absolute -left-[7px] top-1 h-3 w-3 rounded-full ${i === currentStage ? "bg-brand ring-4 ring-brand/20" : "bg-ink/25"}`} />
            <h3 className="text-sm font-semibold text-ink">{t(stage, lang)} {i === currentStage ? `· ${t("youAreHere", lang)}` : ""}</h3>
            <div className="mt-1">{highlighted.map(row)}</div>
            {rest.length > 0 ? <details className="mt-1 text-xs text-brand"><summary className="cursor-pointer">{t("showAllMilestones", lang)} · {items.length}</summary><div className="mt-1">{rest.map(row)}</div></details> : null}
          </div>;
        })}
        {journey.milestones.some((m) => m.signoff) ? <p className="mt-2 text-xs text-ink/50">{t("prototypeReview", lang)}</p> : null}
      </section>

      <details className="mt-3 rounded-2xl bg-white p-4 shadow-sm" open={currentStage === 0}>
        <summary className="cursor-pointer font-semibold text-brand"><span className="inline-block align-middle"><CareIcon name="pregnancy" size={20} /></span> {t("earlyCare", lang)} <span className="text-xs font-normal text-ink/60">· {t("earlyCareHint", lang)}</span></summary>
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          {firstTrimesterSteps.map((step) => <article key={step.title.en} className={`rounded-xl p-3 ${step.urgent ? "bg-red-50 ring-1 ring-red-200" : "bg-brand-soft"}`}>
            <div className="flex items-start gap-2"><span className="shrink-0" aria-hidden="true"><CareIcon name={step.icon} /></span><div><h3 className="font-semibold">{careText(step.title, lang)}</h3><p className="mt-1 text-sm text-ink/75">{careText(step.body, lang)}</p></div></div>
          </article>)}
        </div>
        <h3 className="mt-4 font-semibold"><span className="inline-block align-middle"><CareIcon name="test" size={20}/></span> {t("firstVisitChecks", lang)}</h3>
        <ul className="mt-2 grid gap-2 sm:grid-cols-2">{firstVisitTests.map((item) => <li key={item.text.en} className="flex gap-2 rounded-lg bg-ink/5 p-2 text-sm"><span aria-hidden="true"><CareIcon name={item.icon} size={18} /></span><span>{careText(item.text, lang)}</span></li>)}</ul>
      </details>

      {week >= 14 && <MovementTracker journeyId={journey.journey_id} lang={lang} />}

      {action ? <section className="mt-3 rounded-2xl bg-brand p-4 text-white">
        <h2 className="text-sm font-semibold">{t("nextStep", lang)}</h2>
        <a href={`/milestone?id=${action.id}`} className="mt-1 block font-medium underline">{tr(action.title)}</a>
        <p className="mt-1 text-xs opacity-90">{action.overdue ? t("overdue", lang) : action.status === "now" ? t("currentWindow", lang) : action.status === "upcoming" ? t("scheduledNext", lang) : t("next", lang)}</p>
        <a href={`/upload?milestone=${encodeURIComponent(action.id)}`} className="mt-3 inline-block rounded-lg bg-white px-3 py-2 text-sm font-semibold text-brand">{t("uploadReport", lang)}</a>
      </section> : null}

      <section className="mt-3 rounded-2xl bg-white p-4 shadow-sm">
        <h2 className="font-semibold">{t("attentionNeeded", lang)}{attention.length ? ` · ${attention.length}` : ""}</h2>
        {attention.length ? <ul className="mt-2 space-y-1 text-sm text-amber-900">{attention.map((item, i) => <li key={i}>⚠ {item}</li>)}</ul> :
          <p className="mt-2 text-sm text-ink/60">{t("noOpenItems", lang)}</p>}
      </section>

      {journey.next_appointment ? (
        <div className="mt-3 rounded-2xl bg-white p-4 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-ink/60">{t("nextAppointment", lang)}</p>
          <p className="mt-1 font-semibold">{tr(journey.next_appointment.title)}</p>
          <p className="text-sm text-ink/60">📅 {journey.next_appointment.scheduled_date}</p>
        </div>
      ) : null}

      {nextUp?.milestone ? (
        <div className="mt-3 rounded-2xl bg-white p-4 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-ink/50">{tr("Get ready")}: {tr(nextUp.milestone.title)}</p>
          {nextUp.purpose ? <p className="mt-2 text-sm text-ink/80">{tr(nextUp.purpose)}</p> : null}
          {nextUp.what_to_bring ? <p className="mt-2 text-sm">🎒 <span className="text-ink/80">{tr(nextUp.what_to_bring)}</span></p> : null}
          {nextUp.fasting ? <p className="mt-2 text-sm">🍽️ <span className="font-medium text-amber-800">{tr(nextUp.fasting)}</span></p> : null}
          {nextUp.questions && nextUp.questions.length > 0 ? (
            <details className="mt-2 text-sm">
              <summary className="cursor-pointer text-brand">{tr("Questions worth asking")}</summary>
              <ul className="mt-1 list-disc pl-5 text-ink/70">
                {nextUp.questions.map((q) => <li key={q}>{tr(q)}</li>)}
              </ul>
            </details>
          ) : null}
        </div>
      ) : null}

      {unresolved.length > 0 ? (
        <a href="/doctor" className="mt-3 block rounded-2xl border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          ⚠️ {unresolved.length} {t("flagsQueue", lang)}
        </a>
      ) : null}

      <details className="mt-6"><summary className="cursor-pointer font-semibold text-brand">{t("journeyTitle", lang)} · {t("done", lang)} / {t("now", lang)} / {t("next", lang)}</summary><div className="mt-3 space-y-6">
        {ZONE_ORDER.map((zone) =>
          zones[zone].length === 0 ? null : (
            <section key={zone}>
              <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-ink/50">
                {t(zone, lang)} · {zones[zone].length}
              </h2>
              <div className={`space-y-2 ${zone === "done" ? "opacity-70" : ""}`}>
                {zones[zone].map((m) => <MilestoneCard key={m.id} m={m} lang={lang} tr={tr} />)}
              </div>
            </section>
          )
        )}
      </div></details>

      <section className="mt-8 rounded-2xl border border-red-200 bg-red-50 p-4">
        <h2 className="text-sm font-semibold text-red-800">{t("dangerSigns", lang)}:</h2>
        <ul className="mt-1 list-inside list-disc text-sm text-red-700">
          {journey.danger_signs.map((s) => <li key={s}>{tr(s)}</li>)}
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
