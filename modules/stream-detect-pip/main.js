(function () {
  "use strict";

  var LANG = (navigator.language || "zh").toLowerCase();
  var T;
  var I18N = {
    zh: {
      name: "流媒体画中画",
      detecting: "正在检测视频...",
      none: "未检测到视频",
      found: "检测到 {0} 个视频",
      pipOn: "已开启画中画",
      pipOff: "已退出画中画",
      pipUnavail: "画中画不可用",
      startAll: "全部启用画中画",
      stopAll: "全部退出画中画",
      autoOn: "自动画中画: 已开启",
      autoOff: "自动画中画: 已关闭",
      autoDesc: "视频播放时自动进入画中画",
    },
    en: {
      name: "Streaming PiP",
      detecting: "Detecting video...",
      none: "No video found",
      found: "{0} videos detected",
      pipOn: "PiP enabled",
      pipOff: "PiP exited",
      pipUnavail: "PiP unavailable",
      startAll: "Enable all PiP",
      stopAll: "Exit all PiP",
      autoOn: "Auto PiP: On",
      autoOff: "Auto PiP: Off",
      autoDesc: "Auto-enter PiP when video plays",
    },
    ar: {
      name: "بث صورة داخل صورة",
      detecting: "يكتشف الفيديو...",
      none: "لا يوجد فيديو",
      found: "تم اكتشاف {0} فيديو",
      pipOn: "تم تفعيل PiP",
      pipOff: "تم إنهاء PiP",
      pipUnavail: "PiP غير متاح",
      startAll: "تمكيل كل PiP",
      stopAll: "إنهاء كل PiP",
      autoOn: "البث التلقائي PiP: مفعل",
      autoOff: "البث التلقائي PiP: غير مفعل",
      autoDesc: "أدخل PiP تلقائياً عند تشغيل الفيديو",
    },
  };
  T =
    LANG.indexOf("ar") === 0 && I18N.ar
      ? I18N.ar
      : LANG.indexOf("zh") === 0 && I18N.zh
        ? I18N.zh
        : I18N.en;

  var MODULE = {
    id: "wta-stream-detect-pip",
    name: T.name,
    icon: "🎬",
    color: "#10b981",
  };
  var host = location.hostname;
  var isYouTube =
    host.indexOf("youtube.com") >= 0 || host.indexOf("youtu.be") >= 0;
  function isHls(src) {
    return (
      typeof src === "string" &&
      (src.indexOf(".m3u8") >= 0 || src.indexOf("master.m3u") >= 0)
    );
  }
  function isDash(src) {
    return (
      typeof src === "string" &&
      (src.indexOf(".mpd") >= 0 || src.indexOf("dash") >= 0)
    );
  }

  var autoEntry = true;
  var activePipVideos = new Set();
  var observer = null;

  function getVideos() {
    return Array.prototype.slice.call(document.querySelectorAll("video"));
  }

  function detectStreamingSrc(v) {
    if (!v) return "unknown";
    var srcAttr = v.src || "";
    var srcEl = null;
    if (!srcAttr && v.querySelector) {
      srcEl = v.querySelector("source");
      if (srcEl) srcAttr = srcEl.src || "";
    }
    if (isYouTube) return "youtube";
    if (isHls(srcAttr)) return "hls";
    if (isDash(srcAttr)) return "dash";
    return v.src ? "mp4" : "unknown";
  }

  function canEnterPiP(v) {
    if (!v) return false;
    if (document.pictureInPictureElement) return true;
    if (
      typeof NativeBridge !== "undefined" &&
      typeof NativeBridge.enterPiP === "function"
    )
      return true;
    return false;
  }

  function enterPiP(v) {
    if (!v) return false;
    if (document.pictureInPictureElement) {
      if (document.pictureInPictureElement === v) {
        document.exitPictureInPicture();
        return false;
      }
      try {
        v.requestPictureInPicture();
        return true;
      } catch (e) {
        console.warn("[Streaming PiP] HTML5 PiP failed:", e);
      }
    }
    if (
      typeof NativeBridge !== "undefined" &&
      typeof NativeBridge.enterPiP === "function"
    ) {
      try {
        NativeBridge.enterPiP();
        return true;
      } catch (e) {
        console.warn("[Streaming PiP] Native PiP failed:", e);
      }
    }
    if (typeof v.requestPictureInPicture === "function") {
      v.requestPictureInPicture().catch(function () {});
    }
    return false;
  }

  function exitPiP(v) {
    if (
      document.pictureInPictureElement &&
      v === document.pictureInPictureElement
    ) {
      document.exitPictureInPicture();
    }
  }

  function updatePanel() {
    var vids = getVideos();
    var list = document.getElementById("wta-stream-pip-list");
    var status = document.getElementById("wta-stream-pip-status");
    if (!list || !status) return;
    list.innerHTML = "";
    if (!vids.length) {
      status.textContent = T.none;
      return;
    }
    vids.forEach(function (v) {
      var li = document.createElement("div");
      li.className = "wta-stream-item";
      var icon = document.createElement("span");
      icon.className = "wta-stream-item-icon";
      icon.textContent = "🎬";
      var info = document.createElement("div");
      info.className = "wta-stream-item-info";
      info.textContent = detectStreamingSrc(v);
      var btn = document.createElement("button");
      btn.className = "wta-stream-item-btn";
      btn.textContent = activePipVideos.has(v) ? "✓" : "▶";
      btn.onclick = function () {
        togglePiP(v);
      };
      li.appendChild(icon);
      li.appendChild(info);
      li.appendChild(btn);
      list.appendChild(li);
    });
    status.textContent = T.found.replace("{0}", String(vids.length));
  }

  function togglePiP(v) {
    var wasIn = activePipVideos.has(v);
    var ok = wasIn ? (exitPiP(v), false) : enterPiP(v);
    if (ok) {
      activePipVideos.add(v);
      if (window.__WTA_MODULE_UI__) __WTA_MODULE_UI__.toast(T.pipOn);
    } else {
      activePipVideos.delete(v);
      if (!wasIn && window.__WTA_MODULE_UI__)
        __WTA_MODULE_UI__.toast(T.pipUnavail);
      else if (wasIn && window.__WTA_MODULE_UI__)
        __WTA_MODULE_UI__.toast(T.pipOff);
    }
    updatePanel();
  }

  function startAll() {
    getVideos().forEach(function (v) {
      if (enterPiP(v)) activePipVideos.add(v);
    });
    if (window.__WTA_MODULE_UI__) __WTA_MODULE_UI__.toast(T.pipOn);
    updatePanel();
  }
  function stopAll() {
    activePipVideos.forEach(function (v) {
      exitPiP(v);
    });
    activePipVideos.clear();
    if (window.__WTA_MODULE_UI__) __WTA_MODULE_UI__.toast(T.pipOff);
    updatePanel();
  }

  function handlePlay(e) {
    var v = e.target;
    if (!autoEntry || !canEnterPiP(v)) return;
    if (enterPiP(v)) {
      activePipVideos.add(v);
      if (window.__WTA_MODULE_UI__) __WTA_MODULE_UI__.toast(T.pipOn);
    }
  }

  function observe() {
    if (observer) observer.disconnect();
    observer = new MutationObserver(function () {
      updatePanel();
    });
    observer.observe(document.body, { childList: true, subtree: true });
    getVideos().forEach(function (v) {
      if (!v.__pipBound) {
        v.__pipBound = true;
        v.addEventListener("play", handlePlay);
      }
    });
  }

  function register() {
    var panelHtml =
      '<div class="wta-stream-pip-panel"><div class="wta-stream-pip-header"><span class="wta-stream-pip-title">🎬</span></div><div id="wta-stream-pip-status">' +
      T.detecting +
      '</div><div id="wta-stream-pip-list"></div></div>';
    if (typeof __WTA_MODULE_UI__ === "undefined") {
      setTimeout(register, 100);
      return;
    }
    __WTA_MODULE_UI__.register({
      id: MODULE.id,
      name: MODULE.name,
      icon: MODULE.icon,
      color: MODULE.color,
      uiConfig:
        typeof __MODULE_UI_CONFIG__ !== "undefined"
          ? __MODULE_UI_CONFIG__
          : undefined,
      runMode:
        typeof __MODULE_RUN_MODE__ !== "undefined"
          ? __MODULE_RUN_MODE__
          : "INTERACTIVE",
      actions: [
        { id: "startAll", icon: "⏵", label: T.startAll, action: startAll },
        { id: "stopAll", icon: "⏹", label: T.stopAll, action: stopAll },
      ],
      onAction: function (id) {
        if (id === "startAll") startAll();
        else if (id === "stopAll") stopAll();
      },
      onToggle: function (enabled) {
        autoEntry = enabled;
        if (window.__WTA_MODULE_UI__)
          __WTA_MODULE_UI__.toast(enabled ? T.autoOn : T.autoOff);
        updatePanel();
      },
    });
    // Inject the panel HTML override if the shell supports updatePanel
    if (
      window.__WTA_MODULE_UI__ &&
      typeof __WTA_MODULE_UI__.updatePanel === "function"
    ) {
      __WTA_MODULE_UI__.updatePanel(MODULE.id, panelHtml);
    }
    observe();
    updatePanel();
  }

  // Note: market module registration uses the same __WTA_MODULE_UI__ surface
  // as built-in modules; a single unified onAction handler covers start/stop.
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", register);
  } else {
    register();
  }
})();
