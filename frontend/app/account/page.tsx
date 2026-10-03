"use client";
import { useState } from "react";
import { api, getToken, setToken } from "@/lib/api";
import { useLang, useTr } from "@/lib/i18n";

const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8000";

export default function AccountPage() {
  const [lang] = useLang();
  const tr = useTr(lang);
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);

  async function download() {
    setError(""); setNote(""); setBusy(true);
    try {
      const res = await fetch(`${API}/me/export`, { headers: { Authorization: `Bearer ${getToken() ?? ""}` } });
      if (!res.ok) throw new Error();
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement("a");
      a.href = url; a.download = "caretrail-my-data.json"; document.body.appendChild(a); a.click(); a.remove();
      URL.revokeObjectURL(url);
      setNote(tr("Your data was downloaded."));
    } catch { setError(tr("Could not download your data. Connect and try again.")); }
    finally { setBusy(false); }
  }

  async function remove(e: React.FormEvent) {
    e.preventDefault();
    if (confirm !== "DELETE") { setError(tr("Type DELETE to confirm.")); return; }
    setError(""); setBusy(true);
    try {
      await api("/me", { method: "DELETE", body: JSON.stringify({ confirm }) });
      setToken(null);
      window.location.href = "/login";
    } catch (err) { setError(err instanceof Error ? err.message : tr("Could not delete the account. Try again.")); setBusy(false); }
  }

  return (
    <main className="pt-6 pb-24">
      <a href="/" className="text-sm text-brand">← CareTrail</a>
      <h1 className="mt-2 text-xl font-bold">{tr("Your data")}</h1>
      <section className="mt-4 rounded-3xl bg-white p-4">
        <h2 className="font-semibold">{tr("Download your data")}</h2>
        <p className="mt-1 text-sm text-ink/75">{tr("A file with your dates, results, report list and doctor notes. Original report files are not included.")}</p>
        <button onClick={() => void download()} disabled={busy} className="mt-3 w-full rounded-xl bg-brand p-3 font-semibold text-white disabled:opacity-50">{tr("Download my data")}</button>
        {note && <p role="status" className="mt-2 text-sm text-brand">{note}</p>}
      </section>
      <form onSubmit={remove} className="mt-4 rounded-3xl border border-amber-300 bg-amber-50 p-4">
        <h2 className="font-semibold">{tr("Delete my account")}</h2>
        <p className="mt-1 text-sm text-amber-950">{tr("This permanently removes your journeys, results, reports and doctor notes from CareTrail. It cannot be undone. Download your data first if you want a copy.")}</p>
        <label className="mt-3 block text-sm font-medium">{tr("Type DELETE to confirm")}
          <input value={confirm} onChange={(e) => setConfirm(e.target.value)} autoCapitalize="characters" className="mt-1 w-full rounded-xl border border-ink/15 bg-white p-3 text-sm" />
        </label>
        <button disabled={busy || confirm !== "DELETE"} className="mt-3 w-full rounded-xl bg-red-800 p-3 font-semibold text-white disabled:opacity-40">{tr("Delete my account")}</button>
      </form>
      {error && <p role="alert" className="mt-3 text-sm text-amber-950">{error}</p>}
    </main>
  );
}
