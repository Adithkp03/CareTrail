"use client";
import { useEffect, useState } from "react";

const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8000";

export default function ClinicianSetupPage() {
  const [token, setToken] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [done, setDone] = useState(false);
  useEffect(() => {
    const match = new URLSearchParams(window.location.hash.slice(1)).get("token") || "";
    setToken(match);
    // The secret stays in page memory, not browser history or server-side URL logs.
    window.history.replaceState(null, "", window.location.pathname);
  }, []);
  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (password !== confirm) { setMessage("Passwords do not match."); return; }
    if (password.length < 12 || password.length > 128) { setMessage("Use 12 to 128 characters."); return; }
    setBusy(true); setMessage("");
    try {
      const res = await fetch(`${API}/clinician/setup-password`, {
        method: "POST", headers: {"Content-Type": "application/json"},
        body: JSON.stringify({token, password}),
      });
      if (!res.ok) throw new Error("Link is invalid, already used or expired. Ask the team for a new link.");
      setPassword(""); setConfirm(""); setToken(""); setDone(true);
    } catch (err) { setMessage(err instanceof Error ? err.message : "Could not set password."); }
    finally { setBusy(false); }
  }
  return <main className="pt-8">
    <h1 className="text-xl font-bold">Set your CareTrail clinician password</h1>
    <p className="mt-2 text-sm text-ink/70">This link can be used once and expires after 24 hours. Don't forward it. Setting a password does not give access to patient journeys: a patient must grant access separately.</p>
    {!token && !done && <p className="mt-4 text-red-700">No setup link found. Ask the team for a new one.</p>}
    {token && !done && <form onSubmit={submit} className="mt-5 space-y-3">
      <label className="block text-sm">Password (12 to 128 characters)<input type="password" autoComplete="new-password" minLength={12} maxLength={128} required value={password} onChange={e=>setPassword(e.target.value)} className="mt-1 w-full rounded-xl border bg-white p-3" /></label>
      <label className="block text-sm">Confirm password<input type="password" autoComplete="new-password" minLength={12} maxLength={128} required value={confirm} onChange={e=>setConfirm(e.target.value)} className="mt-1 w-full rounded-xl border bg-white p-3" /></label>
      <button disabled={busy} className="rounded-xl bg-brand px-4 py-3 font-semibold text-white disabled:opacity-50">Set password</button>
    </form>}
    {done && <p className="mt-4">Password set. <a href="/clinician" className="text-brand underline">Sign in as a clinician</a>.</p>}
    {message && <p role="alert" className="mt-4 text-red-700">{message}</p>}
  </main>;
}
