"use client";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { api, getToken } from "@/lib/api";
import { t, type Lang } from "@/lib/i18n";
import type { Flag, Journey } from "@/lib/types";

// Hackathon shell of the doctor view: the flags queue plus the sign-off that
// lands on the mother's timeline. A real doctor login lands in the hardening phase.
export default function DoctorPage() {
  const router = useRouter();
  const [journey, setJourney] = useState<Journey | null>(null);
  const [doctorName, setDoctorName] = useState("");
  const [message, setMessage] = useState("");
  const lang: Lang = "en";

  async function load() {
    const list = await api<{ journeys: { journey_id: string }[] }>("/journeys");
    if (list.journeys.length === 0) return;
    setJourney(await api<Journey>(`/journey/${list.journeys[0].journey_id}`));
  }
  useEffect(() => {
    if (!getToken()) { router.replace("/login"); return; }
    load().catch(() => setMessage("Failed to load"));
  }, [router]);

  async function signOff(flag: Flag) {
    if (!journey || !doctorName.trim()) { setMessage(t("doctorName", lang) + "?"); return; }
    await api("/signoffs", {
      method: "POST",
      headers: { "X-Doctor-Name": doctorName.trim() },
      body: JSON.stringify({ journey_id: journey.journey_id, observation_id: flag.observation_id, note: "Reviewed." }),
    });
    setMessage(`✅ ${flag.label}`);
    await load();
  }

  if (!journey) return <main className="pt-16 text-ink/50">{message || "Loading…"}</main>;

  return (
    <main className="pt-6">
      <a href="/" className="text-sm text-brand">← {t("appName", lang)}</a>
      <h1 className="mt-2 text-xl font-bold">{t("doctorView", lang)}</h1>
      <p className="text-sm text-ink/60">{journey.patient.name} · {journey.gestational_age.weeks} {t("weeks", lang)}</p>

      <input className="mt-4 w-full rounded-xl border border-ink/15 bg-white p-3" placeholder={t("doctorName", lang)}
        value={doctorName} onChange={(e) => setDoctorName(e.target.value)} />
      {message && <p className="mt-2 text-sm text-brand">{message}</p>}

      <h2 className="mt-6 text-sm font-semibold uppercase tracking-wide text-ink/50">{t("flagsQueue", lang)}</h2>
      {journey.flags.length === 0 ? (
        <p className="mt-2 rounded-2xl bg-white p-4 text-sm text-ink/60 shadow-sm">{t("noFlags", lang)}</p>
      ) : (
        <div className="mt-2 space-y-2">
          {journey.flags.map((f) => (
            <div key={f.observation_id} className="rounded-2xl border border-amber-300 bg-amber-50 p-4">
              <p className="font-medium">{f.label}: {f.value} {f.unit}</p>
              <p className="mt-1 text-sm text-amber-900">{f.message}</p>
              <p className="mt-1 text-xs text-ink/50">{f.observed_on}</p>
              <button onClick={() => signOff(f)} className="mt-3 rounded-xl bg-brand px-4 py-2 text-sm font-semibold text-white">
                {t("signOff", lang)}
              </button>
            </div>
          ))}
        </div>
      )}
    </main>
  );
}
