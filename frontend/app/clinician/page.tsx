"use client";
import { Suspense, useEffect, useState } from "react";
import { useSearchParams } from "next/navigation";
import { api } from "@/lib/api";
import type { Flag } from "@/lib/types";

const STORAGE_KEY = "caretrail_clinician_token";
function ClinicianView() {
  const params = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [token, setToken] = useState("");
  const [journeyId, setJourneyId] = useState(params.get("journey") || "");
  const [patient, setPatient] = useState("");
  const [flags, setFlags] = useState<Flag[]>([]);
  const [message, setMessage] = useState("");
  useEffect(() => { setToken(sessionStorage.getItem(STORAGE_KEY) || ""); }, []);
  const h = { Authorization: `Bearer ${token}` };
  async function login(e: React.FormEvent) {
    e.preventDefault(); setMessage("");
    try {
      const result = await api<{token: string; name: string}>("/clinician/login", {method: "POST", body: JSON.stringify({email, password})}, false);
      sessionStorage.setItem(STORAGE_KEY, result.token); setToken(result.token); setPassword("");
      setMessage(`Signed in as ${result.name}. Enter the patient-authorized journey ID to review.`);
    } catch (err) { setMessage(err instanceof Error ? err.message : "Login failed"); }
  }
  async function load() {
    try {
      const result = await api<{patient: string; flags: Flag[]}>(`/clinician/journeys/${encodeURIComponent(journeyId)}/review`, {headers: h});
      setPatient(result.patient); setFlags(result.flags); setMessage("");
    } catch (err) { setPatient(""); setFlags([]); setMessage(err instanceof Error ? err.message : "Review unavailable"); }
  }
  async function signOff(flag: Flag) {
    if (!confirm(`Sign off ${flag.label}: ${flag.value} ${flag.unit} for ${patient}?`)) return;
    try {
      await api("/clinician/signoffs", {method: "POST", headers: h, body: JSON.stringify({journey_id: journeyId, observation_id: flag.observation_id, note: "Reviewed."})});
      await load();
    } catch (err) { setMessage(err instanceof Error ? err.message : "Sign-off failed"); }
  }
  return <main className="pt-8">
    <h1 className="text-xl font-bold">Clinician review</h1>
    <p className="mt-2 rounded-xl bg-amber-50 p-3 text-sm">Prototype review. Only verified clinician accounts with patient-granted access can sign off. Not a diagnosis.</p>
    {!token ? <form className="mt-4 space-y-2" onSubmit={login}>
      <input className="w-full rounded-xl border p-3" type="email" aria-label="Clinician email" placeholder="Clinician email" value={email} onChange={e=>setEmail(e.target.value)} required />
      <input className="w-full rounded-xl border p-3" type="password" aria-label="Password" placeholder="Password" value={password} onChange={e=>setPassword(e.target.value)} required />
      <button className="rounded-xl bg-brand px-4 py-2 text-white">Sign in</button>
    </form> : <div className="mt-4 space-y-2">
      <button className="text-brand underline" onClick={()=>{sessionStorage.removeItem(STORAGE_KEY); setToken(""); setPatient(""); setFlags([]);}}>Sign out</button>
      <input className="w-full rounded-xl border p-3" aria-label="Patient-authorized journey ID" placeholder="Patient-authorized journey ID" value={journeyId} onChange={e=>setJourneyId(e.target.value)} />
      <button className="rounded-xl bg-brand px-4 py-2 text-white" onClick={load} disabled={!journeyId}>Load review</button>
      {patient && <h2 className="font-semibold">{patient}: configured-threshold items for review</h2>}
      {patient && !flags.length && <p>No configured-threshold items requiring review.</p>}
      {flags.map(f=><article className="rounded-xl border bg-white p-3" key={f.observation_id}>
        <p>{f.label}: {f.value} {f.unit} ({f.observed_on})</p><p className="text-sm">{f.message}</p>
        {f.signed_off ? <p>{f.signed_off.verified_clinician ? "Reviewed by verified clinician " : "Historical prototype review by "}{f.signed_off.doctor_name}</p> : <button className="mt-2 rounded-lg bg-brand px-3 py-2 text-white" onClick={()=>signOff(f)}>Sign off</button>}
      </article>)}
    </div>}
    {message && <p className="mt-3 text-sm" role="status">{message}</p>}
  </main>;
}
export default function ClinicianPage() { return <Suspense fallback={<main>Loading...</main>}><ClinicianView /></Suspense>; }
