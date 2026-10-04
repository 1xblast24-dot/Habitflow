/* HabitFlow PWA bootstrap: manifest link, service worker, and an install button. */
(function () {
  var base = document.currentScript ? new URL('.', document.currentScript.src).href : new URL('./', location.href).href;
  var head = document.head;

  function addTag(tag, attrs) {
    var el = document.createElement(tag);
    Object.keys(attrs).forEach(function (k) { el.setAttribute(k, attrs[k]); });
    head.appendChild(el);
  }

  if (!document.querySelector('link[rel="manifest"]')) addTag('link', { rel: 'manifest', href: base + 'manifest.webmanifest' });
  if (!document.querySelector('meta[name="theme-color"]')) addTag('meta', { name: 'theme-color', content: '#4f46e5' });
  addTag('link', { rel: 'icon', type: 'image/svg+xml', href: base + 'icons/icon.svg' });
  addTag('meta', { name: 'mobile-web-app-capable', content: 'yes' });
  addTag('meta', { name: 'apple-mobile-web-app-capable', content: 'yes' });
  addTag('meta', { name: 'apple-mobile-web-app-title', content: 'HabitFlow' });

  if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () {
      navigator.serviceWorker.register(base + 'sw.js').catch(function (e) {
        console.warn('Service worker registration failed:', e);
      });
    });
  }

  var isStandalone = window.matchMedia('(display-mode: standalone)').matches || navigator.standalone === true;
  if (isStandalone) return;

  var deferred = null;
  var btn = null;

  function makeButton(label, onClick) {
    if (btn) btn.remove();
    btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = label;
    btn.setAttribute('aria-label', label);
    btn.style.cssText = 'position:fixed;right:16px;bottom:calc(16px + env(safe-area-inset-bottom,0px));z-index:2147483647;background:#4f46e5;color:#fff;border:0;border-radius:999px;padding:12px 20px;font:600 15px system-ui,-apple-system,sans-serif;box-shadow:0 6px 20px rgba(0,0,0,.3);cursor:pointer';
    btn.addEventListener('click', onClick);
    document.body.appendChild(btn);
  }

  window.addEventListener('beforeinstallprompt', function (e) {
    e.preventDefault();
    deferred = e;
    makeButton('Install app', function () {
      if (!deferred) return;
      deferred.prompt();
      deferred.userChoice.finally(function () {
        deferred = null;
        if (btn) { btn.remove(); btn = null; }
      });
    });
  });

  window.addEventListener('appinstalled', function () {
    if (btn) { btn.remove(); btn = null; }
  });

  // iOS Safari has no install prompt; show a short hint instead.
  var ua = navigator.userAgent;
  var isIOS = /iphone|ipad|ipod/i.test(ua) && !window.MSStream;
  var isSafari = /safari/i.test(ua) && !/crios|fxios|edgios/i.test(ua);
  if (isIOS && isSafari && !sessionStorage.getItem('hf-ios-hint')) {
    window.addEventListener('load', function () {
      makeButton('Add to Home Screen', function () {
        alert('Tap the Share button, then "Add to Home Screen" to install HabitFlow.');
        sessionStorage.setItem('hf-ios-hint', '1');
        if (btn) { btn.remove(); btn = null; }
      });
    });
  }
})();
