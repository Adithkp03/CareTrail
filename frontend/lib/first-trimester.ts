import type { Lang } from "./i18n";

type Text = Record<"en" | "ml" | "hi", string> & Partial<Record<Lang, string>>;
export const careText = (text: Text, lang: Lang) => text[lang] ?? text.en;
export const firstTrimesterSteps: { icon: string; title: Text; body: Text; urgent?: boolean }[] = [
  { icon: "test", title: { en: "Positive pregnancy test", ml: "ഗർഭപരിശോധന പോസിറ്റീവ്", hi: "गर्भावस्था की जाँच पॉज़िटिव" }, body: { en: "After a positive urine pregnancy test, get an ultrasound to confirm where the pregnancy is located.", ml: "മൂത്ര ഗർഭപരിശോധന പോസിറ്റീവായാൽ, ഗർഭം എവിടെയാണെന്ന് സ്ഥിരീകരിക്കാൻ അൾട്രാസൗണ്ട് ചെയ്യിക്കുക.", hi: "पेशाब की गर्भावस्था जाँच पॉज़िटिव हो तो गर्भ कहाँ है, यह जानने के लिए अल्ट्रासाउंड करवाएँ।" } },
  { icon: "hospital", title: { en: "Register your pregnancy", ml: "ഗർഭം രജിസ്റ്റർ ചെയ്യുക", hi: "गर्भावस्था का पंजीकरण कराएँ" }, body: { en: "Ask a local government health facility how to register your pregnancy.", ml: "ഗർഭം എങ്ങനെ രജിസ്റ്റർ ചെയ്യണമെന്ന് അടുത്തുള്ള സർക്കാർ ആരോഗ്യകേന്ദ്രത്തിൽ ചോദിക്കുക.", hi: "गर्भावस्था का पंजीकरण कैसे करें, यह पास की सरकारी स्वास्थ्य सुविधा से पूछें।" } },
  { icon: "visit", title: { en: "At your first visit", ml: "ആദ്യ സന്ദർശനത്തിൽ", hi: "पहली मुलाकात में" }, body: { en: "Tell the doctor about any known comorbidities (other health conditions). Ask about the first-visit investigations listed below.", ml: "നിങ്ങൾക്കുള്ള മറ്റ് ആരോഗ്യപ്രശ്നങ്ങൾ ഡോക്ടറോട് പറയുക. താഴെയുള്ള ആദ്യ സന്ദർശന പരിശോധനകളെക്കുറിച്ച് ചോദിക്കുക.", hi: "अपनी दूसरी ज्ञात बीमारियों के बारे में डॉक्टर को बताएँ। नीचे दी गई पहली मुलाकात की जाँचों के बारे में पूछें।" } },
  { icon: "warning", title: { en: "Bleeding or sharp abdominal pain", ml: "രക്തസ്രാവം അല്ലെങ്കിൽ കുത്തുന്ന വയറുവേദന", hi: "खून आना या पेट में तेज़ चुभने वाला दर्द" }, body: { en: "Visit a doctor immediately if you have bleeding or sharp abdominal pain.", ml: "രക്തസ്രാവമോ കുത്തുന്ന വയറുവേദനയോ ഉണ്ടെങ്കിൽ ഉടൻ ഡോക്ടറെ കാണുക.", hi: "खून आए या पेट में तेज़ चुभने वाला दर्द हो तो तुरंत डॉक्टर से मिलें।" }, urgent: true },
  { icon: "warning", title: { en: "Excessive nausea or vomiting", ml: "അമിതമായ ഓക്കാനം അല്ലെങ്കിൽ ഛർദ്ദി", hi: "बहुत ज़्यादा जी मिचलाना या उल्टी" }, body: { en: "Do not ignore excessive nausea or vomiting. Tell your doctor.", ml: "അമിതമായ ഓക്കാനമോ ഛർദ്ദിയോ അവഗണിക്കരുത്. ഡോക്ടറോട് പറയുക.", hi: "बहुत ज़्यादा जी मिचलाने या उल्टी को नज़रअंदाज़ न करें। डॉक्टर को बताएँ।" } },
];

export const firstVisitTests: { icon: string; text: Text }[] = [
  { icon: "blood", text: { en: "Hemoglobin", ml: "ഹീമോഗ്ലോബിൻ", hi: "हीमोग्लोबिन" } },
  { icon: "blood", text: { en: "Platelets", ml: "പ്ലേറ്റ്ലെറ്റുകൾ", hi: "प्लेटलेट्स" } },
  { icon: "blood", text: { en: "Blood group", ml: "രക്തഗ്രൂപ്പ്", hi: "ब्लड ग्रुप" } },
  { icon: "test", text: { en: "HIV / VDRL / HBsAg", ml: "HIV / VDRL / HBsAg", hi: "HIV / VDRL / HBsAg" } },
  { icon: "test", text: { en: "TFT (fasting)", ml: "TFT (ഉപവാസം)", hi: "TFT (खाली पेट)" } },
  { icon: "food", text: { en: "Fasting / post-prandial sugars", ml: "ഉപവാസ / ഭക്ഷണശേഷ രക്തത്തിലെ പഞ്ചസാര", hi: "खाली पेट / खाने के बाद की शुगर" } },
  { icon: "test", text: { en: "Urine routine microscopy and culture", ml: "മൂത്രത്തിന്റെ സാധാരണ മൈക്രോസ്കോപ്പി, കൾച്ചർ", hi: "पेशाब की रूटीन माइक्रोस्कोपी और कल्चर" } },
];

export const milestoneIcons: Record<string, string> = {
  first_consultation: "visit", baseline_bloods: "blood", nt_scan: "scan",
  second_trimester_review: "visit", anomaly_scan: "scan", consultation_24w: "visit",
  ogtt: "test", tdap_vaccine: "vaccination", third_trimester_review: "visit",
  growth_scan: "scan", review_34w: "visit", review_36w: "visit",
  review_38w: "visit", birth_plan: "hospital",
};
