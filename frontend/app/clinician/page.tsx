"use client";
import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import type { Flag } from "@/lib/types";

const STORAGE_KEY = "caretrail_clinician_token";
const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8000";
async function clinicianApi<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API}${path}`, {
    ...options,
    headers: {"Content-Type": "application/json", ...(options.headers as Record<string, string> || {})},
  });
  if (!res.ok) {
    const body = await res.json().catch(() => ({}));
    throw new Error(body.detail || `Request failed (${res.status})`);
  }
  return res.json() as Promise<T>;
}
function ClinicianView() {
  const params = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState("");
  const [journeyId, setJourneyId] = useState(params.get("journey") || "");
  const [patient, setPatient] = useState("");
  const [flags, setFlags] = useState<Flag[]>([]);
  const [instructions, setInstructions] = useState<{id: string; note: string; doctor_name: string; acknowledged_at: string | null}[]>([]);
  const [instruction, setInstruction] = useState("");
  const [posting, setPosting] = useState(false);
  const [loadedJourney, setLoadedJourney] = useState("");
  const [tftNote, setTftNote] = useState("");
  const [message, setMessage] = useState("");
  type Roster = {journey_id: string; patient: string; weeks: number; plus_days: number; edd: string | null; pending_reviews: number; instructions_waiting: number};
  const [roster, setRoster] = useState<Roster[] | null>(null);
  useEffect(() => { setToken(sessionStorage.getItem(STORAGE_KEY) || ""); }, []);
  const h = { Authorization: `Bearer ${token}` };
  async function loadRoster(tok = token) {
    try { setRoster((await clinicianApi<{items: Roster[]}>("/clinician/patients", {headers: {Authorization: `Bearer ${tok}`}})).items); }
    catch { setRoster(null); setMessage("Patient list unavailable. Connect and try again."); }
  }
  useEffect(() => { if (token) void loadRoster(token); }, [token]); // eslint-disable-line react-hooks/exhaustive-deps
  async function open(id: string) {
    setJourneyId(id); setPatient(""); setFlags([]); setInstructions([]); setLoadedJourney(""); setInstruction("");
    try {
      const result = await clinicianApi<{patient: string; flags: Flag[]; tft_clinician_note: string}>(`/clinician/journeys/${encodeURIComponent(id)}/review`, {headers: h});
      setPatient(result.patient); setFlags(result.flags); setTftNote(result.tft_clinician_note); setMessage(""); setLoadedJourney(id);
      const notes = await clinicianApi<{items: typeof instructions}>(`/clinician/journeys/${encodeURIComponent(id)}/instructions`, {headers:h}); setInstructions(notes.items);
    } catch (err) { setMessage(err instanceof Error ? err.message : "Review unavailable"); }
  }
  async function login(e: React.FormEvent) {
    e.preventDefault(); setMessage("");
    try {
      const result = await clinicianApi<{token: string; name: string}>("/clinician/login", {method: "POST", body: JSON.stringify({email, password})});
      sessionStorage.setItem(STORAGE_KEY, result.token); setToken(result.token); setPassword("");
      setMessage(`Signed in as ${result.name}. Choose a patient to review.`);
    } catch (err) { setMessage(err instanceof Error ? err.message : "Login failed"); }
  }
  async function load() {
    try {
      const result = await clinicianApi<{patient: string; flags: Flag[]; tft_clinician_note: string}>(`/clinician/journeys/${encodeURIComponent(journeyId)}/review`, {headers: h});
      setPatient(result.patient); setFlags(result.flags); setTftNote(result.tft_clinician_note); setMessage(""); setLoadedJourney(journeyId);
      const notes = await clinicianApi<{items: typeof instructions}>(`/clinician/journeys/${encodeURIComponent(journeyId)}/instructions`, {headers:h});setInstructions(notes.items);
    } catch (err) { setPatient(""); setFlags([]); setTftNote(""); setInstructions([]);setLoadedJourney(""); setMessage(err instanceof Error ? err.message : "Review unavailable"); }
  }
  async function sendInstruction() {
    if(!instruction.trim() || !loadedJourney || journeyId !== loadedJourney) return;
    if(!confirm(`Send this instruction to ${patient}?\n\n${instruction.trim()}`)) return;
    setPosting(true);
    try {await clinicianApi(`/clinician/journeys/${encodeURIComponent(loadedJourney)}/instructions`, {method:"POST",headers:h,body:JSON.stringify({note:instruction.trim()})});setInstruction("");await load();setMessage("Instruction sent to the patient.");}
    catch(err){setMessage(err instanceof Error?err.message:"Could not send instruction");}finally{setPosting(false);}
  }
  async function signOff(flag: Flag) {
    if (!confirm(`Sign off ${flag.label}: ${flag.value} ${flag.unit} for ${patient}?`)) return;
    try {
      await clinicianApi("/clinician/signoffs", {method: "POST", headers: h, body: JSON.stringify({journey_id: journeyId, observation_id: flag.observation_id, note: "Reviewed."})});
      await load(); void loadRoster();
    } catch (err) { setMessage(err instanceof Error ? err.message : "Sign-off failed"); }
  }
  return <main className="pt-7">
    <a className="mb-6 inline-flex items-center gap-2 text-sm text-brand" href="/">← CareTrail</a>
    <div className="ct-hero rounded-3xl p-5 text-white">
    <h1 className="text-2xl font-semibold">Clinician review</h1>
    <p className="mt-3 text-sm leading-relaxed text-white/80">Prototype review. Only verified clinician accounts with patient-granted access can sign off. Not a diagnosis.</p></div>
    {!token ? <form className="ct-card mt-5 space-y-3 rounded-3xl bg-white p-5" onSubmit={login}>
      <input className="w-full rounded-xl border p-3" type="email" aria-label="Clinician email" placeholder="Clinician email" value={email} onChange={e=>setEmail(e.target.value)} required />
      <input className="w-full rounded-xl border p-3" type="password" aria-label="Password" placeholder="Password" value={password} onChange={e=>setPassword(e.target.value)} required />
      <button className="rounded-xl bg-brand px-4 py-2 text-white">Sign in</button>
    </form> : <div className="ct-card mt-5 space-y-3 rounded-3xl bg-white p-5">
      <button className="text-brand underline" onClick={()=>{sessionStorage.removeItem(STORAGE_KEY); setToken(""); setPatient(""); setFlags([]); setTftNote("");setInstructions([]);setLoadedJourney("");setInstruction("");}}>Sign out</button>
      <section aria-label="Your patients">
        <h2 className="font-semibold">Your patients{roster ? ` (${roster.length})` : ""}</h2>
        {roster && !roster.length && <p className="mt-1 text-sm text-ink/70">No patient has shared a journey with you yet. A patient grants access from the Doctor view in their app.</p>}
        <div className="mt-2 space-y-2">{roster?.map(r=><button key={r.journey_id} onClick={()=>void open(r.journey_id)} className={`w-full rounded-2xl border p-3 text-left ${loadedJourney===r.journey_id?"border-brand bg-brand-soft":"border-ink/10 bg-white"}`}>
          <span className="block font-semibold">{r.patient}</span>
          <span className="block text-xs text-ink/70">{r.weeks}w+{r.plus_days}d · Due {r.edd ?? "unknown"}</span>
          <span className="mt-1 flex flex-wrap gap-2 text-xs">
            <span className={`rounded-full px-2 py-0.5 ${r.pending_reviews?"bg-amber-100 text-amber-950":"bg-slate-100"}`}>{r.pending_reviews} to review</span>
            <span className="rounded-full bg-slate-100 px-2 py-0.5">Unread notes: {r.instructions_waiting}</span>
          </span>
        </button>)}</div>
      </section>
      <input className="w-full rounded-xl border p-3" aria-label="Patient-authorized journey ID" placeholder="Patient-authorized journey ID" value={journeyId} onChange={e=>{setJourneyId(e.target.value);setPatient("");setFlags([]);setInstructions([]);setLoadedJourney("");setInstruction("");}} />
      <button className="rounded-xl bg-brand px-4 py-2 text-white" onClick={load} disabled={!journeyId}>Load review</button>
      {patient && <h2 className="font-semibold">{patient}: configured-threshold items for review</h2>}
      {patient && tftNote && <p className="rounded-2xl bg-[#f4efe5] p-4 text-sm leading-relaxed">{tftNote}</p>}
      {patient && !flags.length && <p>No configured-threshold items requiring review.</p>}
      {patient && loadedJourney===journeyId && <section className="rounded-2xl border border-brand/20 bg-brand-soft p-4">
        <h2 className="font-semibold">Instructions for {patient}</h2>
        <p className="mt-1 text-xs text-ink/70">The patient sees your exact note and can acknowledge reading it. This does not mark care complete.</p>
        <label className="mt-3 block text-sm" htmlFor="doctor-instruction">Write a patient instruction</label>
        <textarea id="doctor-instruction" className="mt-1 min-h-28 w-full rounded-xl border bg-white p-3 text-sm" maxLength={4000} value={instruction} onChange={e=>setInstruction(e.target.value)} />
        <button disabled={posting||!instruction.trim()} onClick={()=>void sendInstruction()} className="mt-2 rounded-xl bg-brand px-4 py-2 text-sm text-white disabled:opacity-50">{posting?"Sending...":"Review and send instruction"}</button>
        <div className="mt-4 space-y-3">{instructions.map(i=><article key={i.id} className="rounded-xl bg-white p-3 text-sm"><p className="whitespace-pre-wrap break-words">{i.note}</p><p className="mt-2 text-xs text-ink/70">{i.acknowledged_at?`Patient acknowledged: ${new Date(i.acknowledged_at).toLocaleString()}`:"Waiting for patient acknowledgment"}</p></article>)}</div>
      </section>}
      {flags.map(f=><article className="rounded-2xl border border-ink/10 bg-[#f5f7f9] p-4" key={f.observation_id}>
        <p>{f.label}: {f.value} {f.unit} ({f.observed_on})</p><p className="text-sm">{f.message}</p>
        {f.signed_off ? <p>{f.signed_off.verified_clinician ? "Reviewed by verified clinician " : "Historical prototype review by "}{f.signed_off.doctor_name}</p> : <button className="mt-2 rounded-lg bg-brand px-3 py-2 text-white" onClick={()=>signOff(f)}>Sign off</button>}
      </article>)}
    </div>}
    {message && <p className="mt-3 text-sm" role="status">{message}</p>}
  </main>;
}
export default function ClinicianPage() { return <Suspense fallback={<main>Loading...</main>}><ClinicianView /></Suspense>; }
