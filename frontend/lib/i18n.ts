"use client";
import { useCallback, useEffect, useRef, useState } from "react";

export type Lang = "en" | "ml" | "hi";

export const LANGS: { code: Lang; label: string }[] = [
  { code: "en", label: "English" },
  { code: "ml", label: "മലയാളം" },
  { code: "hi", label: "हिन्दी" },
];

// Static UI labels for the demo. Sarvam Translate takes over full content
// translation (milestone titles, explanations, audio) in phase 4.
const dict: Record<string, Record<Lang, string>> = {
  appName: { en: "CareTrail", ml: "കെയർട്രെയിൽ", hi: "केयरट्रेल" },
  weeks: { en: "weeks", ml: "ആഴ്ച", hi: "सप्ताह" },
  days: { en: "days", ml: "ദിവസം", hi: "दिन" },
  now: { en: "Now", ml: "ഇപ്പോൾ", hi: "अभी" },
  upcoming: { en: "Upcoming", ml: "അടുത്തതായി വരുന്നു", hi: "आगामी" },
  next: { en: "Next", ml: "പിന്നീട്", hi: "आगे" },
  done: { en: "Done", ml: "പൂർത്തിയായി", hi: "पूरा हुआ" },
  overdue: { en: "Overdue", ml: "കാലാവധി കഴിഞ്ഞു", hi: "समय से बाहर" },
  nextAppointment: { en: "Next appointment", ml: "അടുത്ത അപ്പോയിന്റ്മെന്റ്", hi: "अगली अपॉइंटमेंट" },
  signedOffBy: { en: "Reviewed by", ml: "പരിശോധിച്ചത്", hi: "जाँच की" },
  doctorView: { en: "Doctor view", ml: "ഡോക്ടർ വ്യൂ", hi: "डॉक्टर व्यू" },
  uploadReport: { en: "Upload report", ml: "റിപ്പോർട്ട് അപ്‌ലോഡ്", hi: "रिपोर्ट अपलोड" },
  logout: { en: "Log out", ml: "ലോഗ് ഔട്ട്", hi: "लॉग आउट" },
  login: { en: "Log in", ml: "ലോഗിൻ", hi: "लॉग इन" },
  signup: { en: "Sign up", ml: "അക്കൗണ്ട് ഉണ്ടാക്കൂ", hi: "साइन अप" },
  phone: { en: "Phone number", ml: "ഫോൺ നമ്പർ", hi: "फ़ोन नंबर" },
  password: { en: "Password", ml: "പാസ്‌വേഡ്", hi: "पासवर्ड" },
  yourName: { en: "Your name", ml: "നിങ്ങളുടെ പേര്", hi: "आपका नाम" },
  language: { en: "Language", ml: "ഭാഷ", hi: "भाषा" },
  startJourney: { en: "Start my journey", ml: "എന്റെ യാത്ര തുടങ്ങൂ", hi: "मेरी यात्रा शुरू करें" },
  lmpLabel: { en: "First day of your last period", ml: "അവസാന ആർത്തവത്തിന്റെ ആദ്യ ദിവസം", hi: "आखिरी पीरियड का पहला दिन" },
  eddLabel: { en: "Or your expected due date", ml: "അല്ലെങ്കിൽ പ്രതീക്ഷിക്കുന്ന പ്രസവ തീയതി", hi: "या अपेक्षित डिलीवरी तारीख" },
  tryDemo: { en: "Try the demo (Anjali)", ml: "ഡെമോ കാണൂ (അഞ്ജലി)", hi: "डेमो देखें (अंजलि)" },
  markDone: { en: "Mark as done", ml: "പൂർത്തിയാക്കി", hi: "पूरा हुआ" },
  schedule: { en: "Schedule", ml: "ഷെഡ്യൂൾ", hi: "तारीख तय करें" },
  listen: { en: "Listen", ml: "കേൾക്കൂ", hi: "सुनें" },
  ask: { en: "Ask", ml: "ചോദിക്കൂ", hi: "पूछें" },
  comingVoice: { en: "Coming in the voice phase", ml: "വോയ്സ് ഘട്ടത്തിൽ വരും", hi: "वॉइस चरण में आएगा" },
  dangerSigns: { en: "Call your doctor now if you have", ml: "ഇവ ശ്രദ്ധിച്ചാൽ ഉടൻ ഡോക്ടറെ വിളിക്കൂ", hi: "इन लक्षणों पर तुरंत डॉक्टर को बुलाएँ" },
  flagsQueue: { en: "Flags waiting for review", ml: "അവലോകനം കാത്തിരിക്കുന്നവ", hi: "समीक्षा के लिए प्रतीक्षारत" },
  noFlags: { en: "Nothing waiting. All values are inside the doctor-set thresholds.", ml: "ഒന്നും ബാക്കിയില്ല. എല്ലാ മൂല്യങ്ങളും ഡോക്ടർ നിശ്ചയിച്ച പരിധിക്കുള്ളിലാണ്.", hi: "कुछ बाकी नहीं। सभी मान डॉक्टर-निर्धारित सीमा के भीतर हैं।" },
  signOff: { en: "Sign off", ml: "അംഗീകരിക്കൂ", hi: "स्वीकृत करें" },
  doctorName: { en: "Doctor's name", ml: "ഡോക്ടറുടെ പേര്", hi: "डॉक्टर का नाम" },
  prepNotes: { en: "How to prepare", ml: "എങ്ങനെ ഒരുക്കമാകാം", hi: "तैयारी कैसे करें" },
  reports: { en: "Reports", ml: "റിപ്പോർട്ടുകൾ", hi: "रिपोर्ट" },
  results: { en: "Results", ml: "ഫലങ്ങൾ", hi: "परिणाम" },
  window: { en: "Usual window", ml: "സാധാരണ സമയം", hi: "सामान्य समय" },
  loading: { en: "Loading…", ml: "ലോഡ് ചെയ്യുന്നു…", hi: "लोड हो रहा है…" },
  tagline: { en: "Done, now, next - in her own language.", ml: "കഴിഞ്ഞത്, ഇപ്പോൾ, അടുത്തത് - അവളുടെ സ്വന്തം ഭാഷയിൽ.", hi: "हो चुका, अभी, आगे - उसकी अपनी भाषा में." },
  journeyIntro: { en: "We build your timeline from one date. Everything after that updates on its own.", ml: "ഒരു തീയതിയിൽ നിന്ന് ഞങ്ങൾ നിങ്ങളുടെ ടൈംലൈൻ തയ്യാറാക്കും. ബാക്കിയെല്ലാം സ്വയം പുതുക്കപ്പെടും.", hi: "हम एक तारीख से आपकी टाइमलाइन बनाते हैं। उसके बाद सब कुछ अपने आप अपडेट होता है।" },
  giveOneDate: { en: "Give one of the two dates", ml: "രണ്ട് തീയതികളിൽ ഒന്ന് നൽകൂ", hi: "दोनों में से एक तारीख दें" },
  couldNotStart: { en: "Could not start journey", ml: "യാത്ര തുടങ്ങാൻ കഴിഞ്ഞില്ല", hi: "यात्रा शुरू नहीं हो सकी" },
  loginFailed: { en: "Login failed", ml: "ലോഗിൻ പരാജയപ്പെട്ടു", hi: "लॉग इन विफल" },
  signupFailed: { en: "Signup failed", ml: "അക്കൗണ്ട് ഉണ്ടാക്കാൻ കഴിഞ്ഞില്ല", hi: "साइन अप विफल" },
  noAccount: { en: "New here?", ml: "പുതിയ ആളാണോ?", hi: "नए हैं?" },
  haveAccount: { en: "Already have an account?", ml: "അക്കൗണ്ട് ഉണ്ടോ?", hi: "पहले से खाता है?" },
  consent: {
    en: "I understand CareTrail keeps my pregnancy timeline and reports on this service, uses AI to read my reports and answer questions, and never replaces my doctor. I agree to my data being used this way.",
    ml: "CareTrail എന്റെ ഗർഭകാല ടൈംലൈനും റിപ്പോർട്ടുകളും ഈ സേവനത്തിൽ സൂക്ഷിക്കുമെന്നും, റിപ്പോർട്ടുകൾ വായിക്കാനും ചോദ്യങ്ങൾക്ക് ഉത്തരം നൽകാനും AI ഉപയോഗിക്കുമെന്നും, ഒരിക്കലും എന്റെ ഡോക്ടർക്ക് പകരമാവില്ലെന്നും ഞാൻ മനസ്സിലാക്കുന്നു. എന്റെ വിവരങ്ങൾ ഇങ്ങനെ ഉപയോഗിക്കുന്നതിന് ഞാൻ സമ്മതിക്കുന്നു.",
    hi: "मैं समझती हूँ कि CareTrail मेरी गर्भावस्था की टाइमलाइन और रिपोर्ट इस सेवा पर रखता है, मेरी रिपोर्ट पढ़ने और सवालों के जवाब देने के लिए AI का उपयोग करता है, और कभी मेरे डॉक्टर की जगह नहीं लेता। मैं अपने डेटा के इस उपयोग के लिए सहमत हूँ।",
  },
  orEmail: { en: "Or sign in with email", ml: "അല്ലെങ്കിൽ ഇമെയിൽ വഴി ലോഗിൻ ചെയ്യൂ", hi: "या ईमेल से साइन इन करें" },
  email: { en: "Email", ml: "ഇമെയിൽ", hi: "ईमेल" },
  tickConsent: { en: "Please tick the consent box first.", ml: "ആദ്യം സമ്മത ബോക്സ് ടിക്ക് ചെയ്യൂ.", hi: "कृपया पहले सहमति बॉक्स पर टिक करें।" },
  checkEmail: { en: "Check your email for the sign-in link.", ml: "ലോഗിൻ ലിങ്കിനായി ഇമെയിൽ നോക്കൂ.", hi: "साइन-इन लिंक के लिए अपना ईमेल देखें।" },
  wrongLogin: { en: "Wrong phone or password", ml: "ഫോൺ നമ്പറോ പാസ്‌വേഡോ തെറ്റാണ്", hi: "फ़ोन नंबर या पासवर्ड गलत है" },
  demoFailed: { en: "Could not open the demo. Please try again.", ml: "ഡെമോ തുറക്കാൻ കഴിഞ്ഞില്ല. വീണ്ടും ശ്രമിക്കൂ.", hi: "डेमो नहीं खुल सका। फिर से कोशिश करें।" },
  emailConsent: { en: "I consent to CareTrail storing my pregnancy data to personalize my care.", ml: "എന്റെ പരിചരണം വ്യക്തിഗതമാക്കാൻ CareTrail എന്റെ ഗർഭകാല വിവരങ്ങൾ സൂക്ഷിക്കുന്നതിന് ഞാൻ സമ്മതിക്കുന്നു.", hi: "मैं अपनी देखभाल को व्यक्तिगत बनाने के लिए CareTrail द्वारा मेरी गर्भावस्था का डेटा रखने की सहमति देती हूँ।" },
  phoneExists: { en: "An account with this phone already exists", ml: "ഈ ഫോൺ നമ്പറിൽ ഇതിനകം ഒരു അക്കൗണ്ട് ഉണ്ട്", hi: "इस फ़ोन नंबर से पहले से एक खाता है" },
  signinFailed: { en: "Sign-in failed", ml: "ലോഗിൻ പരാജയപ്പെട്ടു", hi: "साइन इन विफल" },
  sendLink: { en: "Email me a sign-in link", ml: "ലോഗിൻ ലിങ്ക് അയയ്ക്കൂ", hi: "साइन-इन लिंक भेजें" },
};

