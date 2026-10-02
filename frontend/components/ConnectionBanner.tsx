"use client";
import { useEffect, useState } from "react";
import { useLang, useTr } from "@/lib/i18n";

export default function ConnectionBanner() {
  const [online, setOnline] = useState(true);
  const [lang] = useLang();
  const tr = useTr(lang);
  useEffect(() => {
    setOnline(navigator.onLine);
    const up = () => setOnline(true), down = () => setOnline(false);
    window.addEventListener("online", up); window.addEventListener("offline", down);
    return () => { window.removeEventListener("online", up); window.removeEventListener("offline", down); };
  }, []);
  if (online) return null;
  return <div role="status" aria-live="polite" className="sticky top-0 z-50 border-b border-amber-300 bg-amber-50 px-4 py-2 text-center text-xs text-amber-950">
    {tr("You are offline. Showing your last saved timeline. New uploads, reminders and doctor notes need a connection, and saved information is not a current safety check.")}
  </div>;
}
