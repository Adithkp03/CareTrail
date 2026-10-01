"use client";
import { useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";

type Reminder = { id: string; kind: string; title: string; message: string; scheduled_date: string | null; link: string; read: boolean; dismissed: boolean };
type Inbox = { items: Reminder[]; unread: number };

export default function ReminderInbox({ journeyId, tr }: { journeyId: string; tr: (text: string) => string }) {
  const [inbox, setInbox] = useState<Inbox>({ items: [], unread: 0 });
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState("");
  const [supported, setSupported] = useState(false);
  const [permission, setPermission] = useState<NotificationPermission>("default");
  const notified = useRef(new Set<string>());
  const active = useRef(true);
  const [enabled, setEnabled] = useState(false);

  useEffect(() => {
    active.current = true;
    setSupported("Notification" in window);
    if ("Notification" in window) setPermission(Notification.permission);
    setEnabled(sessionStorage.getItem("ct:notify") === "on");
    return () => { active.current = false; };
  }, []);

  useEffect(() => {
    let cancelled = false;
    async function refresh() {
      if (document.visibilityState !== "visible") return;
      try {
        const result = await api<Inbox>(`/journey/${journeyId}/reminders`, { cache: "no-store" });
        if (cancelled) return;
        setInbox(result); setLoaded(true); setError("");
        const unseen = result.items.filter(i => !i.read && !i.dismissed && !notified.current.has(i.id));
        if (enabled && permission === "granted" && unseen.length) {
          // Generic text only: never patient names, result values or visit titles on lock screens.
          try {
            const options = { body: "Open CareTrail to review your care reminders.", tag: "caretrail-reminders" };
            const registration = "serviceWorker" in navigator ? await navigator.serviceWorker.getRegistration() : undefined;
            if (registration) await registration.showNotification("CareTrail reminder", options);
            else new Notification("CareTrail reminder", options);
            unseen.forEach(i => notified.current.add(i.id));
          } catch { setError("System notifications are unavailable here. Your in-app reminders still work."); }
        }
      } catch { if (!cancelled) setError("Unable to refresh reminders. Connect to the internet and try again; this is not a current safety check."); }
    }
    void refresh();
    // This is a visible-app refresh, not background push, clinical monitoring, or emergency detection.
    const timer = window.setInterval(() => void refresh(), 60000);
    document.addEventListener("visibilitychange", refresh);
    return () => { cancelled = true; clearInterval(timer); document.removeEventListener("visibilitychange", refresh); };
  }, [journeyId, enabled, permission]);

  async function enable() {
    try {
      const p = await Notification.requestPermission(); setPermission(p);
      setEnabled(p === "granted");
      sessionStorage.setItem("ct:notify", p === "granted" ? "on" : "off");
    } catch { setError("This device does not support browser notifications. Use the in-app inbox."); }
  }
  async function update(item: Reminder, dismissed: boolean) {
    try {
      await api(`/journey/${journeyId}/reminders`, { method: "PATCH", body: JSON.stringify({ reminder_id: item.id, read: true, dismissed }) });
      const result = await api<Inbox>(`/journey/${journeyId}/reminders`, { cache: "no-store" });
      if (active.current) { setInbox(result); setLoaded(true); setError(""); }
    } catch { setError("Could not save your change. Refresh and try again."); }
  }
  return <section id="care-reminders" className="ct-card mt-5 scroll-mt-6 rounded-3xl bg-white p-5" aria-label={tr("Care reminders")}>
    <h2 className="font-semibold">{tr("Care reminders")} {inbox.unread > 0 ? `· ${inbox.unread}` : ""}</h2>
    <p className="mt-1 text-xs text-ink/60">{tr("Updated while the app is open. Not emergency monitoring. For warning signs, contact your doctor immediately.")}</p>
    {supported && permission !== "denied" && <button className="mt-3 rounded-full border border-brand/25 bg-brand-soft px-4 py-2 text-xs font-medium text-brand" onClick={enabled ? () => { setEnabled(false); sessionStorage.setItem("ct:notify", "off"); } : enable}>{tr(enabled ? "Turn off browser notifications" : "Enable browser notifications")}</button>}
    {supported && permission === "denied" && <p className="mt-2 text-xs">{tr("Browser notifications are blocked. The in-app inbox still works.")}</p>}
    {!supported && <p className="mt-2 text-xs">{tr("Use the in-app inbox on this device. Browser notifications are not supported.")}</p>}
    {error && <p role="status" className="mt-2 text-sm text-red-700">{tr(error)}</p>}
    {!loaded && !error && <p className="mt-2 text-sm text-ink/60">{tr("Loading reminders...")}</p>}
    {loaded && !error && !inbox.items.some(i => !i.dismissed) && <p className="mt-2 text-sm text-ink/60">{tr("No active care reminders.")}</p>}
    <ul className="mt-3 space-y-3">{inbox.items.filter(i => !i.dismissed).map(item => <li key={item.id} className={`rounded-2xl border border-ink/5 p-4 ${item.read ? "bg-white" : "bg-[#f4efe5]"}`}>
      <a href={item.link} className="font-medium text-brand underline">{tr(item.title)}</a>
      {item.scheduled_date && <p className="mt-1 text-sm">{item.scheduled_date}</p>}
      <p className="mt-1 text-sm">{tr(item.message)}</p>
      <div className="mt-2 flex flex-wrap gap-3 text-sm">
        {!item.read && <button className="text-brand underline" onClick={() => void update(item, false)}>{tr("Mark read")}</button>}
        <button className="text-ink/70 underline" onClick={() => void update(item, true)}>{tr("Dismiss")}</button>
      </div>
    </li>)}</ul>
    <p className="mt-2 text-xs text-ink/70">{tr("Dismissing a reminder does not complete a visit or clear a result. No SMS, email or closed-app push notifications.")}</p>
  </section>;
}
