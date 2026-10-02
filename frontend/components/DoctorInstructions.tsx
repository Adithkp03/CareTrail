"use client";
import { useEffect, useState } from "react";
import { api } from "@/lib/api";
type Instruction = {id: string; note: string; doctor_name: string; created_at: string; acknowledged_at: string | null};
export default function DoctorInstructions({journeyId, tr}: {journeyId: string; tr: (s: string) => string}) {
  const [items, setItems] = useState<Instruction[]>([]);
  const [error, setError] = useState("");
  const [loaded, setLoaded] = useState(false);
  const [saving, setSaving] = useState("");
  useEffect(() => { let active = true; api<{items: Instruction[]}>(`/journey/${journeyId}/instructions`, {cache: "no-store"}).then(r=>{if(active){setItems(r.items);setLoaded(true);}}).catch(()=>{if(active)setError("Doctor instructions could not be loaded. Connect and try again.");});return()=>{active=false;}; }, [journeyId]);
  async function acknowledge(id: string) {
    setSaving(id);setError("");
    try {await api(`/instructions/${id}/acknowledge`, {method:"POST"});const r=await api<{items: Instruction[]}>(`/journey/${journeyId}/instructions`, {cache:"no-store"});setItems(r.items);}
    catch {setError("Could not save your acknowledgment. Try again.");}finally{setSaving("");}
  }
  return <section className="ct-card mt-4 rounded-3xl bg-white p-4" aria-label={tr("Instructions from your doctor")}>
    <h2 className="font-semibold">{tr("Instructions from your doctor")}{items.some(i=>!i.acknowledged_at)?` · ${items.filter(i=>!i.acknowledged_at).length} ${tr("unread")}`:""}</h2>
    <p className="mt-1 text-xs text-ink/70">{tr("These notes were written by a doctor with access to your journey, not by AI. Acknowledging means you have read the note, not that the care step is complete.")}</p>
    {!loaded&&!error&&<p className="mt-2 text-sm">{tr("Loading doctor instructions...")}</p>}
    {loaded&&!items.length&&<p className="mt-2 text-sm text-ink/70">{tr("No doctor instructions recorded yet.")}</p>}
    {error&&<p role="status" className="mt-2 text-sm text-amber-950">{tr(error)}</p>}
    <div className="mt-3 space-y-3">{items.map(i=><article key={i.id} className={`rounded-2xl border p-3 ${i.acknowledged_at?"border-slate-200 bg-slate-50":"border-amber-300 bg-amber-50"}`}>
      <p className="text-sm font-semibold">{i.doctor_name}</p><p className="text-xs text-ink/65">{new Date(i.created_at).toLocaleString()}</p>
      <p className="mt-2 whitespace-pre-wrap break-words text-sm">{i.note}</p>
      {i.acknowledged_at?<p className="mt-3 text-xs text-brand">{tr("You acknowledged this note")} · {new Date(i.acknowledged_at).toLocaleString()}</p>:<button onClick={()=>void acknowledge(i.id)} disabled={!!saving} className="mt-3 rounded-xl bg-brand px-4 py-2 text-sm font-semibold text-white disabled:opacity-50">{tr(saving===i.id?"Saving...":"I have read this note")}</button>}
    </article>)}</div>
  </section>;
}