const LANG_KEY = "ct:lang";

/** The chosen language, shared by every screen and remembered across visits. */
export function useLang(initial?: string): [Lang, (l: Lang) => void] {
  const [lang, setLangState] = useState<Lang>("en");
  useEffect(() => {
    const stored = typeof window !== "undefined" ? localStorage.getItem(LANG_KEY) : null;
    const pick = stored ?? initial;
    if (pick === "en" || pick === "ml" || pick === "hi") setLangState(pick);
  }, [initial]);
  const setLang = useCallback((l: Lang) => {
    setLangState(l);
    if (typeof window !== "undefined") localStorage.setItem(LANG_KEY, l);
  }, []);
  return [lang, setLang];
}

const API = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8000";

/** Translates any English text shown on screen (content from the server, labels,
 * tips) into `lang`. Strings are batched, translated once by Sarvam on the server,
 * and cached on the phone so the next visit is instant and works offline. */
export function useTr(lang: Lang): (text: string | null | undefined) => string {
  const [cache, setCache] = useState<Record<string, string>>({});
  const pending = useRef<Set<string>>(new Set());
  const inflight = useRef<Set<string>>(new Set());

  useEffect(() => {
    if (lang === "en") { setCache({}); return; }
    try { setCache(JSON.parse(localStorage.getItem(`ct:tr:${lang}`) ?? "{}")); } catch { setCache({}); }
  }, [lang]);

  useEffect(() => {
    if (lang === "en") return;
    const todo = Array.from(pending.current).filter((s) => !(s in cache) && !inflight.current.has(s));
    pending.current.clear();
    if (todo.length === 0) return;
    todo.forEach((s) => inflight.current.add(s));
    const token = typeof window !== "undefined" ? localStorage.getItem("caretrail_token") : null;
    if (!token) return;
    const chunks: string[][] = [];
    for (let i = 0; i < todo.length; i += 60) chunks.push(todo.slice(i, i + 60));
    chunks.forEach((chunk) => {
      fetch(`${API}/i18n/translate`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ texts: chunk, lang }),
      })
        .then((r) => (r.ok ? r.json() : null))
        .then((res) => {
          if (!res?.translations) return;
          setCache((prev) => {
            const next = { ...prev, ...res.translations };
            try { localStorage.setItem(`ct:tr:${lang}`, JSON.stringify(next)); } catch { /* storage full */ }
            return next;
          });
        })
        .catch(() => { /* keep English */ })
        .finally(() => chunk.forEach((s) => inflight.current.delete(s)));
    });
  });

  return useCallback(
    (text) => {
      if (!text) return "";
      if (lang === "en") return text;
      if (text in cache) return cache[text];
      pending.current.add(text);
      return text;
    },
    [lang, cache]
  );
}

export function t(key: keyof typeof dict, lang: Lang): string {
  return dict[key]?.[lang] ?? dict[key]?.en ?? key;
}

export const TEST_LABELS: Record<string, string> = {
  hb: "Haemoglobin",
  bp_sys: "Systolic BP",
  bp_dia: "Diastolic BP",
  glucose_fasting: "Fasting glucose",
  glucose_ogtt_1h: "OGTT 1-hour glucose",
  glucose_ogtt_2h: "OGTT 2-hour glucose",
};
export function testLabel(code: string): string {
  return TEST_LABELS[code] ?? code.replace(/_/g, " ");
}
