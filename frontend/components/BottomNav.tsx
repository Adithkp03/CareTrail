"use client";
import { useEffect, useState } from "react";
import { usePathname } from "next/navigation";
import CareIcon from "./CareIcon";
import { useLang, useTr } from "@/lib/i18n";
export default function BottomNav() {
  const rawPath=usePathname(); const path=rawPath.replace(/\.html$/, "").replace(/\/$/, "") || "/"; const [mounted,setMounted]=useState(false); useEffect(()=>setMounted(true),[]); const [lang]=useLang(); const tr=useTr(lang);
  if (!mounted) return null;
  if (["/login","/signup","/clinician","/clinician/setup"].includes(path)) return null;
  const tabs=[{href:"/",icon:"home",label:"Home"},{href:"/timeline",icon:"review",label:"Timeline"},{href:"/reminders",icon:"bell",label:"Reminders"},{href:"/movements",icon:"movement",label:"Movements"}];
  return <nav className="ct-bottom-nav fixed bottom-0 left-1/2 z-40 grid w-full max-w-md -translate-x-1/2 grid-cols-4 gap-1 border-t border-ink/5 bg-white/95 px-3 pt-2 text-center" aria-label={tr("Main navigation")}>
    {tabs.map(item=><a key={item.href} href={item.href} aria-current={path===item.href?"page":undefined} className={`flex flex-col items-center justify-center gap-1 rounded-2xl px-1 py-2 text-[10px] font-medium leading-tight ${path===item.href?"bg-navy text-white":"text-ink/75"}`}><CareIcon name={item.icon} size={19}/><span>{tr(item.label)}</span></a>)}
  </nav>;
}
