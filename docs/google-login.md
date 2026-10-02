# Google identity login

Google is an optional identity provider through Supabase Auth. Phone/password and demo login remain available. The browser uses only Supabase Auth, then exchanges the verified Supabase access token at `POST /auth/supabase` for a normal CareTrail session. It never reads patient tables directly.

## Configuration before enabling

1. Configure the Google provider in Supabase and its Google Cloud OAuth consent screen/client. Request only the standard identity scopes, not Gmail, Drive or Calendar access.
2. Add the Supabase Auth callback URI shown in its provider settings as an authorized Google redirect URI.
3. In Supabase Auth URL Configuration, set the deployed frontend origin as Site URL and allow its exact `/login` callback. Add localhost only for development.
4. Frontend build-time variables: `NEXT_PUBLIC_SUPABASE_URL`, `NEXT_PUBLIC_SUPABASE_ANON_KEY` (public client key), and `NEXT_PUBLIC_GOOGLE_AUTH_ENABLED=true`. Never place a service-role key, client secret or JWT signing secret in frontend variables. Leave the Google flag false until setup is tested.
5. Backend: set `SUPABASE_URL` to the trusted HTTPS project origin. ES256/RS256 signatures are checked against that origin's cached JWKS and exact issuer. Legacy HS256 needs `SUPABASE_JWT_SECRET`; when project URL is configured its issuer is checked too. Install backend dependencies including PyJWT's crypto extra.
6. Deploy backend verifier before enabling the frontend button. Test on the final frontend domain with a real Google test account.

## UX and account boundaries

- Identity login requests the existing CareTrail consent before starting. Pending consent lives only in the browser tab for 30 minutes. A callback opened in a fresh tab/device still works: returning patients need no new consent; new patients must confirm before their profile is created.
- OAuth cancellation and unavailable provider/exchange are retryable, with phone/password fallback. Supabase session exchange is separate from its auth callback lock. Successful exchange signs out only this browser's Supabase session, keeping CareTrail's own session.
- Profiles map by verified Supabase subject, never by matching an email. An existing phone/password profile is not automatically linked to a Google identity. Its journey remains under phone/password login. This is explained next to the Google button.
- A verified identity phone colliding with an existing profile is rejected with a 409, not silently linked. Account linking is a separate feature requiring proof of both accounts.
- Email magic-link login is retained. Supabase may link supported identity providers within its own verified user record according to its settings; CareTrail always uses the resulting verified subject.

## Checks

Local production builds with provider config disabled and enabled pass. Backend regression tests cover provisioning, consent, repeated identity login, legacy login, no email auto-link, phone collision, expired/missing-expiry tokens, issuer mismatch, ES256 JWKS and key-fetch outage. Local browser tests with synthetic provider responses cover both widths, consent guard, cancelled OAuth cleanup, new-tab consent and retryable exchange failure. These are not evidence of a successful live Google OAuth round trip. Before release verify first/returning Google login, callback on final domain, consent, existing phone/password access, and logout/re-login with a real configured provider.

Official setup references:
- https://supabase.com/docs/guides/auth/social-login/auth-google
- https://supabase.com/docs/guides/auth/signing-keys

To run the optional local browser regression test, install Playwright without saving it to dependencies, use Chrome at `/usr/bin/google-chrome`, run the synthetic demo API on port 8000, and serve the exported frontend on port 3000 with extensionless routes mapped to `.html`. Build using `NEXT_PUBLIC_SUPABASE_URL=https://project.example.test`, `NEXT_PUBLIC_SUPABASE_ANON_KEY=local-public-test-key`, `NEXT_PUBLIC_GOOGLE_AUTH_ENABLED=true`. Then run `node tests/google-auth.visual.cjs` from `frontend`. Provider replies are mocked; the test does not contact Google or mutate any real Supabase user.
