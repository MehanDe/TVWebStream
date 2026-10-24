(function () {
  if (window.__tvNav) return;

  var SEL = 'a[href],button,input:not([type=hidden]),select,textarea,summary,' +
            '[tabindex]:not([tabindex="-1"]),[role=button],[role=link],[role=tab],' +
            '[role=menuitem],[role=option],[role=checkbox],[role=switch],[onclick]';
  var STRONG = /^(A|BUTTON|INPUT|SELECT|TEXTAREA|SUMMARY)$/;
  var cur = null;
  var ring = null;
  var stamp = 0;

  function rootEl() {
    return document.fullscreenElement || document.webkitFullscreenElement || document;
  }
  function hostEl() {
    return document.fullscreenElement || document.webkitFullscreenElement ||
           document.body || document.documentElement;
  }

  function ensureRing() {
    if (!ring) {
      ring = document.createElement('div');
      ring.id = '__tv_ring';
      ring.style.cssText = 'position:fixed;z-index:2147483647;pointer-events:none;' +
        'border:4px solid #ffd400;border-radius:8px;box-sizing:border-box;display:none;' +
        'box-shadow:0 0 0 2px rgba(0,0,0,.85),0 0 18px 4px rgba(255,212,0,.65);transition:none;';
    }
    var h = hostEl();
    if (ring.parentNode !== h) h.appendChild(ring);
    return ring;
  }

  function drawRing() {
    if (!cur) return;
    var rg = ensureRing();
    if (!cur.isConnected) { rg.style.display = 'none'; return; }
    var r = cur.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) { rg.style.display = 'none'; return; }
    rg.style.display = 'block';
    rg.style.left = (r.left - 4) + 'px';
    rg.style.top = (r.top - 4) + 'px';
    rg.style.width = (r.width + 8) + 'px';
    rg.style.height = (r.height + 8) + 'px';
  }

  function inView(r) {
    return !(r.bottom < 0 || r.right < 0 || r.top > window.innerHeight || r.left > window.innerWidth);
  }

  function hit(el, r) {
    var x = Math.min(Math.max((r.left + r.right) / 2, 1), window.innerWidth - 1);
    var y = Math.min(Math.max((r.top + r.bottom) / 2, 1), window.innerHeight - 1);
    x = Math.min(Math.max(x, r.left + 1), r.right - 1);
    y = Math.min(Math.max(y, r.top + 1), r.bottom - 1);
    var t = document.elementFromPoint(x, y);
    return !!t && (t === el || el.contains(t) || t.contains(el));
  }

  function usable(el, r) {
    if (r.width < 4 || r.height < 4 || !inView(r)) return false;
    if (el.disabled || el.id === '__tv_ring') return false;
    var cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none' || cs.pointerEvents === 'none') return false;
    if (parseFloat(cs.opacity) === 0) return false;
    if (!STRONG.test(el.tagName) &&
        r.width > window.innerWidth * 0.85 && r.height > window.innerHeight * 0.85) return false;
    return true;
  }

  function gather() {
    stamp++;
    var out = [], r0 = rootEl(), i, el, r, list;

    list = r0.querySelectorAll(SEL);
    for (i = 0; i < list.length; i++) {
      el = list[i];
      r = el.getBoundingClientRect();
      if (!usable(el, r) || !hit(el, r)) continue;
      el.__tvS = stamp;
      out.push({ el: el, r: r });
    }

    // Things that look clickable (cursor:pointer). Keep only the outermost one.
    list = r0.querySelectorAll('div,span,li,img,svg,i,label,p,section,article,figure');
    var n = Math.min(list.length, 6000);
    for (i = 0; i < n; i++) {
      el = list[i];
      if (el.__tvS === stamp) continue;
      r = el.getBoundingClientRect();
      if (r.width < 8 || r.height < 8 || !inView(r)) continue;
      var cs = getComputedStyle(el);
      if (cs.cursor !== 'pointer') continue;
      var p = el.parentElement;
      if (p && getComputedStyle(p).cursor === 'pointer') continue;
      if (!usable(el, r) || !hit(el, r)) continue;
      el.__tvS = stamp;
      out.push({ el: el, r: r });
    }
    return out;
  }

  function center(r) { return { x: (r.left + r.right) / 2, y: (r.top + r.bottom) / 2 }; }

  function pick(dir, from, cands) {
    var best = null, bs = Infinity, fr = from.r, fc = center(fr), i, c, cc, prim, sec, s;
    for (i = 0; i < cands.length; i++) {
      c = cands[i];
      if (c.el === from.el || c.el.contains(from.el) || from.el.contains(c.el)) continue;
      cc = center(c.r);
      if (dir === 'right') {
        if (cc.x <= fc.x + 2) continue;
        prim = Math.max(0, c.r.left - fr.right); sec = Math.abs(cc.y - fc.y);
      } else if (dir === 'left') {
        if (cc.x >= fc.x - 2) continue;
        prim = Math.max(0, fr.left - c.r.right); sec = Math.abs(cc.y - fc.y);
      } else if (dir === 'down') {
        if (cc.y <= fc.y + 2) continue;
        prim = Math.max(0, c.r.top - fr.bottom); sec = Math.abs(cc.x - fc.x);
      } else {
        if (cc.y >= fc.y - 2) continue;
        prim = Math.max(0, fr.top - c.r.bottom); sec = Math.abs(cc.x - fc.x);
      }
      s = prim + sec * 2;
      if (s < bs) { bs = s; best = c; }
    }
    return best;
  }

  function wake(el) {
    try {
      var r = el.getBoundingClientRect();
      var o = { bubbles: true, cancelable: true, view: window,
                clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 };
      el.dispatchEvent(new MouseEvent('mouseover', o));
      el.dispatchEvent(new MouseEvent('mousemove', o));
    } catch (e) {}
  }

  function setCur(el) {
    cur = el;
    try { if (el.focus) el.focus({ preventScroll: true }); } catch (e) {}
    try { el.scrollIntoView({ block: 'nearest', inline: 'nearest' }); } catch (e) {}
    wake(el);
    drawRing();
  }

  function canScroll(e, dx, dy) {
    var cs = getComputedStyle(e);
    if (dy > 0 && /(auto|scroll)/.test(cs.overflowY) && e.scrollTop + e.clientHeight < e.scrollHeight - 1) return true;
    if (dy < 0 && /(auto|scroll)/.test(cs.overflowY) && e.scrollTop > 0) return true;
    if (dx > 0 && /(auto|scroll)/.test(cs.overflowX) && e.scrollLeft + e.clientWidth < e.scrollWidth - 1) return true;
    if (dx < 0 && /(auto|scroll)/.test(cs.overflowX) && e.scrollLeft > 0) return true;
    return false;
  }

  function doScroll(target, dx, dy) {
    try { target.scrollBy({ left: dx, top: dy, behavior: 'instant' }); }
    catch (e) { target.scrollBy(dx, dy); }
  }

  function scrollDir(el, dir) {
    var e = el, dx, dy;
    while (e && e !== document.documentElement) {
      dx = 0; dy = 0;
      if (dir === 'down') dy = e.clientHeight * 0.8;
      else if (dir === 'up') dy = -e.clientHeight * 0.8;
      else if (dir === 'right') dx = e.clientWidth * 0.8;
      else dx = -e.clientWidth * 0.8;
      if (canScroll(e, dx, dy)) { doScroll(e, dx, dy); return true; }
      e = e.parentElement;
    }
    dx = 0; dy = 0;
    if (dir === 'down') dy = window.innerHeight * 0.8;
    else if (dir === 'up') dy = -window.innerHeight * 0.8;
    else if (dir === 'right') dx = window.innerWidth * 0.8;
    else dx = -window.innerWidth * 0.8;
    var de = document.scrollingElement || document.documentElement;
    var can = (dy > 0 && window.pageYOffset + window.innerHeight < de.scrollHeight - 1) ||
              (dy < 0 && window.pageYOffset > 0) ||
              (dx > 0 && window.pageXOffset + window.innerWidth < de.scrollWidth - 1) ||
              (dx < 0 && window.pageXOffset > 0);
    if (can) { doScroll(window, dx, dy); return true; }
    return false;
  }

  function nearestToCenter(cands) {
    var best = null, bd = Infinity, i, c, cc, d;
    var mx = window.innerWidth / 2, my = window.innerHeight / 2;
    for (i = 0; i < cands.length; i++) {
      c = cands[i]; cc = center(c.r);
      d = Math.abs(cc.x - mx) + Math.abs(cc.y - my);
      if (d < bd) { bd = d; best = c; }
    }
    return best;
  }

  window.__tvNav = function (dir, nativeW) {
    if (cur && !cur.isConnected) cur = null;
    var cands = gather();
    var nxt;

    if (!cur || dir === 'start') {
      if (cur) { drawRing(); return 'ring'; }
      nxt = nearestToCenter(cands);
      if (nxt) { setCur(nxt.el); return 'start'; }
      if (dir !== 'start') scrollDir(document.body, dir);
      return 'none';
    }

    var from = { el: cur, r: cur.getBoundingClientRect() };
    nxt = pick(dir, from, cands);
    if (nxt) { setCur(nxt.el); return 'move'; }

    if (scrollDir(cur, dir)) {
      cands = gather();
      from = { el: cur, r: cur.getBoundingClientRect() };
      nxt = pick(dir, from, cands);
      if (nxt) setCur(nxt.el); else drawRing();
      return 'scroll';
    }
    wake(cur);
    return 'edge';
  };

  // Returns the pixel position (in app screen pixels) to tap for the highlighted item.
  window.__tvSel = function (nativeW) {
    if (!cur || !cur.isConnected) return null;
    var r = cur.getBoundingClientRect();
    var k = nativeW / window.innerWidth;
    var x = Math.min(Math.max((r.left + r.right) / 2, 1), window.innerWidth - 1);
    var y = Math.min(Math.max((r.top + r.bottom) / 2, 1), window.innerHeight - 1);
    x = Math.min(Math.max(x, r.left + 1), r.right - 1);
    y = Math.min(Math.max(y, r.top + 1), r.bottom - 1);
    return [x * k, y * k];
  };

  window.__tvOff = function () {
    cur = null;
    if (ring) ring.style.display = 'none';
  };

  window.addEventListener('scroll', drawRing, true);
  window.addEventListener('resize', drawRing);
  setInterval(drawRing, 400);
})();
