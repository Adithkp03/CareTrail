# Sprint 6: Help assistant, ask-the-doctor, closed-app reminders

User direction (2026-10-29): the chat gives "basic information and other help a
mother would need" and must NOT give medical advice. Concerning questions go to
the doctor through the app.

## What was built

### 1. General-help assistant (`/assistant/*`, `AssistantScreen`)
- New backend pipeline, deliberately separate from the clinical `/ask` flow.
- Code-first routing, before any model:
  - Danger-sign words (including the translated-question pass) -> urgent
    "contact your doctor now" + a button to message the doctor.
  - Advice-seeking questions (symptoms, medicines, doses, "should I...") ->
    fixed deferral to the doctor. Never an individualized answer.
  - App help (appointments, reminders, reports, features) answered
    deterministically from the mother's own data.
  - General pregnancy information answered only from the curated
    FOGSI/WHO-aligned corpus / care-guide explanations. Sarvam, when its key is
    set, may reword that approved text (prompt forbids adding advice or facts);
    without the key the curated text is returned verbatim.
  - Anything else defers and offers the doctor route.
- History is stored per patient (max 100 shown) and can be wiped from the chat
  screen ("Clear chat history") or via DELETE /assistant/history.
- First use shows a consent card: general info only, questions go to the server
  and an AI language service (personal details stripped), history is stored and
  deletable. One-time acknowledgement stored on the phone.
- 60 messages/hour/patient cap.

### 2. Ask the doctor (`/journeys/{id}/doctor-questions`, clinician web page)
- Patient writes and sends each question explicitly; nothing from the assistant
  chat is shared automatically.
- Questions reach only clinicians with an active patient-granted access grant;
  with no linked doctor the app says so and points to the existing grant flow
  (409 from the API). Max 20 open questions per journey.
- Clinicians see questions and answer them on the existing clinician web page;
  the roster shows an "N questions to answer" badge.
- Revoking a grant immediately blocks the clinician's view/answers (404); the
  patient's own copy of the exchange is kept.
- Copy everywhere states: not for emergencies, no reply time promised.

### 3. Closed-app appointment reminders (WorkManager)
- Periodic local check (12 h, network-required) fetches the reminder inbox and
  posts the existing generic local notification ("Open CareTrail to review your
  care reminders" - no health details), still opt-in via the existing toggle
  and the POST_NOTIFICATIONS runtime permission.

## Limits (honest list)

- Reminder timing is best-effort: WorkManager can be delayed or killed by
  Android battery management, especially on OEM builds; no exact-time delivery
  is promised. The in-app Reminders tab remains the source of truth. No FCM /
  server push yet.
- The assistant's no-advice boundary is keyword-based; unusual phrasing can
  slip past the deferral list and land on the curated corpus or the generic
  deferral. It is not a certified advice classifier.
- Danger-sign keyword lists cover en/ml/hi; other languages rely on
  Sarvam translation of the question first - without the key those questions
  get the safe "no ground" fallback instead.
- Sarvam rewording of curated text is prompt-constrained, not verified by a
  clinician. Malayalam/Hindi deferral and danger-sign strings are from the
  existing codebase; other languages are machine-translated at runtime and are
  NOT clinician-reviewed (same status as Sprint 5 labels).
- The doctor route assumes the doctor actually checks the web page; there is
  no notification to the doctor (no email/push) in this sprint.
- Unit-tested only (31 Android + 152 backend). No on-device run: this sandbox
  has no KVM, and the software emulator was unstable in Sprint 5. Chat,
  ask-doctor and the reminder worker still need a real-phone pass.

## Manual phone test steps
1. Sign in (demo: seed), open Home -> "Help assistant". Read the consent card,
   tap "I understand".
2. Ask "When is my next appointment?" -> deterministic answer with your date.
3. Ask "What is the anomaly scan?" -> general explanation.
4. Ask "Can I take paracetamol for back pain?" -> deferral + "Ask your doctor"
   button. Ask "I have bleeding" -> urgent red answer + doctor button.
5. Kill and reopen the app -> history reloads. "Clear chat history" empties it.
6. Home -> "Ask your doctor": without a linked doctor you get the link-first
   card. Link a doctor (Doctor section), send a question, answer it from the
   clinician web page (/clinician), pull-to-refresh in app -> answer visible.
7. Enable reminder notifications (Reminders settings toggle + system
   permission), book an appointment within 3 days, close the app -> a generic
   notification appears within ~12 h (best-effort, battery dependent).
