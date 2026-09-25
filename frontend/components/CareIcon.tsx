import type { ReactNode } from "react";

const paths: Record<string, ReactNode> = {
  visit: <><circle cx="12" cy="6" r="3"/><path d="M5 21v-2a7 7 0 0 1 14 0v2M12 11v7m-3.5-3.5h7"/></>,
  test: <><path d="M8 3h8m-7 0v7l-4 8a2 2 0 0 0 2 3h10a2 2 0 0 0 2-3l-4-8V3M8 16h8"/></>,
  scan: <><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4m-5-9 3-3 3 3 4-4"/></>,
  vaccination: <><path d="m5 19 3-3m-1-9 10 10M9 5l10 10M13 3l8 8m-8 8-8-8m-2 2 8 8"/></>,
  review: <><rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V2h6v2M8 10h8m-8 4h8m-8 4h5"/></>,
  pregnancy: <><path d="M9 7a3 3 0 1 0 6 0 3 3 0 0 0-6 0ZM8 21v-4a4 4 0 0 1 8 0v4M12 14a4 4 0 0 1 4 4"/></>,
  movement: <><path d="M3 12h4l2-5 3 10 2-5h7M18 5l1-2m-1 18 1-2"/></>,
  warning: <><path d="M12 3 2 21h20L12 3Zm0 6v5m0 3v1"/></>,
  hospital: <><rect x="4" y="7" width="16" height="14" rx="1"/><path d="M10 4h4v7m-6 1h8m-4-4v8m-2 5v-4h4v4"/></>,
  blood: <><path d="M12 2C10 6 5 11 5 15a7 7 0 0 0 14 0c0-4-5-9-7-13Z"/></>,
  food: <><path d="M5 3v8m3-8v8m-3 0h3m-1 0v10m8-18v18m0-18c3 2 4 5 4 8h-4"/></>,
};

export default function CareIcon({ name, size = 24 }: { name: string; size?: number }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" className="shrink-0 text-brand">{paths[name] ?? paths.review}</svg>;
}
