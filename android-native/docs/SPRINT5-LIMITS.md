# Sprint 5: referral portability and privacy foundations

## Implemented

- Patient prepares a local, checksum-protected CareTrail JSON summary, reviews it (including the entire file contents), acknowledges export, and chooses a destination using Android's document picker. Nothing is automatically sent to another person or service.
- Received JSON can be previewed read-only. Wrong type/version, corruption and files over 1 MB are rejected. No import can overwrite a journey.
- Local image paths and all mood data are excluded from referral output. Original reports are not included. Partner test-status fields in registration are included and disclosed before export.
- Optional ABHA number/address fields are references supplied by the patient. A length warning is not identity verification.
- An explicit Data safety action migrates the cache and bearer token to AES-256-GCM using an Android Keystore key. The migration builds all encrypted values before committing. Encryption failure does not write plaintext as a fallback. Existing plaintext remains until the user enables this action.
- Assamese is selectable with 87 bundled static labels. Marathi has all 87 labels including consent. These translations are drafts, not clinician/native-speaker-reviewed text. Dynamic clinical text still needs translation availability and review.

## Not implemented / do not claim

- ABDM registration, ABHA verification, HIP/HIU/PHR integration, FHIR profile conformance, HIE-CM consent artefacts, certification or legal compliance.
- This is not end-to-end encryption. Original image/report files, cloud records, exported JSON, movement counts and translation caches are outside the local-cache encryption change. The export is plaintext and has no revocation once copied.
- SHA-256 detects accidental corruption; anyone who changes a file can recompute it. It proves neither sender identity nor clinical correctness. The package is not a signed clinician referral, and does not establish that a receiving facility accepted a transfer.
- Backend clinical-record sync is still pending. No automatic upload of locally held mood or other records has been added. IDs, conflict handling, retention, export/delete integration and opt-in scope must be designed and tested before sync can safely clear pending flags.
- Do not use real patient data until security and clinical review. Device-key loss can make encrypted cache unreadable. Keep original clinical documents safely elsewhere; do not clear app data to fix a key error.

## Sources checked

- https://docs.abdm.gov.in/docs/hiecm/v3 : ABDM exchanges data between participating systems; it is not a central health-record store. Integration needs sandbox credentials and the applicable milestones.
- https://docs.abdm.gov.in/docs/hiecm/v3/concepts/consent : actual ABDM sharing requires the consent workflow and active artefacts, including expiry/revocation handling. A local checkbox or ABHA field is not that workflow.

## Validation status

Assemble/unit-test results are in the PR. No Android device or emulator was connected in the build environment, so visual layout, document-picker behavior and actual Android Keystore round-trip still need device testing. The PR is draft until those checks are done.

## Manual checks

Use synthetic/demo data only.
1. Record a health profile, birth and BP. Home > Referral transfer > Prepare. Check summary and complete JSON. Confirm no mood data or private file paths.
2. Acknowledge export, save JSON to a chosen folder, reopen via Preview. Confirm matching summary and read-only notice. Change a field without updating checksum: preview rejects it. Cancel either picker: no writes.
3. Health profile: ABHA is optional and labelled unverified; short number triggers a warning.
4. Data safety: enable device-key encryption, restart app, check existing timeline/cache/token still read. In debug app storage, cache/token should have enc1 prefix and no visible clinical plaintext. Disable network and read saved data. Simulate Keystore failure to confirm migration leaves existing data intact. Key-loss behavior needs careful recovery testing.
5. Pick Assamese and Marathi; verify consent and static labels wrap/read correctly. Check that medical-review warning still appears. Have native-speaker clinicians review translations before marking either reviewed.


## Sprint 4 SOS clarification

Selecting the self-harm item shows help contacts immediately. Tapping a contact opens the phone dialer; the user must place the call. No call or message is sent automatically. This is tap-to-dial routing, not zero-click contact with a helpline.

## Emulator attempt, 2026-10-27

Installed Android Emulator 37.2.12 and Android 28 x86_64 image using the free Android SDK. This environment has no /dev/kvm, so the emulator used software CPU emulation. Android booted and the APK installed and launched, but further UI operations were unstable: ADB dropped offline, package installs took long enough to time out, and actual screenshot inspection showed a System UI not responding dialog. No referral picker or Keystore migration pass is claimed. A synthetic instrumentation harness was prepared locally but did not return a completed result. Device validation remains outstanding. This is an environment blocker, not evidence that the app itself passed or failed those checks.
