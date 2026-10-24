(function () {
  if (window.__tvNavInstalled) return;
  window.__tvNavInstalled = true;

  var cur = null, box = null;
  var SEL = 'a[href],button,input:not([type=hidden]):not([type=range]),select,textarea,summary,' +
            '[role=button],[role=link],[role=tab],[role=menuitem],[role=option],[role=switch],' +
            '[role=checkbox],[tabindex]:not([tabindex="-1"]),[onclick]';

  function vw() { return window.innerWidth; }
  function vh() { return window.innerHeight; }
  function R(el) { return el.getBoundingClientRect(); }
  function C(r) { return { x: r.left + r.width / 2, y: r.top + r.height / 2 }; }
  function alive(el) { return el && document.documentElement.contains(el); }

  function usable(el) {
    if (el === box || (box && box.contains(el))) return false;
    if (el.disabled) return false;
    var r = R(el);
    if (r.width < 6 || r.height < 6) return false;
    var c = C(r);
    if (c.x < 0 || c.x > vw()) return false;                            // off-canvas side menus
    if (r.width > vw() * 0.6 && r.height < 26) return false;            // seek bars / sliders
    if (r.width > vw() * 0.95 && r.height > vh() * 0.9) return false;   // full-screen wrappers
    var cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none') return false;
    if (c.y >= 0 && c.y <= vh()) {
      var t = document.elementFromPoint(c.x, c.y);
      if (!t || !(t === el || el.contains(t))) return false;
    }
    return true;
  }

  function ptr(el) { return getComputedStyle(el).cursor === 'pointer'; }
  function ptrRoot(e) {
    if (!ptr(e)) return null;
    var c = e, n = 0;
    while (c.parentElement && n < 8 && ptr(c.parentElement)) { c = c.parentElement; n++; }
    return c;
  }

  function candidates() {
    var out = [], i, j, list = document.querySelectorAll(SEL);
    for (i = 0; i < list.length; i++) { if (usable(list[i])) out.push(list[i]); }

    var sx = Math.max(20, vw() / 48), sy = Math.max(20, vh() / 27), x, y, e, c, last = null;
    for (y = sy / 2; y < vh(); y += sy) {
      for (x = sx / 2; x < vw(); x += sx) {
        e = document.elementFromPoint(x, y);
        if (!e || e === last || e === document.documentElement || e === document.body) continue;
        last = e;
        c = ptrRoot(e);
        if (c && out.indexOf(c) < 0 && usable(c)) out.push(c);
      }
    }

    var res = [];
    for (i = 0; i < out.length; i++) {
      var keep = true;
      for (j = 0; j < out.length; j++) {
        if (i !== j && out[i].contains(out[j])) { keep = false; break; }
      }
      if (keep) res.push(out[i]);
    }
    return res;
  }

  function draw() {
    if (!alive(cur)) { if (box) box.style.display = 'none'; return; }
    if (!box) {
      box = document.createElement('div');
      box.style.cssText = 'position:fixed;pointer-events:none;z-index:2147483647;' +
        'border:4px solid #fff;box-shadow:0 0 0 3px #000,0 0 18px 6px rgba(0,0,0,.6);' +
        'border-radius:8px;box-sizing:border-box;transition:none;display:none;';
    }
    var fs = document.fullscreenElement || document.webkitFullscreenElement;
    var host = (fs && fs.tagName !== 'VIDEO') ? fs : document.documentElement;
    if (box.parentNode !== host) host.appendChild(box);
    var r = R(cur);
    box.style.left = (r.left - 3) + 'px';
    box.style.top = (r.top - 3) + 'px';
    box.style.width = (r.width + 6) + 'px';
    box.style.height = (r.height + 6) + 'px';
    box.style.display = 'block';
  }

  function wake(el) {
    try {
      var t = el || document.body, r = R(t);
      var o = { bubbles: true, clientX: r.left + r.width / 2, clientY: r.top + r.height / 2 };
      t.dispatchEvent(new MouseEvent('mouseover', o));
      t.dispatchEvent(new MouseEvent('mousemove', o));
      t.dispatchEvent(new MouseEvent('mouseenter', { bubbles: false, clientX: o.clientX, clientY: o.clientY }));
    } catch (err) {}
  }

  function setCur(el) {
    cur = el;
    var r = R(el);
    try {
      if (r.bottom < 0 || r.top > vh()) {
        el.scrollIntoView({ block: 'center', inline: 'nearest' });
      } else if (r.top < 0 || r.bottom > vh()) {
        el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
      }
    } catch (err) {}
    draw();
    wake(el);
    setTimeout(draw, 150);
  }

  function move(dir) {
    var list = candidates();
    var i, el, r, c;
    if (!list.length) {
      wake(document.body);
      if (dir === 'down') { window.scrollBy(0, vh() * 0.6); return 'scroll'; }
      if (dir === 'up') { window.scrollBy(0, -vh() * 0.6); return 'scroll'; }
      return 'none';
    }
    var best = null, bestScore = 1e12;

    if (!alive(cur)) {
      cur = null;
      var mx = vw() / 2, my = vh() / 2;
      for (i = 0; i < list.length; i++) {
        c = C(R(list[i]));
        var d = Math.sqrt((c.x - mx) * (c.x - mx) + (c.y - my) * (c.y - my));
        if (c.y < 0 || c.y > vh()) d += 5000;
        if (d < bestScore) { bestScore = d; best = list[i]; }
      }
      setCur(best);
      return 'ok';
    }

    var cc = C(R(cur));
    for (i = 0; i < list.length; i++) {
      el = list[i];
      if (el === cur || el.contains(cur) || cur.contains(el)) continue;
      r = R(el);
      c = C(r);
      var dx = c.x - cc.x, dy = c.y - cc.y, p, s;
      if (dir === 'right') { if (dx <= 2) continue; p = dx; s = Math.abs(dy); }
      else if (dir === 'left') { if (dx >= -2) continue; p = -dx; s = Math.abs(dy); }
      else if (dir === 'down') { if (dy <= 2) continue; p = dy; s = Math.abs(dx); }
      else { if (dy >= -2) continue; p = -dy; s = Math.abs(dx); }
      var score = p + s * 2.5;
      if (score < bestScore) { bestScore = score; best = el; }
    }

    if (best) { setCur(best); return 'ok'; }
    wake(cur);
    if (dir === 'down') { window.scrollBy(0, vh() * 0.6); setTimeout(draw, 150); return 'scroll'; }
    if (dir === 'up') { window.scrollBy(0, -vh() * 0.6); setTimeout(draw, 150); return 'scroll'; }
    return 'none';
  }

  window.addEventListener('scroll', function () { if (cur) draw(); }, true);
  window.addEventListener('resize', function () { if (cur) draw(); });

  window.__tvNav = function (cmd) {
    try {
      if (cmd === 'hide') {
        cur = null;
        if (box) box.style.display = 'none';
        return 'ok';
      }
      if (cmd === 'select') {
        if (!alive(cur)) return 'none';
        var r = R(cur), dpr = window.devicePixelRatio || 1;
        var x = Math.min(Math.max(r.left + r.width / 2, 1), vw() - 1);
        var y = Math.min(Math.max(r.top + r.height / 2, 1), vh() - 1);
        return Math.round(x * dpr) + ',' + Math.round(y * dpr);
      }
      return move(cmd);
    } catch (err) {
      return 'err';
    }
  };
})();
