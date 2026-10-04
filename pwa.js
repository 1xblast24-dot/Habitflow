/* HabitFlow PWA helper.
   1) Registers the service worker (every page that includes this file).
   2) On the sign-in page, adds a "Get HabitFlow on your phone" card under the form.
   Optional settings, set before this script loads:
     window.HF_PWA = { apk: 'downloads/habitflow.apk', icon: 'icons/icon-192.png', card: true };
   Set apk to '' if you don't host an APK file. */
(function () {
  'use strict';
  var CFG = Object.assign({ apk: 'downloads/habitflow.apk', icon: 'icons/icon-192.png', card: true, sw: true }, window.HF_PWA || {});
  var ua = navigator.userAgent;
  var isIOS = /iPad|iPhone|iPod/.test(ua) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  var isAndroid = /Android/i.test(ua);
  var standalone = (window.matchMedia && matchMedia('(display-mode: standalone)').matches) || navigator.standalone === true;
  var deferred = null, apkOk = false, root = null;

  /* ---- service worker ---- */
  if (CFG.sw && 'serviceWorker' in navigator && /^https?:$/.test(location.protocol)) {
    addEventListener('load', function () { navigator.serviceWorker.register('sw.js').catch(function () {}); });
  }

  /* ---- install prompt (Chrome / Edge / Samsung Internet) ---- */
  addEventListener('beforeinstallprompt', function (e) { e.preventDefault(); deferred = e; paint(); });
  addEventListener('appinstalled', function () { deferred = null; installed(); });

  /* ---- card ---- */
  var slot = document.getElementById('pwa-slot');
  var onLogin = /login(\.html)?\/?$/.test(location.pathname);
  if (!CFG.card || standalone || !(slot || onLogin)) return;

  var ICON_DL = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4v11m0 0-4-4m4 4 4-4M5 19h14"/></svg>';
  var ICON_SHARE = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 15V4m0 0L8.5 7.5M12 4l3.5 3.5M7 11H6a1.5 1.5 0 0 0-1.5 1.5v6A1.5 1.5 0 0 0 6 20h12a1.5 1.5 0 0 0 1.5-1.5v-6A1.5 1.5 0 0 0 18 11h-1"/></svg>';

  var css = '' +
    'main{align-content:center;gap:18px}' +
    '.hfi{width:min(440px,100%);padding:20px 22px 18px;border-radius:28px;background:rgba(12,19,31,.58);-webkit-backdrop-filter:blur(30px) saturate(180%);backdrop-filter:blur(30px) saturate(180%);box-shadow:inset 0 0 0 1px rgba(255,255,255,.12),0 24px 70px rgba(0,0,0,.35);color:#f5f5fa;font:15px/1.45 -apple-system,BlinkMacSystemFont,"SF Pro Text",Inter,system-ui,sans-serif;animation:hfiIn .6s .25s cubic-bezier(.32,.72,0,1) backwards}' +
    '@keyframes hfiIn{from{opacity:0;transform:translateY(14px)}}' +
    '.hfi-top{display:flex;gap:14px;align-items:center}' +
    '.hfi-ic{width:56px;height:56px;border-radius:14px;flex:none;box-shadow:0 8px 22px rgba(20,200,168,.22),0 0 0 1px rgba(255,255,255,.14)}' +
    '.hfi h2{margin:0;font-size:17px;line-height:1.2;letter-spacing:-.02em;font-weight:650}' +
    '.hfi p{margin:3px 0 0;font-size:13.5px;color:rgba(226,228,244,.74)}' +
    '.hfi-bt{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-top:16px}' +
    '.hfi-b{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:46px;padding:0 12px;border:0;border-radius:23px;font:600 14px/1 inherit;letter-spacing:-.01em;color:#fff;cursor:pointer;background:rgba(255,255,255,.1);box-shadow:inset 0 0 0 1px rgba(255,255,255,.16);transition:transform .3s cubic-bezier(.34,1.56,.64,1),background .2s}' +
    '.hfi-b:hover{background:rgba(255,255,255,.17)}.hfi-b:active{transform:scale(.96)}' +
    '.hfi-b.pri{background:linear-gradient(135deg,#14c8a8,#5b5bf0);box-shadow:0 8px 24px rgba(91,91,240,.35),inset 0 0 0 1px rgba(255,255,255,.22)}' +
    '.hfi-b:focus-visible,.hfi-x:focus-visible,.hfi-a:focus-visible{outline:2px solid #8c8cff;outline-offset:3px}' +
    '.hfi-b svg{width:18px;height:18px;fill:none;stroke:currentColor;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round;flex:none}' +
    '.hfi-more{display:flex;justify-content:center;gap:18px;flex-wrap:wrap;margin-top:12px}' +
    '.hfi-a{background:none;border:0;padding:4px 2px;font:500 13px/1.2 inherit;color:rgba(226,228,244,.74);text-decoration:underline;text-underline-offset:3px;cursor:pointer}' +
    '.hfi-a:hover{color:#fff}' +
    '.hfi-ok{display:flex;gap:12px;align-items:center}.hfi-ok b{font-weight:650}' +
    'dialog.hfi-sh{border:0;padding:0;background:none;color:#f5f5fa;width:min(440px,100%);max-width:100%;margin:auto auto 0}' +
    'dialog.hfi-sh::backdrop{background:rgba(3,7,14,.62);-webkit-backdrop-filter:blur(6px);backdrop-filter:blur(6px)}' +
    '.hfi-pn{padding:22px 22px calc(22px + env(safe-area-inset-bottom,0px));border-radius:30px 30px 0 0;background:rgba(14,22,36,.94);box-shadow:inset 0 0 0 1px rgba(255,255,255,.14);font:15px/1.45 -apple-system,BlinkMacSystemFont,"SF Pro Text",Inter,system-ui,sans-serif}' +
    '@media (min-width:560px){dialog.hfi-sh{margin:auto}.hfi-pn{border-radius:30px}}' +
    '.hfi-hd{display:flex;justify-content:space-between;align-items:flex-start;gap:12px}' +
    '.hfi-pn h3{margin:0;font-size:20px;line-height:1.2;letter-spacing:-.03em;font-weight:700}' +
    '.hfi-x{flex:none;width:36px;height:36px;border:0;border-radius:50%;background:rgba(255,255,255,.1);color:#fff;font-size:20px;line-height:1;cursor:pointer}' +
    '.hfi-pn ol{margin:16px 0 0;padding:0;list-style:none;counter-reset:s}' +
    '.hfi-pn li{counter-increment:s;display:flex;gap:12px;align-items:flex-start;padding:10px 0;color:rgba(236,240,250,.9)}' +
    '.hfi-pn li::before{content:counter(s);flex:none;width:26px;height:26px;border-radius:50%;display:grid;place-items:center;font-size:13px;font-weight:650;background:rgba(140,140,255,.22);color:#c9c9ff}' +
    '.hfi-pn li svg{width:18px;height:18px;vertical-align:-3px;fill:none;stroke:#c9c9ff;stroke-width:1.9;stroke-linecap:round;stroke-linejoin:round}' +
    '.hfi-note{margin:10px 0 0;font-size:13px;color:rgba(226,228,244,.62)}' +
    '@media (prefers-reduced-motion:reduce){.hfi{animation:none}.hfi-b{transition:none}}';

  var st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);

  root = document.createElement('section');
  root.className = 'hfi';
  root.setAttribute('aria-labelledby', 'hfi-t');

  var host = slot || document.querySelector('main');
  if (!host) return;
  host.appendChild(root);

  if (CFG.apk) {
    fetch(CFG.apk, { method: 'HEAD' }).then(function (r) {
      apkOk = r.ok && !/text\/html/.test(r.headers.get('content-type') || ''); paint();
    }).catch(function () {});
  }

  function paint() {
    if (!root || root.dataset.done) return;
    var androidFirst = !isIOS;
    var aLabel = deferred ? 'Install for Android' : (apkOk ? 'Download for Android' : 'Get for Android');
    var bA = '<button class="hfi-b' + (androidFirst ? ' pri' : '') + '" data-a="android">' + ICON_DL + '<span>' + aLabel + '</span></button>';
    var bI = '<button class="hfi-b' + (isIOS ? ' pri' : '') + '" data-a="ios">' + ICON_SHARE + '<span>Add to iPhone</span></button>';
    var more = '';
    if (deferred && !isAndroid && !isIOS) more += '<button class="hfi-a" data-a="desktop">Install on this computer</button>';
    if (apkOk && deferred) more += '<a class="hfi-a" href="' + CFG.apk + '" download>Download the APK file</a>';
    root.innerHTML =
      '<div class="hfi-top"><img class="hfi-ic" src="' + CFG.icon + '" alt="" width="56" height="56">' +
      '<div><h2 id="hfi-t">Get HabitFlow on your phone</h2><p>Opens full screen, works offline, and keeps your habits in sync.</p></div></div>' +
      '<div class="hfi-bt">' + (androidFirst ? bA + bI : bI + bA) + '</div>' +
      (more ? '<div class="hfi-more">' + more + '</div>' : '');
  }

  function installed() {
    if (!root) return;
    root.dataset.done = '1';
    root.innerHTML = '<div class="hfi-ok"><img class="hfi-ic" src="' + CFG.icon + '" alt="" width="56" height="56"><div><b>HabitFlow is installed</b><p>Open it from your home screen.</p></div></div>';
  }

  function sheet(kind) {
    var d = document.createElement('dialog');
    d.className = 'hfi-sh';
    var steps, title, note = '';
    if (kind === 'ios') {
      title = 'Add HabitFlow to your iPhone';
      steps = ['Open this page in <b>Safari</b>.',
               'Tap the <b>Share</b> button ' + ICON_SHARE + ' in the toolbar.',
               'Scroll down and tap <b>Add to Home Screen</b>.',
               'Tap <b>Add</b>. HabitFlow now sits on your home screen with its own icon.'];
      note = 'iPhone and iPad install web apps this way. There is no download file.';
    } else {
      title = 'Install HabitFlow on Android';
      steps = ['Open this page in <b>Chrome</b>.',
               'Tap the <b>⋮</b> menu in the top right.',
               'Tap <b>Install app</b> (or <b>Add to Home screen</b>).',
               'Tap <b>Install</b>. HabitFlow appears in your app drawer.'];
      if (apkOk) note = 'Prefer a file? <a class="hfi-a" href="' + CFG.apk + '" download>Download the APK</a>. Android may ask you to allow installs from your browser.';
    }
    d.innerHTML = '<div class="hfi-pn"><div class="hfi-hd"><h3>' + title + '</h3><button class="hfi-x" aria-label="Close">×</button></div><ol>' +
      steps.map(function (s) { return '<li><span>' + s + '</span></li>'; }).join('') + '</ol>' + (note ? '<p class="hfi-note">' + note + '</p>' : '') + '</div>';
    document.body.appendChild(d);
    d.addEventListener('click', function (e) { if (e.target === d || e.target.closest('.hfi-x')) d.close(); });
    d.addEventListener('close', function () { d.remove(); });
    if (d.showModal) d.showModal(); else d.setAttribute('open', '');
  }

  function prompt() {
    var p = deferred; deferred = null; paint();
    p.prompt();
    return p.userChoice;
  }

  root.addEventListener('click', function (e) {
    var b = e.target.closest('[data-a]'); if (!b) return;
    var a = b.dataset.a;
    if (a === 'ios') return sheet('ios');
    if (a === 'desktop') return prompt();
    if (a === 'android') {
      if (deferred) return prompt();
      if (apkOk) { var l = document.createElement('a'); l.href = CFG.apk; l.download = ''; document.body.appendChild(l); l.click(); l.remove(); return; }
      sheet('android');
    }
  });

  paint();
})();
