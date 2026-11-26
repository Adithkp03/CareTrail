# Community room review

Scope agreed with Adith (26 November 2026): one opt-in community room, nicknames, text-only messages, report and block, no direct messages, no patient-record sharing. Moderation by the CareTrail team through a review queue; Adith approved the reporting/removal workflow.

## What it is

- One shared room for mothers using CareTrail. Members join explicitly with a nickname and accept guidelines shown before joining. No auto-enrollment.
- Text only, 1-500 characters. Links are rejected client-side and server-side. No photos, no DMs, no threads.
- Pseudonymous: only the chosen nickname is shown. Nicknames cannot be phone-like (7+ digits), cannot be reserved words (doctor, moderator, support, team, etc.), 2-24 characters.
- Nothing from health records is readable through the community API, and community content is never written into journeys, records or the clinician surface.
- Posting rate limit (10 messages / 10 minutes) and report rate limit (20 / day). The shared demo account cannot join or post.

## Safety model (post-moderation, stated honestly)

- The room is not monitored around the clock. The app says so on screen and routes urgent worries to the Danger signs page / doctor.
- Every message carries Report (5 reasons) and Block. Blocked members' messages are hidden client-side for the blocker; blocking is private and reversible.
- Reports go to a team review queue (`/moderation/app`, token-authenticated, no patient or clinician session). A reviewer can dismiss, remove a message, or remove and suspend the author from posting. Every action is audit-logged. Suspended members can still read.
- Guidelines and moderation approach follow published online peer-support principles (JMIR "Keeping Online Peer Support Safe": anonymity, published rules, honest limits, flag-to-queue review; Light Collective moderation best practices).

## Data and privacy

- Tables: community_members, community_messages, community_reports, community_blocks, community_moderators (auto-created by ensure_tables; migration 0002 documents the manual provisioning path).
- Messages are served Cache-Control: no-store and are not cached on device.
- Account export includes community membership, messages, reports made and blocks. Account deletion anonymizes posted messages (body and nickname blanked) and deletes membership, reports made and blocks.
- Moderator tokens are separate server-issued credentials (`python -m app.moderation_cli create <name>`), shown once, ctmod- prefixed. No patient or clinician account gets moderation powers.

## Limits

- One room only: no topics, no groups, no private messaging, no rich media.
- Post-moderation means a harmful message is visible until reviewed; the review queue has no SLA and the team is not yet staffed around the clock.
- Nickname privacy is pseudonymity, not anonymity: the server links messages to accounts for moderation and deletion; members should still not share identifying details.
- The review queue page is a minimal operational tool, not a staffed moderation service.
- Real-patient launch stays off until chat and moderation tests are verified.

## Tests

- Backend: backend/tests/test_community.py (8 tests): join validation, membership and link rules, demo refusal, full report-to-removal-and-suspension flow, dismiss keeps message, block/unblock, own-delete plus export and account-deletion anonymization, post rate limit.
- Android: CommunityTest (nickname/body rules, pinned safety copy, payload parsing), CommunityScreenshotTest (join card, chat with blocked masking, report sheet, suspended read-only view).
