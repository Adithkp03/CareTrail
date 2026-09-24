"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, getToken } from "@/lib/api";
import { t, useLang, useTr, testLabel } from "@/lib/i18n";
import type { Brief, Flag, Journey } from "@/lib/types";

// Hackathon shell of the doctor view: the flags queue plus the sign-off that
// lands on the mother's timeline. A real doctor login lands in the hardening phase.
export default function DoctorPage() {
  const router = useRouter();
  const [journey, setJourney] = useState<Journey | null>(null);
  const [brief, setBrief] = useState<Brief | null>(null);
  const [doctorName, setDoctorName] = useState("");
  const [message, setMessage] = useState("");
  const [lang] = useLang();
  const tr = useTr(lang);

  async function load() {
    const list = await api<{ journeys: { journey_id: string }[] }>("/journeys");
    if (list.journeys.length === 0) return;
    const j = await api<Journey>(`/journey/${list.journeys[0].journey_id}`);
    setJourney(j);
    api<Brief>(`/journey/${j.journey_id}/brief`).then(setBrief).catch(() => {});
  }
  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    load().catch(() => setMessage(tr("Could not load. Please try again.")));
  }, [router]);

  async function signOff(flag: Flag) {
    if (!journey || !doctorName.trim()) { setMessage(t("doctorName", lang) + "?"); return; }
    await api("/signoffs", {
      method: "POST",
      headers: { "X-Doctor-Name": doctorName.trim() },
      body: JSON.stringify({ journey_id: journey.journey_id, observation_id: flag.observation_id, note: "Reviewed." }),
    });
    setMessage(`✅ ${tr(flag.label)}`);
    await load();
  }

  async function resetDemo() {
    if (!confirm(tr("Reset the demo journey? This restores Anjali to the starting state."))) return;
    await api("/demo/seed", { method: "POST" }, false);
    setMessage("✅ " + tr("Demo reset - Anjali is back to the starting state."));
    await load();
  }

  if (!journey) return <main className="pt-16 text-ink/50">{message || t("loading", lang)}</main>;

  return (
    <main className="pt-6">
      <a href="/" className="text-sm text-brand">← {t("appName", lang)}</a>
      <div className="mt-2 flex items-center justify-between">
        <h1 className="text-xl font-bold">{t("doctorView", lang)}</h1>
        <button onClick={resetDemo} className="rounded-lg border border-brand px-3 py-1 text-xs font-semibold text-brand">
          {tr("Reset demo")}
        </button>
      </div>
      <p className="text-sm text-ink/60">{journey.patient.name} · {journey.gestational_age.weeks} {t("weeks", lang)}</p>

      <input className="mt-4 w-full rounded-xl border border-ink/15 bg-white p-3" placeholder={t("doctorName", lang)}
        value={doctorName} onChange={(e) => setDoctorName(e.target.value)} />
      {message && <p className="mt-2 text-sm text-brand">{message}</p>}

      {brief ? (
        <section className="mt-4 rounded-2xl bg-white p-4 shadow-sm">
          <h2 className="text-sm font-semibold uppercase tracking-wide text-ink/50">{tr("Pre-consult brief")}</h2>
          <p className="mt-1 text-sm text-ink/70">
            {brief.gestational_age.weeks}w{brief.gestational_age.plus_days}d · {tr("Due date")} {brief.edd}
            {brief.next_appointment ? ` · ${tr("Next")}: ${tr(brief.next_appointment.title)} (${brief.next_appointment.scheduled_date})` : ""}
          </p>
          {brief.latest_values.length > 0 ? (
            <div className="mt-2 flex flex-wrap gap-2">
              {brief.latest_values.map((v) => (
                <span key={v.code} className="rounded-full bg-ink/5 px-3 py-1 text-xs text-ink/70">
                  {tr(testLabel(v.code))}: {v.value} {v.unit}
                </span>
              ))}
            </div>
          ) : null}
          {brief.recent_reports.length > 0 ? (
            <p className="mt-2 text-xs text-ink/50">{tr("Latest report")}: {brief.recent_reports[0].filename} ({tr(brief.recent_reports[0].status)})</p>
          ) : null}
        </section>
      ) : null}

      <h2 className="mt-6 text-sm font-semibold uppercase tracking-wide text-ink/50">{t("flagsQueue", lang)}</h2>
      {journey.flags.length === 0 ? (
        <p className="mt-2 rounded-2xl bg-white p-4 text-sm text-ink/60 shadow-sm">{t("noFlags", lang)}</p>
      ) : (
        <div className="mt-2 space-y-2">
          {journey.flags.map((f) => (
            <div key={f.observation_id} className="rounded-2xl border border-amber-300 bg-amber-50 p-4">
              <p className="font-medium">{tr(f.label)}: {f.value} {f.unit}</p>
              <p className="mt-1 text-sm text-amber-900">{tr(f.message)}</p>
              <p className="mt-1 text-xs text-ink/50">{f.observed_on}</p>
              {f.signed_off ? (
                <p className="mt-3 text-sm text-green-700">✅ {t("signedOffBy", lang)} {f.signed_off.doctor_name}{f.signed_off.note ? ` - ${tr(f.signed_off.note)}` : ""}</p>
              ) : (
                <button onClick={() => signOff(f)} className="mt-3 rounded-xl bg-brand px-4 py-2 text-sm font-semibold text-white">
                  {t("signOff", lang)}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </main>
  );
}
