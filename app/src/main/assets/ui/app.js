/* ============================================================
   PS Builder · Plaaslike APK Vervaardiger UI Logika
   Bridge konvensie:
   - window.html2apk.pickHtmlFile(cb) / pickFolder(cb)
   - window.html2apk.startBuild(configJson)
   - window.html2apk.installApk() / openApkFolder()
   - window.html2apk.getEngineStatus() / getDownloadDir() / toast(msg)
   Native callbacks: window.onPickResult / onBuildProgress / onBuildDone / onBuildError
   ============================================================ */
(function () {
  'use strict';

  var bridge = window.html2apk;
  var state = {
    inputDir: '',
    iconPath: '',
    building: false,
    engineReady: false
  };

  var $ = function (id) { return document.getElementById(id); };
  var LOG = $('log');

  /* ---------- Enjin status (LED bo) ---------- */
  function setEngine(status, text) {
    var led = $('engLed');
    led.className = 'led ' + status;
    $('engText').textContent = text;
  }
  function checkEngine() {
    if (!bridge || !bridge.getEngineStatus) { setEngine('fault', 'GEEN BRIDGE'); return; }
    try {
      var res = JSON.parse(bridge.getEngineStatus());
      if (res.ready) {
        state.engineReady = true;
        setEngine('on', 'ENJIN GEREED');
      } else {
        state.engineReady = false;
        setEngine('fault', 'ENJIN FOUT');
      }
    } catch (e) {
      state.engineReady = false;
      setEngine('fault', 'ENJIN FOUT');
    }
    refreshBuildBtn();
  }

  /* ---------- Invoer keuse ---------- */
  function onPickResult(path) {
    if (!path) {
      state.inputDir = '';
      $('pathBox').hidden = true;
      refreshBuildBtn();
      return;
    }
    state.inputDir = path;
    var parts = path.split('/');
    $('pathText').textContent = parts[parts.length - 1];
    $('pathBox').hidden = false;
    autoFillPackage();
    refreshBuildBtn();
  }

  function autoFillPackage() {
    var pkgInput = $('pkg');
    if (!pkgInput.value.trim()) {
      var name = $('appName').value.trim();
      if (name) {
        var slug = name.toLowerCase().replace(/[^a-z0-9]+/g, '.').replace(/^\.+|\.+$/g, '');
        if (slug) pkgInput.value = 'com.' + slug;
      }
    }
  }

  function refreshBuildBtn() {
    var btn = $('buildBtn');
    var canBuild = state.engineReady && !!state.inputDir && !!$('appName').value.trim() && !state.building;
    btn.disabled = !canBuild;
  }

  /* ---------- Log ---------- */
  function appendLog(text, cls) {
    var span = document.createElement('span');
    if (cls) span.className = cls;
    span.textContent = text + '\n';
    LOG.appendChild(span);
    LOG.scrollTop = LOG.scrollHeight;
  }
  function stamp() {
    var d = new Date();
    var p = function (n) { return ('0' + n).slice(-2); };
    return '[' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds()) + '] ';
  }

  /* ---------- Bou ---------- */
  function startBuild() {
    if (state.building) return;
    var appName = $('appName').value.trim();
    if (!appName) { toast('Vul asseblief App Naam in'); $('appName').focus(); return; }

    var pkg = $('pkg').value.trim();
    var pkgPattern = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/;
    if (pkg && !pkgPattern.test(pkg)) { toast('Package naam is verkeerd, bv: com.example.app'); return; }
    if (!pkg) pkg = 'com.psbuilder.app' + (Date.now() % 100000);

    var versionName = $('verName').value.trim() || '1.0';
    var versionCode = parseInt($('verCode').value, 10);
    if (!versionCode || versionCode < 1) versionCode = 1;

    var cfg = {
      appName: appName,
      packageName: pkg,
      versionName: versionName,
      versionCode: versionCode,
      entryFile: 'index.html',
      inputDir: state.inputDir,
      iconPath: state.iconPath,
      statusBarColor: '#0B0E14',
      navBarColor: '#0B0E14',
      backgroundColor: '#0B0E14'
    };

    state.building = true;
    refreshBuildBtn();
    var btn = $('buildBtn');
    btn.textContent = 'BOU NOU…';
    btn.disabled = true;
    setEngine('busy', 'ENJIN BESIG');
    $('resultPanel').hidden = true;
    LOG.textContent = '';
    appendLog(stamp() + '$ psbuilder build --app "' + appName + '" --pkg ' + pkg);

    bridge.startBuild(JSON.stringify(cfg));
  }

  /* ---------- Native callbacks ---------- */
  window.onPickResult = function (path) { onPickResult(path); };

  /* ---------- Voorblad ikoon ---------- */
  window.onIconResult = function (path, preview) {
    if (!path || !preview) {
      toast('Ikoon kon nie gelees word nie');
      return;
    }
    state.iconPath = path;
    var img = $('iconPreview');
    img.src = preview;
    img.hidden = false;
    $('iconClear').hidden = false;
    toast('Ikoon gestel');
  };

  function clearIcon() {
    state.iconPath = '';
    $('iconPreview').hidden = true;
    $('iconPreview').removeAttribute('src');
    $('iconClear').hidden = true;
  }

  window.onBuildProgress = function (step, msg) {
    appendLog(stamp() + '[' + step + '] ' + msg, 'step');
  };

  window.onBuildDone = function (path, size) {
    state.building = false;
    var btn = $('buildBtn');
    btn.textContent = 'BOU APK';
    refreshBuildBtn();
    setEngine('on', 'ENJIN GEREED');
    appendLog(stamp() + 'BOU VOLTOOI - ' + size, 'ok');
    $('rPath').textContent = path;
    $('rSize').textContent = size;
    $('rPkg').textContent = $('pkg').value.trim();
    $('resultPanel').hidden = false;
    $('resultPanel').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    toast('Bou voltooi ✓');
  };

  window.onBuildError = function (msg) {
    state.building = false;
    var btn = $('buildBtn');
    btn.textContent = 'BOU APK';
    refreshBuildBtn();
    setEngine('fault', 'BOU HET MISLUK');
    appendLog(stamp() + 'FOUT: ' + msg, 'err');
    toast('Bou het misluk');
  };

  /* ---------- Event binding ---------- */
  function bind() {
    $('pickFile').addEventListener('click', function () {
      if (state.building) return;
      bridge.pickHtmlFile('onPickResult');
    });
    $('pickDir').addEventListener('click', function () {
      if (state.building) return;
      bridge.pickFolder('onPickResult');
    });
    $('clearPath').addEventListener('click', function () {
      state.inputDir = '';
      $('pathBox').hidden = true;
      refreshBuildBtn();
    });
    $('buildBtn').addEventListener('click', startBuild);
    $('installBtn').addEventListener('click', function () { bridge.installApk(); });
    $('iconBtn').addEventListener('click', function () {
      if (state.building) return;
      bridge.pickIcon('onIconResult');
    });
    $('iconClear').addEventListener('click', clearIcon);
    ['appName', 'pkg', 'verName', 'verCode'].forEach(function (id) {
      $(id).addEventListener('input', refreshBuildBtn);
    });
    $('appName').addEventListener('input', autoFillPackage);
  }

  function toast(msg) {
    if (bridge && bridge.toast) { bridge.toast(msg); return; }
    var t = $('toast');
    t.textContent = msg;
    t.classList.add('show');
    clearTimeout(t._h);
    t._h = setTimeout(function () { t.classList.remove('show'); }, 1800);
  }

  /* ---------- Startup ---------- */
  document.addEventListener('DOMContentLoaded', function () {
    bind();
    checkEngine();
  });

  var pollCount = 0;
  var pollTimer = setInterval(function () {
    if (state.engineReady) { clearInterval(pollTimer); return; }
    if (++pollCount > 10) { clearInterval(pollTimer); return; }
    checkEngine();
  }, 1500);
})();
