/* HabitFlow service worker.
   - Shell pages + icons are cached so the app opens offline.
   - Only same-origin GET requests are handled. Supabase, fonts and images
     from other hosts always go straight to the network, so sign-in and cloud
     sync are never served from a stale cache.
   Bump VERSION whenever you deploy changes you want users to receive. */
const VERSION = 'hf-v7';
const SHELL = [
  '/', '/login', '/app', 'pwa.js',
  'manifest.webmanifest',
  'icons/icon-192.png', 'icons/badge-96.png', 'icons/icon-512.png', 'icons/apple-touch-icon.png'
];

self.addEventListener('install', e => {
  e.waitUntil(
    caches.open(VERSION).then(c =>
      Promise.allSettled(SHELL.map(u => c.add(new Request(u, { cache: 'reload' }))))
    ).then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== VERSION).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin !== location.origin) return;
  if (url.pathname.startsWith('/.well-known/')) return;

  // Pages: network first, so deploys show up right away; cache when offline.
  if (req.mode === 'navigate') {
    e.respondWith(
      fetch(req.url, { cache: 'no-cache' }).then(res => {
        const copy = res.clone();
        if (res.ok) caches.open(VERSION).then(c => c.put(req, copy));
        return res;
      }).catch(() =>
        caches.match(req, { ignoreSearch: true })
          .then(hit => hit || caches.match('/app') || caches.match('/login'))
      )
    );
    return;
  }

  // Everything else on this site: serve the cache, refresh it in the background.
  e.respondWith(
    caches.match(req).then(hit => {
      const net = fetch(req).then(res => {
        if (res.ok) { const copy = res.clone(); caches.open(VERSION).then(c => c.put(req, copy)); }
        return res;
      }).catch(() => hit);
      return hit || net;
    })
  );
});

// Push notifications sent by the send-reminders Edge Function
self.addEventListener('push', e => {
  let d = {};
  try { d = e.data.json(); } catch (_) { d = { body: e.data ? e.data.text() : '' }; }
  const title = d.title || 'HabitFlow', body = d.body || '', url = d.url || '/app';
  e.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then(cs => {
    const vis = cs.find(c => c.visibilityState === 'visible' && c.url.includes('/app'));
    if (vis) { vis.postMessage({ type: 'hf-reminder', title, body }); return; }
    return self.registration.showNotification(title, {
      body, tag: d.tag, renotify: !!d.tag, requireInteraction: true, vibrate: [300, 150, 300, 150, 600],
      icon: '/icons/icon-192.png', badge: '/icons/badge-96.png', data: { url, title, body }
    });
  }));
});
self.addEventListener('notificationclick', e => {
  const n = e.notification, dd = n.data || {};
  n.close();
  e.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then(cs => {
    for (const c of cs) {
      if (c.url.includes('/app') && 'focus' in c) { c.postMessage({ type: 'hf-reminder', title: dd.title, body: dd.body }); return c.focus(); }
    }
    const u = new URL(dd.url || '/app', self.location.origin);
    u.searchParams.set('r', (dd.title || '') + '|' + (dd.body || ''));
    return clients.openWindow(u.href);
  }));
});
