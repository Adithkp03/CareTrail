"""Hand-checked translations for fixed medical content (milestone names, danger
signs, test names). Machine translation mangles clinical terms ("anomaly scan"
came back as "contradiction check"), so these win over Sarvam. Everything else
falls through to Sarvam Translate. Still needs a native-speaker/doctor review."""

CURATED: dict[str, dict[str, str]] = {
    "First consultation (booking visit)": {"ml": "ആദ്യ കൺസൾട്ടേഷൻ (ബുക്കിംഗ് വിസിറ്റ്)", "hi": "पहली जाँच (बुकिंग विज़िट)"},
    "Baseline blood tests": {"ml": "അടിസ്ഥാന രക്തപരിശോധനകൾ", "hi": "शुरुआती खून की जाँचें"},
    "NT scan (first trimester ultrasound)": {"ml": "NT സ്കാൻ (ആദ്യ ത്രൈമാസ അൾട്രാസൗണ്ട്)", "hi": "NT स्कैन (पहली तिमाही का अल्ट्रासाउंड)"},
    "Second-trimester review": {"ml": "രണ്ടാം ത്രൈമാസ പരിശോധന", "hi": "दूसरी तिमाही की जाँच"},
    "Anomaly scan (TIFFA)": {"ml": "അനോമലി സ്കാൻ (TIFFA)", "hi": "एनॉमली स्कैन (TIFFA)"},
    "Antenatal consultation (24 weeks)": {"ml": "ഗർഭകാല പരിശോധന (24 ആഴ്ച)", "hi": "प्रसवपूर्व जाँच (24 सप्ताह)"},
    "Glucose tolerance test (OGTT)": {"ml": "ഗ്ലൂക്കോസ് ടോളറൻസ് ടെസ്റ്റ് (OGTT)", "hi": "ग्लूकोज़ टॉलरेंस टेस्ट (OGTT)"},
    "Tdap/TT vaccination": {"ml": "Tdap/TT കുത്തിവെപ്പ്", "hi": "Tdap/TT टीकाकरण"},
    "Third-trimester review": {"ml": "മൂന്നാം ത്രൈമാസ പരിശോധന", "hi": "तीसरी तिमाही की जाँच"},
    "Growth scan": {"ml": "ഗ്രോത്ത് സ്കാൻ (വളർച്ചാ സ്കാൻ)", "hi": "ग्रोथ स्कैन (विकास स्कैन)"},
    "Antenatal review (34 weeks)": {"ml": "ഗർഭകാല പരിശോധന (34 ആഴ്ച)", "hi": "प्रसवपूर्व जाँच (34 सप्ताह)"},
    "Antenatal review (36 weeks)": {"ml": "ഗർഭകാല പരിശോധന (36 ആഴ്ച)", "hi": "प्रसवपूर्व जाँच (36 सप्ताह)"},
    "Antenatal review (38 weeks)": {"ml": "ഗർഭകാല പരിശോധന (38 ആഴ്ച)", "hi": "प्रसवपूर्व जाँच (38 सप्ताह)"},
    "Birth plan review": {"ml": "പ്രസവ പ്ലാൻ പരിശോധന", "hi": "प्रसव योजना की जाँच"},
    "Bleeding or leaking fluid": {"ml": "രക്തസ്രാവം അല്ലെങ്കിൽ വെള്ളം പോകൽ", "hi": "खून आना या पानी रिसना"},
    "Severe headache or blurred vision": {"ml": "കടുത്ത തലവേദന അല്ലെങ്കിൽ കാഴ്ച മങ്ങൽ", "hi": "तेज़ सिरदर्द या धुंधला दिखना"},
    "Reduced or absent baby movements": {"ml": "കുഞ്ഞിന്റെ അനക്കം കുറയുകയോ ഇല്ലാതാവുകയോ ചെയ്യുക", "hi": "बच्चे की हलचल कम होना या बंद होना"},
    "Severe abdominal pain": {"ml": "കടുത്ത വയറുവേദന", "hi": "पेट में तेज़ दर्द"},
    "High fever": {"ml": "കടുത്ത പനി", "hi": "तेज़ बुखार"},
    "Swelling of face and hands with headache": {"ml": "തലവേദനയോടൊപ്പം മുഖത്തും കൈകളിലും നീര്", "hi": "सिरदर्द के साथ चेहरे और हाथों में सूजन"},
    # Draft translations of added warning signs. Clinical/native-speaker review pending.
    "Severe nausea or vomiting": {"ml": "കടുത്ത ഓക്കാനം അല്ലെങ്കിൽ ഛർദ്ദി", "hi": "तेज़ मतली या उल्टी", "ta": "கடுமையான குமட்டல் அல்லது வாந்தி", "te": "తీవ్రమైన వికారం లేదా వాంతులు", "kn": "ತೀವ್ರ ವಾಕರಿಕೆ ಅಥವಾ ವಾಂತಿ", "bn": "তীব্র বমি বমি ভাব বা বমি", "mr": "तीव्र मळमळ किंवा उलट्या"},
    "Right-sided upper abdominal pain": {"ml": "വയറിന്റെ വലതുഭാഗത്ത് മുകളിൽ വേദന", "hi": "पेट के ऊपर दाईं ओर दर्द", "ta": "வயிற்றின் வலது மேல் பகுதியில் வலி", "te": "కడుపు కుడి పైభాగంలో నొప్పి", "kn": "ಹೊಟ್ಟೆಯ ಬಲ ಮೇಲ್ಭಾಗದಲ್ಲಿ ನೋವು", "bn": "পেটের ডান দিকের ওপরের অংশে ব্যথা", "mr": "पोटाच्या उजव्या वरच्या भागात वेदना"},
    "Reduced urine": {"ml": "മൂത്രത്തിന്റെ അളവ് കുറയുക", "hi": "पेशाब कम होना", "ta": "சிறுநீர் குறைவாக வருதல்", "te": "మూత్రం తగ్గడం", "kn": "ಮೂತ್ರದ ಪ್ರಮಾಣ ಕಡಿಮೆಯಾಗುವುದು", "bn": "প্রস্রাব কমে যাওয়া", "mr": "लघवी कमी होणे"},
    "Sudden swelling of the whole body": {"ml": "ശരീരം മുഴുവൻ പെട്ടെന്ന് വീർക്കുക", "hi": "पूरे शरीर में अचानक सूजन", "ta": "உடல் முழுவதும் திடீர் வீக்கம்", "te": "శరీరం అంతా అకస్మాత్తుగా వాపు", "kn": "ಇಡೀ ದೇಹದಲ್ಲಿ ಹಠಾತ್ ಊತ", "bn": "সারা শরীরে হঠাৎ ফোলা", "mr": "संपूर्ण शरीरावर अचानक सूज"},
    "Fasting glucose": {"ml": "ഫാസ്റ്റിംഗ് ഗ്ലൂക്കോസ്", "hi": "फास्टिंग ग्लूकोज़"},
    "Systolic BP": {"ml": "സിസ്റ്റോളിക് ബിപി (മുകളിലെ രക്തസമ്മർദ്ദം)", "hi": "सिस्टोलिक बीपी (ऊपर का ब्लड प्रेशर)"},
    "Diastolic BP": {"ml": "ഡയസ്റ്റോളിക് ബിപി (താഴത്തെ രക്തസമ്മർദ്ദം)", "hi": "डायस्टोलिक बीपी (नीचे का ब्लड प्रेशर)"},
    "OGTT 1-hour glucose": {"ml": "OGTT 1 മണിക്കൂർ ഗ്ലൂക്കോസ്", "hi": "OGTT 1 घंटे का ग्लूकोज़"},
    "OGTT 2-hour glucose": {"ml": "OGTT 2 മണിക്കൂർ ഗ്ലൂക്കോസ്", "hi": "OGTT 2 घंटे का ग्लूकोज़"},
    "Haemoglobin": {"ml": "ഹീമോഗ്ലോബിൻ", "hi": "हीमोग्लोबिन"},
    "Hemoglobin": {"ml": "ഹീമോഗ്ലോബിൻ", "hi": "हीमोग्लोबिन"},
    "Fasting glucose is 92 mg/dL or above (IADPSG).": {"ml": "ഫാസ്റ്റിംഗ് ഗ്ലൂക്കോസ് 92 mg/dL അല്ലെങ്കിൽ അതിൽ കൂടുതലാണ് (IADPSG).", "hi": "फास्टिंग ग्लूकोज़ 92 mg/dL या उससे अधिक है (IADPSG)."},
    "Get ready": {"ml": "തയ്യാറെടുക്കാം", "hi": "तैयारी करें"},
    "Questions worth asking": {"ml": "ചോദിക്കേണ്ട ചോദ്യങ്ങൾ", "hi": "पूछने लायक सवाल"},
}


def curated(text: str, lang: str) -> str | None:
    return CURATED.get(text, {}).get(lang)
