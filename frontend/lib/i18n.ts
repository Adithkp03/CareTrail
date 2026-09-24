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
};

export function t(key: keyof typeof dict, lang: Lang): string {
  return dict[key]?.[lang] ?? dict[key]?.en ?? key;
}
