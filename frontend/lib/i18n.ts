"use client";
import { useCallback, useEffect, useRef, useState } from "react";

export type Lang = "en" | "ml" | "hi" | "ta" | "te" | "kn" | "bn" | "mr";

export const LANGS: { code: Lang; label: string }[] = [
  { code: "en", label: "English" },
  { code: "ml", label: "മലയാളം" },
  { code: "hi", label: "हिन्दी" },
  { code: "ta", label: "தமிழ்" },
  { code: "te", label: "తెలుగు" },
  { code: "kn", label: "ಕನ್ನಡ" },
  { code: "bn", label: "বাংলা" },
  { code: "mr", label: "मराठी" },
];

// Static UI labels for the demo. Sarvam Translate takes over full content
// translation (milestone titles, explanations, audio) in phase 4.
const dict: Record<string, Record<"en" | "ml" | "hi", string> & Partial<Record<Lang, string>>> = {
  earlyCare: { en: "Early pregnancy care", ml: "ഗർഭകാലത്തിന്റെ ആദ്യ പരിചരണം", hi: "शुरुआती गर्भावस्था की देखभाल" },
  earlyCareHint: { en: "What to do in the first trimester", ml: "ആദ്യ ത്രൈമാസത്തിൽ ചെയ്യേണ്ടത്", hi: "पहली तिमाही में क्या करें" },
  firstVisitChecks: { en: "First-visit investigations", ml: "ആദ്യ സന്ദർശനത്തിലെ പരിശോധനകൾ", hi: "पहली मुलाकात की जाँचें" },
  movementTracker: { en: "Baby movements", ml: "കുഞ്ഞിന്റെ അനക്കം", hi: "बच्चे की हलचल" },
  movementInstruction: { en: "Noticed a movement? Tap + to record it today. This is a personal note, not a medical test or a target count.", ml: "കുഞ്ഞിന്റെ അനക്കം ശ്രദ്ധിച്ചോ? ഇന്നത്തേക്ക് രേഖപ്പെടുത്താൻ + അമർത്തുക. ഇത് വ്യക്തിഗത കുറിപ്പാണ്; വൈദ്യപരിശോധനയോ ലക്ഷ്യസംഖ്യയോ അല്ല.", hi: "बच्चे की हलचल महसूस हुई? आज दर्ज करने के लिए + दबाएँ। यह निजी नोट है, चिकित्सीय जाँच या तय संख्या नहीं।" },
  movementCount: { en: "Movements noted today", ml: "ഇന്ന് രേഖപ്പെടുത്തിയ അനക്കങ്ങൾ", hi: "आज दर्ज हलचलें" },
  movementWarning: { en: "If movements seem reduced or stop, contact your doctor promptly. Do not wait for a number on this tracker.", ml: "അനക്കം കുറഞ്ഞതായി തോന്നുകയോ നിലയ്ക്കുകയോ ചെയ്താൽ ഉടൻ ഡോക്ടറെ ബന്ധപ്പെടുക. ഈ ട്രാക്കറിലെ സംഖ്യയ്ക്കായി കാത്തിരിക്കരുത്.", hi: "हलचल कम लगे या बंद हो जाए तो तुरंत डॉक्टर से संपर्क करें। इस ट्रैकर की संख्या का इंतज़ार न करें।" },
  movementStorage: { en: "Saved only on this device. Not shared with a clinician.", ml: "ഈ ഉപകരണത്തിൽ മാത്രം സൂക്ഷിക്കുന്നു. ഡോക്ടറുമായി പങ്കിടുന്നില്ല.", hi: "सिर्फ़ इस डिवाइस पर सहेजा जाता है। डॉक्टर से साझा नहीं होता।" },
  translationNote: { completedOf: "మైలురాళ్లు పూర్తయ్యాయి", youAreHere: "మీరు ఇక్కడ ఉన్నారు", nextStep: "మీ తదుపరి దశ", movementStorage: "ఈ పరికరంలో మాత్రమే సేవ్ చేయబడింది. వైద్యునితో భాగస్వామ్యం చేయబడలేదు.", movementWarning: "కదలికలు తగ్గినట్లు లేదా ఆగిపోయినట్లు అనిపిస్తే, వెంటనే మీ వైద్యుడిని సంప్రదించండి. ఈ ట్రాకర్‌లో నంబర్ కోసం వేచి ఉండకండి.", movementInstruction: "కదలికను గమనించారా? ఈరోజే రికార్డ్ చేయడానికి + నొక్కండి. ఇది వ్యక్తిగత గమనిక, వైద్య పరీక్ష లేదా లక్ష్య గణన కాదు.", en: "Some medical text is still shown in English. Please confirm it with your clinician.", ml: "ചില വൈദ്യവിവരങ്ങൾ ഇപ്പോഴും ഇംഗ്ലീഷിലാണ്. ഡോക്ടറോട് സ്ഥിരീകരിക്കുക.", hi: "कुछ चिकित्सा जानकारी अभी अंग्रेज़ी में है। अपने डॉक्टर से पुष्टि करें।" },
  movementUndo: { en: "Undo one", ml: "ഒന്ന് പിൻവലിക്കൂ", hi: "एक घटाएँ" },
  showAllMilestones: { en: "Show all milestones", ml: "എല്ലാ ഘട്ടങ്ങളും കാണൂ", hi: "सभी चरण देखें" },
  journeyTitle: { en: "Your pregnancy journey", ml: "നിങ്ങളുടെ ഗർഭകാല യാത്ര", hi: "आपकी गर्भावस्था की यात्रा" },
  youAreHere: { en: "You are here", ml: "നിങ്ങൾ ഇവിടെ", hi: "आप यहाँ हैं" },
  completedOf: { en: "milestones completed", ml: "ഘട്ടങ്ങൾ പൂർത്തിയായി", hi: "चरण पूरे हुए" },
  firstTrimester: { en: "First trimester", ml: "ഒന്നാം ത്രൈമാസം", hi: "पहली तिमाही" },
  secondTrimester: { en: "Second trimester", ml: "രണ്ടാം ത്രൈമാസം", hi: "दूसरी तिमाही" },
  thirdTrimester: { en: "Third trimester", ml: "മൂന്നാം ത്രൈമാസം", hi: "तीसरी तिमाही" },
  beyondPathway: { en: "Beyond this pathway", ml: "ഈ പരിചരണപാതയ്ക്ക് ശേഷം", hi: "इस देखभाल योजना के बाद" },
  nextStep: { en: "Your next step", ml: "നിങ്ങളുടെ അടുത്ത ഘട്ടം", hi: "आपका अगला कदम" },
  attentionNeeded: { en: "Items needing attention", ml: "ശ്രദ്ധിക്കേണ്ട കാര്യങ്ങൾ", hi: "ध्यान देने योग्य बातें" },
  noOpenItems: { en: "No outstanding items recorded in this tracker. This is not a medical assessment.", ml: "ഈ ട്രാക്കറിൽ ബാക്കിയുള്ള കാര്യങ്ങളൊന്നും രേഖപ്പെടുത്തിയിട്ടില്ല. ഇത് വൈദ്യപരിശോധനയല്ല.", hi: "इस ट्रैकर में कोई लंबित मुद्दा दर्ज नहीं है। यह चिकित्सीय मूल्यांकन नहीं है।" },
  overdueMilestone: { en: "Overdue milestone", ml: "കാലാവധി കഴിഞ്ഞ ഘട്ടം", hi: "समय से लंबित चरण" },
  reviewPending: { en: "Review pending", ml: "പരിശോധന ബാക്കിയുണ്ട്", hi: "समीक्षा बाकी है" },
  currentWindow: { en: "In the current window", ml: "നിലവിലെ സമയപരിധിയിൽ", hi: "वर्तमान समय अवधि में" },
  scheduledNext: { en: "Scheduled next", ml: "അടുത്തതായി നിശ്ചയിച്ചത്", hi: "अगली निर्धारित तारीख" },
  updateJourney: { en: "Update your journey", ml: "നിങ്ങളുടെ യാത്ര പുതുക്കൂ", hi: "अपनी यात्रा अपडेट करें" },
  whyNow: { en: "Why now?", ml: "ഇപ്പോൾ എന്തുകൊണ്ട്?", hi: "अभी क्यों?" },
  prototypeReview: { en: "Prototype review flow - clinician identity is not verified in this demo.", ml: "പ്രോട്ടോടൈപ്പ് പരിശോധന - ഈ ഡെമോയിൽ ഡോക്ടറുടെ തിരിച്ചറിയൽ സ്ഥിരീകരിച്ചിട്ടില്ല.", hi: "प्रोटोटाइप समीक्षा - इस डेमो में डॉक्टर की पहचान सत्यापित नहीं है।" },
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
    if (LANGS.some((l) => l.code === pick)) setLangState(pick as Lang);
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

// New-language UI labels are a starter set; untranslated labels remain English.
// Clinical copy goes through the translation service and needs native review.
const extraLabels: Partial<Record<Lang, Record<string, string>>> = {
  ta: { completedOf: "மைல்கற்கள் முடிந்தது", youAreHere: "நீங்கள் இங்கே இருக்கிறீர்கள்", nextStep: "உங்கள் அடுத்த படி", movementStorage: "இந்தச் சாதனத்தில் மட்டுமே சேமிக்கப்பட்டது. மருத்துவருடன் பகிரப்படவில்லை.", movementWarning: "இயக்கங்கள் குறைந்து அல்லது நிறுத்தப்பட்டால், உடனடியாக உங்கள் மருத்துவரை அணுகவும். இந்த டிராக்கரில் எண்ணுக்காக காத்திருக்க வேண்டாம்.", movementInstruction: "ஒரு இயக்கத்தை கவனித்தீர்களா? இன்று பதிவு செய்ய + என்பதைத் தட்டவும். இது ஒரு தனிப்பட்ட குறிப்பு, மருத்துவ பரிசோதனை அல்லது இலக்கு எண்ணிக்கை அல்ல.", translationNote: "சில மருத்துவத் தகவல்கள் இன்னும் ஆங்கிலத்தில் உள்ளன. மருத்துவரிடம் உறுதிப்படுத்துங்கள்.", language: "மொழி", login: "உள்நுழைக", signup: "பதிவு செய்க", phone: "தொலைபேசி எண்", password: "கடவுச்சொல்", tryDemo: "மாதிரியைக் காண்க (அஞ்சலி)", firstTrimester: "முதல் மூன்று மாதங்கள்", secondTrimester: "இரண்டாம் மூன்று மாதங்கள்", thirdTrimester: "மூன்றாம் மூன்று மாதங்கள்", journeyTitle: "உங்கள் கர்ப்பகாலப் பயணம்", earlyCare: "ஆரம்பகால கர்ப்ப பராமரிப்பு", firstVisitChecks: "முதல் வருகை விசாரணைகள்", movementTracker: "குழந்தை அசைவுகள்", dangerSigns: "இந்த அறிகுறிகள் இருந்தால் உடனே மருத்துவரை அழையுங்கள்", movementCount: "இன்று பதிவு செய்த அசைவுகள்", movementUndo: "ஒன்றை நீக்கு" },
  te: { translationNote: "కొంత వైద్య సమాచారం ఇంకా ఆంగ్లంలో ఉంది. వైద్యుడితో నిర్ధారించుకోండి.", language: "భాష", login: "లాగిన్", signup: "నమోదు", phone: "ఫోన్ నంబర్", password: "పాస్‌వర్డ్", tryDemo: "డెమో చూడండి (అంజలి)", firstTrimester: "మొదటి త్రైమాసికం", secondTrimester: "రెండో త్రైమాసికం", thirdTrimester: "మూడో త్రైమాసికం", journeyTitle: "మీ గర్భధారణ ప్రయాణం", earlyCare: "ప్రారంభ గర్భ సంరక్షణ", firstVisitChecks: "మొదటి సందర్శన పరిశోధనలు", movementTracker: "శిశువు కదలికలు", dangerSigns: "ఈ లక్షణాలుంటే వెంటనే వైద్యుణ్ని సంప్రదించండి", movementCount: "ఈ రోజు నమోదు చేసిన కదలికలు", movementUndo: "ఒకటి తీసివేయి" },
  kn: { completedOf: "ಮೈಲಿಗಲ್ಲುಗಳು ಪೂರ್ಣಗೊಂಡಿವೆ", youAreHere: "ನೀವು ಇಲ್ಲಿದ್ದೀರಿ", nextStep: "ನಿಮ್ಮ ಮುಂದಿನ ಹೆಜ್ಜೆ", movementStorage: "ಈ ಸಾಧನದಲ್ಲಿ ಮಾತ್ರ ಉಳಿಸಲಾಗಿದೆ. ವೈದ್ಯರೊಂದಿಗೆ ಹಂಚಿಕೊಂಡಿಲ್ಲ.", movementWarning: "ಚಲನೆಗಳು ಕಡಿಮೆಯಾಗಿದ್ದರೆ ಅಥವಾ ನಿಲ್ಲಿಸಿದರೆ, ನಿಮ್ಮ ವೈದ್ಯರನ್ನು ತಕ್ಷಣವೇ ಸಂಪರ್ಕಿಸಿ. ಈ ಟ್ರ್ಯಾಕರ್‌ನಲ್ಲಿ ಸಂಖ್ಯೆಗಾಗಿ ಕಾಯಬೇಡಿ.", movementInstruction: "ಚಲನೆಯನ್ನು ಗಮನಿಸಿದ್ದೀರಾ? ಇಂದು ಅದನ್ನು ರೆಕಾರ್ಡ್ ಮಾಡಲು + ಟ್ಯಾಪ್ ಮಾಡಿ. ಇದು ವೈಯಕ್ತಿಕ ಟಿಪ್ಪಣಿಯಾಗಿದೆ, ವೈದ್ಯಕೀಯ ಪರೀಕ್ಷೆ ಅಥವಾ ಗುರಿ ಎಣಿಕೆ ಅಲ್ಲ.", translationNote: "ಕೆಲವು ವೈದ್ಯಕೀಯ ಮಾಹಿತಿ ಇನ್ನೂ ಇಂಗ್ಲಿಷ್‌ನಲ್ಲಿದೆ. ವೈದ್ಯರೊಂದಿಗೆ ಖಚಿತಪಡಿಸಿಕೊಳ್ಳಿ.", language: "ಭಾಷೆ", login: "ಲಾಗಿನ್", signup: "ನೋಂದಾಯಿಸಿ", phone: "ಫೋನ್ ಸಂಖ್ಯೆ", password: "ಪಾಸ್‌ವರ್ಡ್", tryDemo: "ಡೆಮೊ ನೋಡಿ (ಅಂಜಲಿ)", firstTrimester: "ಮೊದಲ ತ್ರೈಮಾಸಿಕ", secondTrimester: "ಎರಡನೇ ತ್ರೈಮಾಸಿಕ", thirdTrimester: "ಮೂರನೇ ತ್ರೈಮಾಸಿಕ", journeyTitle: "ನಿಮ್ಮ ಗರ್ಭಧಾರಣೆಯ ಪಯಣ", earlyCare: "ಆರಂಭಿಕ ಗರ್ಭಧಾರಣೆಯ ಆರೈಕೆ", firstVisitChecks: "ಮೊದಲ ಭೇಟಿಯ ತನಿಖೆಗಳು", movementTracker: "ಮಗುವಿನ ಚಲನೆಗಳು", dangerSigns: "ಈ ಲಕ್ಷಣಗಳಿದ್ದರೆ ತಕ್ಷಣ ವೈದ್ಯರನ್ನು ಸಂಪರ್ಕಿಸಿ", movementCount: "ಇಂದು ದಾಖಲಿಸಿದ ಚಲನೆಗಳು", movementUndo: "ಒಂದನ್ನು ತೆಗೆದುಹಾಕಿ" },
  bn: { completedOf: "মাইলফলক সম্পন্ন", youAreHere: "তুমি এখানে", nextStep: "আপনার পরবর্তী পদক্ষেপ", movementStorage: "শুধুমাত্র এই ডিভাইসে সংরক্ষিত. একজন চিকিত্সকের সাথে ভাগ করা হয়নি।", movementWarning: "যদি নড়াচড়া কমে বা বন্ধ বলে মনে হয়, অবিলম্বে আপনার ডাক্তারের সাথে যোগাযোগ করুন। এই ট্র্যাকারে একটি নম্বরের জন্য অপেক্ষা করবেন না।", movementInstruction: "একটি আন্দোলন লক্ষ্য করেছেন? আজ এটি রেকর্ড করতে + আলতো চাপুন। এটি একটি ব্যক্তিগত নোট, একটি মেডিকেল পরীক্ষা বা লক্ষ্য গণনা নয়।", translationNote: "কিছু চিকিৎসা সংক্রান্ত তথ্য এখনও ইংরেজিতে আছে। ডাক্তারের সঙ্গে নিশ্চিত করুন।", language: "ভাষা", login: "লগ ইন", signup: "সাইন আপ", phone: "ফোন নম্বর", password: "পাসওয়ার্ড", tryDemo: "ডেমো দেখুন (অঞ্জলি)", firstTrimester: "প্রথম ত্রৈমাসিক", secondTrimester: "দ্বিতীয় ত্রৈমাসিক", thirdTrimester: "তৃতীয় ত্রৈমাসিক", journeyTitle: "আপনার গর্ভাবস্থার যাত্রা", earlyCare: "গর্ভাবস্থার প্রাথমিক যত্ন", firstVisitChecks: "প্রথম পরিদর্শন তদন্ত", movementTracker: "শিশুর নড়াচড়া", dangerSigns: "এই লক্ষণগুলি থাকলে এখনই ডাক্তারকে জানান", movementCount: "আজ লেখা নড়াচড়া", movementUndo: "একটি বাদ দিন" },
  mr: { completedOf: "टप्पे पूर्ण केले", youAreHere: "आपण येथे आहात", nextStep: "तुमची पुढची पायरी", movementStorage: "फक्त या डिव्हाइसवर जतन केले. क्लिनिकशी शेअर केलेले नाही.", movementWarning: "हालचाली कमी झाल्या किंवा थांबल्यासारखे वाटत असल्यास, त्वरीत डॉक्टरांशी संपर्क साधा. या ट्रॅकरवर नंबरची वाट पाहू नका.", movementInstruction: "एक हालचाल लक्षात आली? आज रेकॉर्ड करण्यासाठी + वर टॅप करा. ही वैयक्तिक नोंद आहे, वैद्यकीय चाचणी किंवा लक्ष्य संख्या नाही.", translationNote: "काही वैद्यकीय माहिती अजून इंग्रजीत आहे. डॉक्टरांकडून खात्री करा.", language: "भाषा", login: "लॉग इन", signup: "नोंदणी करा", phone: "फोन नंबर", password: "पासवर्ड", tryDemo: "डेमो पहा (अंजली)", firstTrimester: "पहिली तिमाही", secondTrimester: "दुसरी तिमाही", thirdTrimester: "तिसरी तिमाही", journeyTitle: "तुमचा गरोदरपणाचा प्रवास", earlyCare: "लवकर गर्भधारणा काळजी", firstVisitChecks: "प्रथम-भेट तपास", movementTracker: "बाळाच्या हालचाली", dangerSigns: "ही लक्षणे असल्यास लगेच डॉक्टरांना भेटा", movementCount: "आज नोंदवलेल्या हालचाली", movementUndo: "एक कमी करा" },
};

export function t(key: keyof typeof dict, lang: Lang): string {
  return extraLabels[lang]?.[key] ?? dict[key]?.[lang] ?? dict[key]?.en ?? key;
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
