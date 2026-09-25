"use client";
import { useEffect, useState } from "react";
import CareIcon from "@/components/CareIcon";
import { t, type Lang } from "@/lib/i18n";

export default function MovementTracker({ journeyId, lang }: { journeyId: string; lang: Lang }) {
  const [count, setCount] = useState(0);
  const [day, setDay] = useState("");
  useEffect(() => {
    const today = new Date().toLocaleDateString("en-CA");
    setDay(today);
    try { setCount(Number(localStorage.getItem(`ct:movements:${journeyId}:${today}`) || 0)); } catch { /* unavailable */ }
  }, [journeyId]);
  function update(delta: number) {
    if (!day) return;
    const today = new Date().toLocaleDateString("en-CA");
    if (day !== today) { setDay(today); setCount(0); try { localStorage.setItem(`ct:movements:${journeyId}:${today}`, String(Math.max(0, delta))); } catch { /* unavailable */ } setCount(Math.max(0, delta)); return; }
    const next = Math.max(0, count + delta);
    setCount(next);
    try { localStorage.setItem(`ct:movements:${journeyId}:${day}`, String(next)); } catch { /* unavailable */ }
  }
  return <section className="mt-3 rounded-2xl bg-white p-4 shadow-sm" aria-label={t("movementTracker", lang)}>
    <h2 className="font-semibold"><span className="inline-block align-middle"><CareIcon name="movement" size={20}/></span> {t("movementTracker", lang)}</h2>
    <p className="mt-2 text-sm text-ink/70">{t("movementInstruction", lang)}</p>
    <div className="mt-3 flex items-center gap-3">
      <button type="button" onClick={() => update(1)} className="rounded-xl bg-brand px-5 py-2 text-lg font-bold text-white" aria-label={`+ ${t("movementCount", lang)}`}>+ 1</button>
      <span className="font-semibold" aria-live="polite">{count} · {t("movementCount", lang)}</span>
    </div>
    <button type="button" onClick={() => update(-1)} disabled={!count} className="mt-2 text-xs text-brand underline disabled:opacity-40">{t("movementUndo", lang)}</button>
    <p className="mt-3 rounded-xl bg-amber-50 p-3 text-sm text-amber-900">⚠️ {t("movementWarning", lang)}</p>
    <p className="mt-2 text-xs text-ink/50">{t("movementStorage", lang)}</p>
  </section>;
}
