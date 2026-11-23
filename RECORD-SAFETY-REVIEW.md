# Record safety review, phase 1

Based only on main 53ccb7625014b551bcd812ba70c689c2ebb4ad73. Does not include pending launcher/loading/Listen/Google UX PRs 56-59 or clinical PR 61. No merge or deployment is approved by this branch.

- Birth form restores raw unfinished text from an explicit local draft. Drafts do not switch postpartum mode. Each edit uses checked local commit; no network is used for drafts.
- Confirmed birth, optional initial growth row and draft removal use one checked preferences edit, retaining existing s2 keys. Existing zero-month growth rows are not overwritten or duplicated.
- Logout has an explicit local-data-loss warning and cancel path. Confirming logout still clears all local caches/drafts, to preserve the existing account boundary. This does not add cross-account retention.
- Home groups existing pregnancy or mother/baby entry points; no clinical pathway or schedule is added.
- Vault/data-safety copy distinguishes local entries, server export and selected referral summary. No cloud-sync, universal backup or restore claim.

Limits: only birth-form drafts in this phase, not all forms. Checked SharedPreferences writes/reopening tests are not proof against arbitrary physical device failure, encryption-key loss, uninstall, app-data clear or new-phone loss. No cloud sync, restore, neonatal rules, government API, podcast billing or real patient data. Other doctor points remain separate staged work, including source-linked listening and official scheme guidance.

Tests use only synthetic fixtures. Screen checks render real screen components with an injected offline synthetic journey, not full account sign-in or a physical power cut. Device-key encryption remains existing opt-in behavior, not a new certification.
