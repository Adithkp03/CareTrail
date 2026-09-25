"use client";
import { t, useLang, useTr } from "@/lib/i18n";

export default function DataSafetyPage() {
  const [lang] = useLang();
  const tr = useTr(lang);
  return <main className="mx-auto max-w-xl space-y-4 px-4 py-8 text-sm text-ink/80">
    <a className="text-brand underline" href="/">← CareTrail</a>
    <h1 className="text-2xl font-semibold text-ink">{t("dataSafety", lang)}</h1>
    <p>{t("medicalReviewNote", lang)}</p>
    <p>{tr("CareTrail is a prototype, not a clinical record or emergency service. It keeps pregnancy dates, reports, extracted results, and account details to build your timeline. Reports are stored on the service; recent timeline data may also be cached on this device for offline viewing.")}</p>
    <p>{tr("Cloud AI services may process report text, images, and questions. Phone numbers, email addresses and some name lines are masked in text before a cloud model call, but that masking is not complete protection, especially in images or unusual report formats. Do not upload a real patient report to this demo.")}</p>
    <p>{tr("Today, AI call traces can include prompts and outputs, and the trace view is not scoped to one patient. Lab names or patient details may appear there. We cannot promise that lab identity stays private. A separate unmerged privacy fix is under review; it is not active here.")}</p>
    <p>{tr("Uploaded files are stored on instance-local disk in this build. On serverless hosting, that storage is temporary and may disappear; do not rely on it as your only copy. The prototype does not offer a tested retention or deletion workflow. Ask the team before entering real health information.")}</p>
  </main>;
}
