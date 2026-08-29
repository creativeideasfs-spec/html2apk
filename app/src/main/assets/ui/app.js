/* ============================================================
   HTML2APK · 工业复古控制台 UI 逻辑（对接真实 JsBridge）
   Bridge 约定：
   - window.html2apk.pickHtmlFile(cb) / pickFolder(cb)
   - window.html2apk.startBuild(configJson)
   - window.html2apk.installApk() / openApkFolder()
   - window.html2apk.getEngineStatus() / getDownloadDir() / toast(msg)
   原生回调：window.onPickResult / onBuildProgress / onBuildDone / onBuildError
   ============================================================ */
(function () {
  'use strict';

  var bridge = window.html2apk;
  var state = {
    inputDir: '',
    building: false,
    engineReady: false
  };

  var $ = function (id) { return document.getElementById(id); };
  var LOG = $('log');

  /* ---------- 引擎状态（顶部 LED） ---------- */
  function setEngine(status, text) {
    var led = $('engLed');
    led.className = 'led ' + status;
    $('engText').textContent = text;
  }
  function checkEngine() {
    if (!bridge || !bridge.getEngineStatus) { setEngine('fault', 'NO BRIDGE'); return; }
    try {
      var res = JSON.parse(bridge.getEngineStatus());
      if (res.ready) {
        state.engineReady = true;
        setEngine('on', 'ENGINE READY');
      } else {
        state.engineReady = false;
        setEngine('fault', 'ENGINE FAULT');
      }
    } catch (e) {
      state.engineReady = false;
      setEngine('fault', 'ENGINE FAULT');
    }
    refreshBuildBtn();
  }

  /* ---------- 输入选择 ---------- */
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

  /* ---------- 日志 ---------- */
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

  /* ---------- 构建 ---------- */
  function startBuild() {
    if (state.building) return;
    var appName = $('appName').value.trim();
    if (!appName) { toast('请填写应用名称'); $('appName').focus(); return; }

    var pkg = $('pkg').value.trim();
    var pkgPattern = /^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$/;
    if (pkg && !pkgPattern.test(pkg)) { toast('包名格式不正确，例如 com.example.app'); return; }
    if (!pkg) pkg = 'com.html2apk.app' + (Date.now() % 100000);

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
      statusBarColor: '#f0ede4',
      navBarColor: '#f0ede4',
      backgroundColor: '#f0ede4'
    };

    state.building = true;
    refreshBuildBtn();
    var btn = $('buildBtn');
    btn.textContent = 'BUILDING…';
    btn.disabled = true;
    setEngine('busy', 'ENGINE BUSY');
    $('resultPanel').hidden = true;
    LOG.textContent = '';
    appendLog(stamp() + '$ html2apk build --app "' + appName + '" --pkg ' + pkg);

    bridge.startBuild(JSON.stringify(cfg));
  }

  /* ---------- 原生回调 ---------- */
  window.onPickResult = function (path) { onPickResult(path); };

  window.onBuildProgress = function (step, msg) {
    appendLog(stamp() + '[' + step + '] ' + msg, 'step');
  };

  window.onBuildDone = function (path, size) {
    state.building = false;
    var btn = $('buildBtn');
    btn.textContent = 'EXECUTE BUILD';
    refreshBuildBtn();
    setEngine('on', 'ENGINE READY');
    appendLog(stamp() + 'BUILD COMPLETE - ' + size, 'ok');
    $('rPath').textContent = path;
    $('rSize').textContent = size;
    $('rPkg').textContent = $('pkg').value.trim();
    $('resultPanel').hidden = false;
    $('resultPanel').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    toast('构建完成');
  };

  window.onBuildError = function (msg) {
    state.building = false;
    var btn = $('buildBtn');
    btn.textContent = 'EXECUTE BUILD';
    refreshBuildBtn();
    setEngine('fault', 'BUILD FAILED');
    appendLog(stamp() + 'ERROR: ' + msg, 'err');
    toast('构建失败');
  };

  /* ---------- 事件绑定 ---------- */
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

  /* ---------- 启动 ---------- */
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
