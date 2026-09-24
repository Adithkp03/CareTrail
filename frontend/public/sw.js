// CareTrail offline fallback (Phase 7): static assets cache-first, pages and
// read-only API GETs network-first with cache backup, so a slow venue network
// cannot kill the demo.
const STATIC_CACHE = "caretrail-static-v1";
const RUNTIME_CACHE = "caretrail-runtime-v1";

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(STATIC_CACHE).then((c) => c.addAll(["/", "/icon.svg", "/manifest.webmanifest"])));
  self.skipWaiting();
});

self.addEventListener("activate", (e) => {
  e.waitUntil(self.clients.claim());
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);
  if (e.request.method !== "GET") return;

  if (url.pathname.startsWith("/_next/static") || url.pathname === "/icon.svg") {
    e.respondWith(
      caches.match(e.request).then((hit) => hit || fetch(e.request).then((res) => {
        const copy = res.clone();
        caches.open(STATIC_CACHE).then((c) => c.put(e.request, copy));
        return res;
      }))
    );
    return;
  }

  // API GETs (journey, explanations, flags): fresh when possible, cached when offline.
  const isApi = !url.pathname.startsWith("/_next") && (url.pathname.startsWith("/journey") || url.pathname.startsWith("/journeys") || url.pathname.startsWith("/milestones") || url.pathname.startsWith("/flags") || url.hostname !== self.location.hostname);
  if (isApi) {
    e.respondWith(
      fetch(e.request).then((res) => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(RUNTIME_CACHE).then((c) => c.put(e.request, copy));
        }
        return res;
      }).catch(() => caches.match(e.request).then((hit) => hit || new Response(JSON.stringify({ detail: "offline" }), { status: 503, headers: { "Content-Type": "application/json" } })))
    );
  }
});
