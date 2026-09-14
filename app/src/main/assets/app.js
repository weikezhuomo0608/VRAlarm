"use strict";
const clone = (value) => JSON.parse(JSON.stringify(value));
const $ = (s) => document.querySelector(s), $$ = (s) => [...document.querySelectorAll(s)];
const esc = (v) => String(v != null ? v : "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
const paths = { bell: "M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4", moon: "M20.8 13A9 9 0 0 1 11 3.2 9 9 0 1 0 20.8 13Z", clock: "M12 8v4l3 2M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0", sound: "M11 5 6 9H3v6h3l5 4ZM15 8a6 6 0 0 1 0 8M18 5a10 10 0 0 1 0 14", settings: "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8M9 3h6l1 3 3 1 2 5-2 5-3 1-1 3H9l-1-3-3-1-2-5 2-5 3-1Z", shield: "M12 3 3 7v5c0 5 9 9 9 9s9-4 9-9V7ZM8 12l3 3 5-6", arrow: "M4 12h16m-6-6 6 6-6 6", refresh: "M20 10a8 8 0 0 0-14-5L3 8m0-5v5h5M4 14a8 8 0 0 0 14 5l3-3m0 5v-5h-5", external: "M14 3h7v7m0-7L10 14M9 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-4", history: "M3 11a9 9 0 1 1 2 7M3 3v8h8M12 7v5l4 2", play: "m9 5 11 7-11 7Z", spark: "m12 2 2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5ZM20 2v4m-2-2h4", sun: "M12 3V1M12 23v-2M3 12H1m22 0h-2M4 4l2 2m12 12 2 2M4 20l2-2M18 6l2-2M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0", music: "M9 18V5l12-2v13M9 8l12-2M9 18a3 3 0 1 1-3-3h3m12 1a3 3 0 1 1-3-3h3", file: "M14 2H5a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V9ZM14 2v7h7M8 14h8M8 18h5", calendar: "M8 2v4M16 2v4M3 8h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Z", plus: "M12 5v14M5 12h14", close: "m6 6 12 12M6 18 18 6", check: "m5 12 4 4L19 6", battery: "M22 10v4M3 6h15a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1M11 8l-3 5h5l-3 4", screen: "M8 3H3v5m13-5h5v5M3 16v5h5m13-5v5h-5", download: "M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5", upload: "M12 16V4m-5 5 5-5 5 5M4 16v5h16v-5", info: "M12 11v6M12 7v.1M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0", wifi: "M2 8a16 16 0 0 1 20 0M5 12a11 11 0 0 1 14 0M8 16a6 6 0 0 1 8 0M12 20h.01", heart: "M20 5c-3-3-7-1-8 1-1-2-5-4-8-1-4 4 0 9 8 15 8-6 12-11 8-15", pause: "M7 4v16M17 4v16", pencil: "M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" };
const icon = (name, extra = "") => `<svg class="i ${extra}" viewBox="0 0 24 24" aria-hidden="true"><path d="${paths[name] || paths.bell}"></path></svg>`;
let seq = 0, awaiting = /* @__PURE__ */ new Map(), native = typeof window.HazelNative !== "undefined", route = "home", S = null, configFingerprint = "", busy = false, ruleDraft = null, alarmPainted = false, zoneList = [];
let compatibilityAction = null, compatibilitySaving = false, anchorDraft = null;
const defaults = { soundWithoutNotifications: false, allDay: true, timezone: "device", catchUp: false, pollSeconds: 30, reliable: true, boot: true, ringtone: "starlight", customName: "未选择", volume: 85, ramp: true, vibrate: true, duration: 60, snoozeMinutes: 5, quietCalls: true, theme: "light", preStream: true, ringQueue: false, aiOcr: true, aiKey: "", aiModel: "deepseek-flash", seedColor: "", amoled: false, hideRecents: false, recovery: true, backgroundDim: 40, cardOpacity: 94, windows: [{ id: "night", name: "凌晨守候", start: 60, end: 360, days: 127, enabled: true }] };
const previewState = { config: clone(defaults), enabled: false, running: false, ringing: false, snapshot: {}, anchors: [{ id: "hazel", name: "灰泽满 Hazel", uid: 1298779265, room: 1713546334, enabled: true, avatar: false, snapshot: {} }], networkError: "", serviceError: "", startError: "", inside: true, permissions: { notifications: false, alarmChannel: true, battery: false, fullScreen: false, exact: false, dnd: false, alarmVolume: 4, alarmMax: 7 }, zone: Intl.DateTimeFormat().resolvedOptions().timeZone, deviceZone: Intl.DateTimeFormat().resolvedOptions().timeZone, version: "1.1.1", preview: true, now: Date.now(), snoozeAt: 0, testAt: 0, backgroundName: "", backgroundSet: false, recoveryAt: 0 };
if (!native) $("#preview").textContent = "界面预览 · 检测与响铃功能请安装安卓应用体验";
window.NativeReply = (id, result) => {
  const p = awaiting.get(id);
  if (!p) return;
  clearTimeout(p.timer);
  awaiting.delete(id);
  result.ok ? p.resolve(result.value) : p.reject(new Error(result.error));
};
function api(action, data = {}, timeoutMs = 1e4) {
  if (!native) {
    if (action === "state") {
      previewState.now = Date.now();
      return Promise.resolve(clone(previewState));
    }
    if (action === "history") return Promise.resolve([]);
    if (action === "zones") {
      const ids = Intl.supportedValuesOf ? Intl.supportedValuesOf("timeZone") : ["Asia/Shanghai", "Asia/Tokyo", "Asia/Kathmandu", "Europe/London", "America/New_York", "America/Los_Angeles", "Australia/Sydney"];
      return Promise.resolve(ids.map((id) => ({ id, offset: "", time: new Intl.DateTimeFormat("zh-CN", { timeZone: id, hour: "2-digit", minute: "2-digit", hour12: false }).format(/* @__PURE__ */ new Date()) })));
    }
    if (action === "save") {
      Object.assign(previewState.config, data);
      return Promise.resolve(previewState.config);
    }
    if (action === "resolveAnchor") return Promise.resolve({ id: data.id || "preview", room: Number(data.room) || 1713546334, uid: 1298779265, name: "灰泽满 Hazel", avatar: false });
    if (action === "saveAnchor" || action === "deleteAnchor") {
      toast("这是界面预览，请安装 APK 使用此功能");
      return Promise.resolve(previewState.anchors);
    }
    if (action === "toggle" || action === "test" || action === "testLater" || action === "permission" || action === "refresh" || action === "pickAudio" || action === "export" || action === "exportNotificationReport" || action === "import") {
      toast("这是界面预览，请安装 APK 使用此功能");
      return Promise.resolve(previewState);
    }
    return Promise.resolve(true);
  }
  return new Promise((resolve, reject) => {
    const id = "r" + ++seq, timer = setTimeout(() => {
      awaiting.delete(id);
      reject(new Error("操作超时，请重新打开应用后重试"));
    }, timeoutMs);
    awaiting.set(id, { resolve, reject, timer });
    window.HazelNative.request(id, action, JSON.stringify(data));
  });
}
function toast(text) {
  const el = $("#toast");
  el.textContent = text;
  el.classList.add("visible");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => el.classList.remove("visible"), 4200);
}
function time(n) {
  return `${String(Math.floor(n / 60)).padStart(2, "0")}:${String(n % 60).padStart(2, "0")}`;
}
function stamp(ms, full = false) {
  if (!ms) return "尚未检测";
  try {
    return new Intl.DateTimeFormat("zh-CN", { timeZone: (S == null ? void 0 : S.zone) || void 0, ...full ? { month: "2-digit", day: "2-digit" } : {}, hour: "2-digit", minute: "2-digit", ...full ? {} : { second: "2-digit" }, hour12: false }).format(new Date(ms));
  } catch (e) {
    return new Date(ms).toLocaleTimeString();
  }
}
const toneNames = { starlight: "星铃", morning: "清晨", urgent: "强提醒", system: "系统闹钟", custom: "自选铃声" };
function sw(key, checked, label) {
  return `<button class="switch" role="switch" aria-checked="${!!checked}" aria-label="${esc(label)}" data-toggle="${esc(key)}"></button>`;
}
function setting(title, description, right) {
  return `<div class="setting-row"><div class="grow"><h3>${title}</h3><p class="sub">${description}</p></div>${right}</div>`;
}
function select(key, value, choices) {
  return `<select aria-label="${esc(key)}" data-setting="${key}">${choices.map(([v, n]) => `<option value="${esc(v)}" ${String(v) === String(value) ? "selected" : ""}>${esc(n)}</option>`).join("")}</select>`;
}
function hexRgb(hex) {
  return [parseInt(hex.slice(1, 3), 16), parseInt(hex.slice(3, 5), 16), parseInt(hex.slice(5, 7), 16)];
}
function lumaRgb(rgb) {
  return (0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]) / 255;
}
function mixRgb(a, b, t) {
  return [0, 1, 2].map((i) => Math.round(a[i] + (b[i] - a[i]) * t));
}
function cssRgb(rgb) {
  return `rgb(${rgb[0]},${rgb[1]},${rgb[2]})`;
}
function applyTheme() {
  const c = S.config, night = c.theme === "dark" || c.theme === "system" && matchMedia("(prefers-color-scheme:dark)").matches;
  document.body.classList.toggle("dark", night);
  document.body.classList.toggle("amoled", night && !!c.amoled);
  const vars = document.documentElement.style;
  const seed = c.seedColor && /^#[0-9a-fA-F]{6}$/.test(c.seedColor) ? hexRgb(c.seedColor) : null;
  if (seed) {
    let primary = seed;
    if (night) primary = mixRgb(seed, [255, 255, 255], 0.5);
    else if (lumaRgb(seed) > 0.45) primary = mixRgb(seed, [0, 0, 0], 0.38);
    vars.setProperty("--primary", cssRgb(primary));
    vars.setProperty("--on-primary", lumaRgb(primary) > 0.179 ? "#111318" : "#ffffff");
  } else {
    vars.removeProperty("--primary");
    vars.removeProperty("--on-primary");
  }
  const isAlarm = document.body.classList.contains("alarm-page");
  if (isAlarm) return;
  const base = night ? c.amoled ? [18, 17, 20] : [36, 44, 53] : [255, 255, 255];
  const op = Math.min(100, Math.max(75, Number(c.cardOpacity) || 94)) / 100;
  vars.setProperty("--card", `rgba(${base[0]},${base[1]},${base[2]},${op})`);
  const layer = document.getElementById("bg-layer");
  if (layer) {
    if (S.backgroundSet) {
      layer.style.backgroundImage = "url(/background/bg.jpg)";
      document.body.classList.add("has-bg");
    } else {
      layer.style.backgroundImage = "";
      document.body.classList.remove("has-bg");
    }
    vars.setProperty("--bg-dim", String(Math.min(90, Math.max(0, Number(c.backgroundDim) || 0)) / 100));
  }
}
function header() {
  const history2 = route === "history";
  $("#header").innerHTML = `<div class="brand"><img src="hazel.png" alt="VR闹钟图标"><div><div class="brand-name">VR闹钟</div><div class="brand-sub">LIVE STREAM ALARM</div></div></div><button class="icon-button" data-action="${history2 ? "back" : "history"}" aria-label="${history2 ? "返回" : "通知记录"}">${icon(history2 ? "close" : "history")}</button>`;
}
function navigation() {
  const tabs = [["home", "bell", "守候"], ["anchors", "heart", "主播"], ["week", "calendar", "周表"], ["schedule", "clock", "时段"], ["sound", "sound", "声音"], ["settings", "settings", "设置"]];
  $("#nav").innerHTML = tabs.map(([id, i, label]) => `<button class="${route === id ? "active" : ""}" data-route="${id}" ${route === id ? 'aria-current="page"' : ""}><span class="icon-wrap">${icon(i)}</span>${label}</button>`).join("");
}
function heading(label, title, sub) {
  return `<div class="page-heading"><div class="eyebrow">${label}</div><h1>${title}</h1><p>${sub}</p></div>`;
}
function render() {
  if (!S) return;
  applyTheme();
  if (document.body.classList.contains("alarm-page")) {
    renderAlarm();
    return;
  }
  header();
  navigation();
  if (route === "home") home();
  if (route === "anchors") anchorsPage();
  if (route === "week") week();
  if (route === "schedule") schedule();
  if (route === "sound") sound();
  if (route === "settings") settings();
  if (route === "history") history();
}
function enabledAnchors() {
  return (S.anchors || []).filter((a) => a.enabled);
}
function statusSummary() {
  const list = enabledAnchors(), known = list.filter((a) => (a.snapshot || {}).checkedAt);
  if (!known.length) return [S.networkError ? "待重试" : "尚未检测", S.networkError ? "wifi" : "clock", false];
  const live = known.filter((a) => a.snapshot.status === 1);
  if (live.length === 1) return [live[0].name + " 正在直播", "bell", true];
  if (live.length > 1) return [live.length + " 位主播正在直播", "bell", true];
  if (Date.now() - Math.max(...known.map((a) => a.snapshot.checkedAt)) > Math.max(3e5, S.config.pollSeconds * 4e3)) return ["状态待更新", "clock", false];
  if (known.some((a) => a.snapshot.status === 2)) return ["轮播中 · 不触发开播提醒", "play", false];
  return ["暂未开播", "moon", false];
}
function alarmAvailable() {
  const p = S.permissions;
  return !!S.config.soundWithoutNotifications || !!(p.notifications && (p.alarmChannelImportance === void 0 ? p.alarmChannel : p.alarmChannelImportance > 0));
}
function watchNoticeContent() {
  var _a;
  const status = ((_a = S.watchNotification) == null ? void 0 : _a.status) || (S.running ? "unknown" : S.enabled ? "interrupted" : "stopped");
  const copy = {
    stopped: ["尚未开启", "开启守候后，使用前台服务持续监测，并提交常驻状态通知。"],
    interrupted: ["后台服务待恢复", "守候开关已保存，但服务暂未运行。请检查后台权限后重新开启。"],
    permission_blocked: ["通知被系统拦截", "前台服务正在运行，但系统未允许通知，通知栏无法显示常驻卡片。"],
    channel_blocked: ["守候通知通道已关闭", "前台服务正在运行。请打开“后台守候状态”通道，才能显示常驻卡片。"],
    registered: ["前台守候已运行", "守候通知已提交到系统；可在通知中立即检查、关闭响铃或停止守候。"],
    not_posted: ["前台服务正在运行", "系统暂未列出守候通知。请下拉通知栏确认，并检查通知设置。"],
    unknown: ["前台服务正在运行", "暂时无法读取通知的提交状态，请下拉通知栏确认。"]
  };
  const [label, detail] = copy[status] || copy.unknown;
  return `<div class="row"><div class="label-icon">${icon("shield")}</div><div class="grow"><h3>常驻通知守候</h3><p class="sub">${esc(label)}</p></div></div><p class="sub" style="margin-top:12px">${esc(detail)}</p><div class="permission-actions"><button class="text-button" data-permission="watchChannel">守候通知设置 ${icon("external")}</button><button class="text-button" data-action="watchNotificationHelp">通知授权帮助 ${icon("arrow")}</button></div><p class="hint">停止守候后会移除通知。常驻服务可减少后台中断，但仍受系统省电和强行停止限制。</p>`;
}
function watchNoticeCard() {
  return `<section class="card" data-watch-notice>${watchNoticeContent()}</section>`;
}
function paintWatchNotice() {
  const card = $("[data-watch-notice]");
  if (!card || !S) return;
  const html = watchNoticeContent();
  if (card.innerHTML !== html) card.innerHTML = html;
}
function compatibilityInfo(next = null) {
  compatibilityAction = next;
  openModal("通知异常，也可以选择响铃", `<div class="body-copy"><p>如果小米等手机的通知开关反复关闭，可以开启<strong>通知异常时仍响铃</strong>。只需在本应用中选择，无需额外工具。</p><p>开启后，即使系统未允许通知，也会按你的时段、时区和音量设置检测开播并尝试播放闹铃。不会替你打开系统通知权限。</p><p><strong>通知栏和锁屏提醒可能不显示。</strong>响铃时可打开应用关闭，或等待设定的 ${S.config.duration} 秒自动停止。可选的悬浮关闭按钮需要“显示在其他应用上层”权限。</p><p>勿扰、通话和系统后台限制仍然有效。建议开启后做一次锁屏测试。</p></div>`, `<div class="sheet-actions"><button class="secondary" data-permission="notifications">允许系统通知</button><button class="primary" data-action="confirmCompatibility">${next ? "兼容响铃并继续" : "开启兼容响铃"}</button></div>`);
}
function home() {
  const c = S.config, [liveLabel, liveIcon, isLive] = statusSummary(), n = c.windows.filter((x) => x.enabled).length, p = S.permissions, ready = [p.notifications, p.alarmChannel, p.fullScreen, p.battery, p.exact].filter(Boolean).length;
  const armed = enabledAnchors(), liveNow = armed.filter((a) => (a.snapshot || {}).status === 1), liveTarget = liveNow[0] || armed[0] || {};
  const lastCheck = Math.max(0, ...armed.map((a) => (a.snapshot || {}).checkedAt || 0));
  const watchChip = esc(armed.length === 0 ? "尚未选择主播" : armed.length === 1 ? "专属守候 · " + armed[0].name : "守候 " + armed.length + " 位主播");
  const watchTitle = S.enabled ? S.running ? "正在替你守候" : "守候需要恢复" : "准备好，再开始守候";
  const watchSub = S.enabled ? S.inside ? "当前在提醒时段内 · 开播即提醒" : "时段外安静守候 · 不会响铃" : "先完成权限检查，锁屏也能安心等待";
  let html = `<section class="hero"><div class="hero-copy"><div class="eyebrow">NEVER MISS A STREAM</div><h1>${S.enabled ? "安心去忙，<br>开播叫你。" : "开播那一刻，<br>马上叫醒你。"}</h1><div class="tiny-note">${icon("heart")}把期待，交给一声铃响</div></div><img class="hero-art" src="hazel.png" alt="VR闹钟插画"><div class="hero-chip"><span class="dot ${S.enabled ? "green" : ""}"></span>${watchChip}</div></section>
    <section class="card"><div class="row"><div class="label-icon ${S.enabled && S.running ? "green" : ""}">${icon(S.enabled ? "shield" : "bell")}</div><div class="grow"><h3>${watchTitle}</h3><p class="sub">${watchSub}</p></div>${sw("enabled", S.enabled, "开启或停止直播守候")}</div>
    <div class="metrics"><button class="metric" data-route="schedule"><strong>${c.allDay ? "全天" : n + " 个时段"}</strong><small>提醒范围</small></button><button class="metric" data-route="sound"><strong>${c.volume}<span class="small">%</span></strong><small>闹钟音量</small></button><button class="metric" data-route="settings"><strong>${c.pollSeconds}<span class="small"> 秒</span></strong><small>检测间隔</small></button></div>
    ${!S.enabled ? `<button class="primary watch-button" data-action="start">${icon("bell")}开始守候</button>` : ""}
    <div class="live-info"><span class="dot ${isLive ? "live" : ""}"></span><span class="live-state">${liveLabel}</span><button class="text-button" data-action="refresh">${icon("refresh")}刷新</button></div>${liveNow.length === 1 && liveNow[0].snapshot.title ? `<p class="live-title">${esc(liveNow[0].snapshot.title)}</p>` : ""}<p class="timestamp" style="margin-top:7px">${lastCheck ? "上次成功检测 " + stamp(lastCheck) + " · " + esc(S.zone) : "尚未连接直播间，点击刷新查看状态"}</p></section>`;
  if (!armed.length) html += `<div class="card warning"><p>当前没有启用中的主播，守候不会响铃。</p><button class="text-button" data-route="anchors">去主播页启用 ${icon("arrow")}</button></div>`;
  if (S.ringing) html = `<div class="card warning"><div class="row"><div class="grow"><h3>响铃进行中</h3><p class="sub">${S.alarmAnchor ? esc(S.alarmAnchor) + " · " : ""}点按右侧按钮立即结束</p></div><button class="primary" data-action="dismiss">关闭响铃</button></div></div>` + html;
  const beat = S.serviceHeartbeatAt || 0, silent = Date.now() - beat;
  if (S.enabled && (!beat || silent > 18e4)) html += `<div class="card warning"><p>守候服务已中断${beat ? "约 " + Math.round(silent / 6e4) + " 分钟" : "，尚未收到服务心跳"}，期间不会检测开播。应用每 15 分钟会尝试自动恢复；若经常中断，请在系统设置中允许“自启动”，把电池策略设为“不限制”，并在最近任务里长按本应用的卡片将它<strong>锁定</strong>——从最近任务划掉应用时，系统会连恢复用的闹钟一起停掉。</p><button class="text-button" data-action="start">立即重新开启 ${icon("arrow")}</button>${p.accessibility ? "" : `<button class="text-button" data-action="accessibilityInfo">开启无障碍守候辅助 ${icon("arrow")}</button>`}</div>`;
  if (S.enabled) html += watchNoticeCard();
  if (!p.notifications) html += `<div class="card warning"><p>${c.soundWithoutNotifications ? "通知异常兼容响铃已开启；系统通知仍未获准，响铃时可打开应用关闭。" : "系统尚未允许通知。可在设置中授权，或选择通知异常时仍响铃。"}</p><button class="text-button" data-action="compatibilityInfo">${c.soundWithoutNotifications ? "查看兼容响铃说明" : "选择兼容响铃"} ${icon("arrow")}</button></div>`;
  if (S.testAt > Date.now()) html += `<div class="card warning"><div class="row"><div class="grow"><h3>锁屏测试已安排</h3><p class="sub">${stamp(S.testAt)} 响铃，现在可以锁屏</p></div><button class="text-button" data-action="cancelTest">取消</button></div></div>`;
  if (S.snoozeAt > 0) html += `<div class="card"><div class="row"><div class="label-icon">${icon("clock")}</div><div class="grow"><h3>稍后再叫你</h3><p class="sub">${stamp(S.snoozeAt)} 再确认本场直播</p></div><button class="text-button" data-action="cancelSnooze">取消</button></div></div>`;
  if (S.networkError || S.serviceError || S.startError) html += `<div class="card warning"><p>${esc(S.serviceError || S.startError || S.networkError)}</p></div>`;
  html += `<div class="quick-actions"><button class="quick" data-action="testDialog">${icon("sound")}<div><strong>响铃测试</strong><small>听一下，放心等</small></div></button><button class="quick" data-action="openLive" data-anchor="${esc(liveTarget.id || "")}">${icon("external")}<div><strong>打开直播间</strong><small>${liveNow.length ? "正在直播中" : "去守候的直播间看看"}</small></div></button></div>
    <section class="card ready"><div class="row"><div class="label-icon green">${icon("shield")}</div><div class="grow"><h3>响铃准备度 <span class="muted">${ready} / 5</span></h3><p class="sub">${ready === 5 ? "主要权限已就绪，仍建议做一次锁屏测试" : "检查通知与后台权限，让提醒更可靠"}</p></div><button class="text-button" data-route="settings">去检查 ${icon("arrow")}</button></div></section>
    <p class="footnote">每场直播只自动提醒一次。<br>关机、断网或被系统强制停止时，提醒可能延迟或无法送达。<br>本质AI拼好钟，切勿盲目信任。</p>`;
  $("#content").innerHTML = html;
}
function anchorStatus(snapshot) {
  if (!snapshot || !snapshot.checkedAt) return ["尚未检测", "clock", false];
  if (Date.now() - snapshot.checkedAt > Math.max(3e5, S.config.pollSeconds * 4e3)) return ["状态待更新", "clock", false];
  if (snapshot.status === 1) return ["正在直播", "bell", true];
  if (snapshot.status === 2) return ["轮播中 · 不触发", "play", false];
  return ["暂未开播", "moon", false];
}
function artHue(key) {
  let h = 0;
  const s = String(key || "");
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
  return h;
}
function anchorArt(a, cls = "") {
  const letter = esc((a.name || "?").trim().slice(0, 1) || "?");
  const h = artHue(a.id || a.name);
  const tone = `background:linear-gradient(135deg,hsl(${h},46%,72%),hsl(${(h + 40) % 360},46%,58%))`;
  if (!a.avatar) return `<div class="anchor-art placeholder ${cls}" style="${tone}">${letter}</div>`;
  return `<div class="anchor-art ${cls}" style="${tone}"><span class="anchor-letter">${letter}</span><img src="/avatar/${esc(a.id)}.png" alt="${esc(a.name)}的头像"></div>`;
}
function anchorsPage() {
  const list = S.anchors || [], on = list.filter((a) => a.enabled), live = on.filter((a) => (a.snapshot || {}).status === 1);
  let html = heading("YOUR BROADCASTERS", "想被谁叫醒，就加上谁。", "每位主播一个开关；提醒时段与铃声由所有主播共用。") + `<section class="card"><div class="row"><div class="label-icon ${live.length ? "green" : ""}">${icon(live.length ? "bell" : "shield")}</div><div class="grow"><h3>${live.length ? live.length + " 位主播正在直播" : "守候中 · " + on.length + " 位主播"}</h3><p class="sub">同一时间只响一次；其余主播开播时记入记录，不会叠着响。</p></div></div><div class="rule-footer"><small class="muted">头像只在识别时下载一次，失败不会自动补</small><button class="text-button" data-action="refreshAvatars">${icon("refresh")}刷新全部头像</button></div></section>`;
  html += list.map((a) => {
    const [label, i, isLive] = anchorStatus(a.snapshot || {});
    return `<article class="card anchor-card ${a.enabled ? "" : "disabled-rule"}"><div class="row">${anchorArt(a)}
            <div class="grow"><div class="anchor-name">${esc(a.name)}</div><div class="live-info"><span class="dot ${isLive ? "live" : ""}"></span><span class="live-state">${label}</span></div><small class="sub"><span class="nowrap">UID ${esc(String(a.uid))}</span> · <span class="nowrap">直播间 ${esc(String(a.room))}</span></small></div>
            <div class="anchor-switches"><span class="mini">检测${sw("anchor:" + a.id, a.enabled, a.name + " 的检测开关")}</span><span class="mini">响铃${sw("ring:" + a.id, a.alarm !== false, a.name + " 的响铃开关")}</span></div></div>
            <div class="rule-footer anchor-footer"><small class="muted">${a.snapshot && a.snapshot.checkedAt ? `<span class="nowrap">上次检测 ${stamp(a.snapshot.checkedAt)}</span>` : '<span class="nowrap">尚未检测</span>'}${a.stats && a.stats.count30 !== void 0 ? ` · <span class="nowrap">本周 ${a.stats.count7} 场</span> · <span class="nowrap">近 30 天 ${a.stats.count30} 场</span>` : ""}</small><div class="anchor-actions"><button class="text-button" data-anchor-schedule="${esc(a.id)}">周表 ${(a.schedule || []).length ? `<span class="muted">${a.schedule.length}</span>` : ""} ${icon("calendar")}</button><button class="text-button" data-anchor-edit="${esc(a.id)}">编辑 ${icon("pencil")}</button><button class="text-button" data-anchor-open="${esc(a.id)}">直播间 ${icon("external")}</button><button class="text-button" data-anchor-profile="${esc(a.id)}">空间 ${icon("external")}</button></div></div></article>`;
  }).join("");
  html += `<button class="add-rule" data-action="addAnchor">${icon("plus")}添加主播</button><p class="hint">${icon("info")} 填写 B 站直播间号码（live.bilibili.com/ 后面的数字），应用会读取主播名称与头像。头像只保存在本机；识别不到时也可以自己填名称。「检测」关掉后不再检测这位主播；「响铃」关掉后照常检测、时间线照常亮起，只是不吵你。</p>`;
  $("#content").innerHTML = html;
}
function editAnchor(id) {
  const existing = (S.anchors || []).find((a) => a.id === id) || null;
  anchorDraft = existing ? { id: existing.id, name: existing.name, uid: existing.uid, room: existing.room, enabled: existing.enabled, alarm: existing.alarm !== false, avatar: !!existing.avatar } : { id: "", name: "", uid: 0, room: "", enabled: true, alarm: true, avatar: false };
  const d = anchorDraft;
  openModal(existing ? "编辑主播" : "添加主播", `<label class="form-label" for="anchor-room">B 站直播间号码</label><input class="input" id="anchor-room" inputmode="numeric" maxlength="12" value="${esc(String(d.room || ""))}" placeholder="例如 1713546334"><button class="secondary" data-action="resolveAnchor" style="margin-top:12px">${icon("refresh")}识别主播信息</button><button class="secondary" data-action="refreshAvatar" style="margin-top:10px">${icon("download")}重新下载头像</button><div id="anchor-preview"></div><label class="form-label" for="anchor-name">主播名称</label><input class="input" id="anchor-name" maxlength="40" value="${esc(d.name)}" placeholder="识别后自动填写"><div class="setting-row"><div class="grow"><h3>开播响铃</h3><p class="sub">关闭后仍检测并显示在时间线，但不响铃</p></div>${sw("draftAlarm", d.alarm !== false, "开播响铃开关")}</div><p class="hint">名称会显示在提醒通知里。改动房间号后需要重新识别，避免认错主播。</p><div class="form-error" id="anchor-error"></div>`, `<div class="sheet-actions">${existing ? '<button class="secondary outline" data-action="deleteAnchor">删除主播</button>' : '<button class="secondary outline" data-action="closeModal">取消</button>'}<button class="primary" data-action="saveAnchorDraft">保存主播</button></div>`);
  paintAnchorPreview();
}
function paintAnchorPreview() {
  const box = $("#anchor-preview");
  if (!box || !anchorDraft) return;
  const d = anchorDraft;
  box.innerHTML = d.room ? `<div class="anchor-preview">${anchorArt(d)}<div class="grow"><strong>${esc(d.name || "尚未识别")}</strong><small class="sub">${d.uid ? `<span class="nowrap">UID ${esc(String(d.uid))}</span> · <span class="nowrap">直播间 ${esc(String(d.room))}</span>` : "请先点“识别主播信息”"}</small></div></div>` : "";
}
async function resolveAnchorDraft() {
  const error = $("#anchor-error");
  if (error) error.textContent = "";
  const button = $('#modal [data-action="resolveAnchor"]');
  const room = Number(($("#anchor-room").value || "").trim());
  if (!Number.isFinite(room) || room <= 0) {
    if (error) error.textContent = "请填写直播间号码，例如 1713546334";
    return;
  }
  if (button) {
    button.disabled = true;
    button.textContent = "正在识别…";
  }
  try {
    const found = await api("resolveAnchor", { room, id: anchorDraft.id }, 4e4);
    anchorDraft = { ...anchorDraft, ...found };
    $("#anchor-name").value = found.name || "";
    $("#anchor-room").value = String(found.room);
    paintAnchorPreview();
    toast(found.avatar ? "已识别主播名称与头像" : "已识别主播名称，头像未能读取");
  } catch (e) {
    if (error) error.textContent = e.message;
  } finally {
    if (button) {
      button.disabled = false;
      button.innerHTML = `${icon("refresh")}识别主播信息`;
    }
  }
}
async function refreshAvatarDraft() {
  const error = $("#anchor-error");
  if (error) error.textContent = "";
  if (!anchorDraft.room) {
    if (error) error.textContent = "请先填写直播间号码";
    return;
  }
  const button = $('#modal [data-action="refreshAvatar"]');
  if (button) {
    button.disabled = true;
    button.textContent = "正在获取…";
  }
  try {
    const found = await api("resolveAnchor", { room: anchorDraft.room, id: anchorDraft.id }, 4e4);
    anchorDraft = { ...anchorDraft, ...found };
    $("#anchor-name").value = found.name || $("#anchor-name").value;
    paintAnchorPreview();
    toast(found.avatar ? "头像已更新" : "未能获取头像，稍后可再试");
  } catch (e) {
    if (error) error.textContent = e.message;
  } finally {
    if (button) {
      button.disabled = false;
      button.innerHTML = `${icon("download")}重新下载头像`;
    }
  }
}
async function saveAnchorDraft() {
  const error = $("#anchor-error");
  if (error) error.textContent = "";
  try {
    const room = Number(($("#anchor-room").value || "").trim()), name = ($("#anchor-name").value || "").trim();
    if (!Number.isFinite(room) || room <= 0) throw new Error("请填写直播间号码");
    if (!name) throw new Error("请填写主播名称");
    if (!anchorDraft.uid || Number(anchorDraft.room) !== room) throw new Error("请先点“识别主播信息”，确认是这个直播间");
    await putAnchor({ id: anchorDraft.id, name, uid: anchorDraft.uid, room, enabled: anchorDraft.enabled, alarm: anchorDraft.alarm !== false });
    closeModal();
    toast("主播已保存");
  } catch (e) {
    if (error) error.textContent = e.message;
  }
}
async function putAnchor(anchor) {
  stateRevision++;
  S.anchors = await api("saveAnchor", { anchor });
  render();
  await window.refreshNative(true);
}
async function toggleAnchor(id) {
  const a = (S.anchors || []).find((x) => x.id === id);
  if (!a || busy) return;
  busy = true;
  try {
    stateRevision++;
    S.anchors = await api("saveAnchor", { anchor: { id: a.id, name: a.name, uid: a.uid, room: a.room, enabled: !a.enabled, alarm: a.alarm !== false } });
    render();
    await window.refreshNative(true);
  } catch (e) {
    toast(e.message);
  } finally {
    busy = false;
  }
}
async function toggleRing(id) {
  const a = (S.anchors || []).find((x) => x.id === id);
  if (!a || busy) return;
  busy = true;
  try {
    stateRevision++;
    S.anchors = await api("saveAnchor", { anchor: { id: a.id, name: a.name, uid: a.uid, room: a.room, enabled: a.enabled, alarm: !a.alarm } });
    render();
    await window.refreshNative(true);
  } catch (e) {
    toast(e.message);
  } finally {
    busy = false;
  }
}
function askDeleteAnchor() {
  const a = (S.anchors || []).find((x) => x.id === anchorDraft.id) || anchorDraft;
  openModal("删除这位主播？", `<p class="body-copy">将从守候列表移除 ${esc(a.name || "这位主播")}，本机保存的头像与场次记录一并清除。不影响 B 站账号，也不影响其他主播。</p>`, `<div class="sheet-actions"><button class="secondary" data-action="closeModal">取消</button><button class="primary" data-action="confirmDeleteAnchor">删除</button></div>`);
}
async function removeAnchor() {
  const id = anchorDraft && anchorDraft.id;
  if (!id) return;
  try {
    stateRevision++;
    S.anchors = await api("deleteAnchor", { id });
    anchorDraft = null;
    closeModal();
    render();
    await window.refreshNative(true);
    toast("已删除");
  } catch (e) {
    toast(e.message);
  }
}
let scheduleDraft = null, schedDraftDays = 0, schedDraftStart = 1200, schedDraftEnd = -1;
const weekDays = ["一", "二", "三", "四", "五", "六", "日"];
function editSchedule(id) {
  const a = (S.anchors || []).find((x) => x.id === id);
  if (!a) return;
  scheduleDraft = { id, entries: clone(a.schedule || []), text: "", hasImage: !!a.scheduleImage };
  schedDraftDays = 0;
  schedDraftStart = 1200;
  schedDraftEnd = -1;
  openModal(a.name + " · 周表", '<div id="schedule-editor"></div>', '<div class="sheet-actions"><button class="secondary outline" data-action="closeModal">取消</button><button class="primary" data-action="saveSchedule">保存周表</button></div>');
  paintScheduleModal();
  api("getSchedule", { id }).then((info) => {
    if (!scheduleDraft || scheduleDraft.id !== id) return;
    scheduleDraft.text = info.text || "";
    scheduleDraft.hasImage = !!info.hasImage;
    if (info.entries && info.entries.length && !scheduleDraft.entries.length) scheduleDraft.entries = clone(info.entries);
    paintScheduleModal();
  }).catch(() => {
  });
}
function paintScheduleModal() {
  const box = $("#schedule-editor");
  if (!box || !scheduleDraft) return;
  const keepText = $("#sched-text") ? $("#sched-text").value : null;
  const rows = scheduleDraft.entries.map((e) => {
    const dl = [0, 1, 2, 3, 4, 5, 6].filter((i) => e.days & 1 << i).map((i) => weekDays[i]).join("、");
    const t = time(e.start) + (e.end >= 0 ? " – " + time(e.end) : "");
    return '<div class="sched-row"><div class="grow"><strong>' + t + '</strong><small class="sub">周' + dl + (e.note ? " · " + esc(e.note) : "") + '</small></div><button class="text-button" data-sched-del="' + esc(e.id) + '">删除 ' + icon("close") + "</button></div>";
  }).join("") || '<p class="sub">还没有安排。用下面逐条添加，或粘贴周表文字识别。</p>';
  box.innerHTML = '<div class="section-label"><h2>已有安排</h2><span style="display:flex;gap:10px;align-items:center"><small>' + scheduleDraft.entries.length + " / 16</small>" + (scheduleDraft.entries.length ? '<button class="text-button" data-action="clearScheduleDraft">清空全部</button>' : "") + "</span></div>" + rows + '<div class="section-label"><h2>添加安排</h2></div><div class="form-label">重复星期</div><div class="weekday-picker">' + weekDays.map((d, i) => '<button data-sched-weekday="' + i + '" class="' + (schedDraftDays & 1 << i ? "selected" : "") + '" aria-pressed="' + !!(schedDraftDays & 1 << i) + '" aria-label="周' + d + '">' + d + "</button>").join("") + '</div><div class="time-inputs" style="margin-top:10px"><label><span class="form-label">开始</span><input class="input" type="time" id="sched-start" value="' + time(schedDraftStart) + '"></label><label><span class="form-label">结束（可空）</span><input class="input" type="time" id="sched-end" value="' + (schedDraftEnd >= 0 ? time(schedDraftEnd) : "") + '"></label></div><input class="input" id="sched-note" maxlength="40" placeholder="备注，例如：杂谈 / 游戏回" style="margin-top:10px"><button class="secondary" data-action="addScheduleEntry" style="margin-top:12px">' + icon("plus") + '添加这条安排</button><div class="section-label"><h2>从文字识别</h2></div><textarea class="input" id="sched-text" rows="4" placeholder="粘贴周表文字，例如：周五 20:00 游戏">' + esc((keepText !== null ? keepText : scheduleDraft.text) || "") + '</textarea><button class="secondary" data-action="parseScheduleText" style="margin-top:10px">' + icon("spark") + '识别为安排</button><p class="hint">识别结果只进上面的列表，保存前可逐条修改；也可以上传周表图片对照。</p>' + (scheduleDraft.hasImage ? '<div class="section-label"><h2>周表图片对照</h2></div><div class="sched-img"><img src="/schedule/' + esc(scheduleDraft.id) + '.img" alt="周表图片"><button class="text-button" data-action="removeScheduleImage" style="margin-top:6px">删除图片 ' + icon("close") + "</button></div>" : "") + '<button class="secondary" data-action="pickScheduleImage" style="margin-top:10px">' + icon("upload") + "上传或更换周表图片</button>" + (scheduleDraft.hasImage ? '<button class="secondary" data-action="ocrSchedule" style="margin-top:10px">' + icon("spark") + "AI 识别图片安排</button>" : "") + '<p class="hint">图片显示在列表上方，方便边看边录；只保存在本机，应用不做图片识别。</p><div class="form-error" id="sched-error"></div>';
}
function addScheduleEntry() {
  const error = $("#sched-error");
  if (error) error.textContent = "";
  if (!scheduleDraft) return;
  const start = $("#sched-start").value, end = $("#sched-end").value, note = ($("#sched-note").value || "").trim();
  if (!start) {
    if (error) error.textContent = "请选择开始时间";
    return;
  }
  if (!schedDraftDays) {
    if (error) error.textContent = "请至少选择一个星期";
    return;
  }
  const minutes = (s) => Number(s.slice(0, 2)) * 60 + Number(s.slice(3));
  const e = { id: "w" + Date.now().toString(36) + scheduleDraft.entries.length, days: schedDraftDays, start: minutes(start), end: end ? minutes(end) : -1, note };
  if (scheduleDraft.entries.length >= 16) {
    if (error) error.textContent = "每位主播最多 16 条安排";
    return;
  }
  scheduleDraft.entries.push(e);
  paintScheduleModal();
}
function parseScheduleLine(line) {
  const map = { "一": 0, "二": 1, "三": 2, "四": 3, "五": 4, "六": 5, "日": 6, "天": 6, "末": -1 };
  let days = 0, m;
  const re = /周([一二三四五六日天末])|礼拜([一二三四五六日天末])|星期([一二三四五六日天末])/g;
  while (m = re.exec(line)) {
    const ch = m[1] || m[2] || m[3];
    if (ch === "末") {
      days |= 1 << 5;
      days |= 1 << 6;
    } else days |= 1 << map[ch];
  }
  if (!days) return null;
  const times = [];
  const tm = /(\d{1,2})[:：点时](\d{2})?分?/g;
  while (m = tm.exec(line)) {
    const h = Number(m[1]), min = m[2] ? Number(m[2]) : 0;
    if (h < 24 && min < 60) times.push(h * 60 + min);
  }
  if (!times.length) return null;
  let note = line.replace(re, " ").replace(/(\d{1,2})[:：点时](\d{2})?分?/g, " ").replace(/[【\[\]\s:：\-—]+/g, " ").trim();
  return { days, start: times[0], end: times.length > 1 ? times[1] : -1, note: note.slice(0, 40) };
}
async function parseScheduleIntoDraft() {
  const error = $("#sched-error");
  if (error) error.textContent = "";
  if (!scheduleDraft) return;
  const text = ($("#sched-text").value || "").trim();
  if (!text) {
    if (error) error.textContent = "请先粘贴周表文字";
    return;
  }
  try {
    const source = text;
    const parsed = source.split(/\n+/).map(parseScheduleLine).filter(Boolean);
    if (!parsed.length) {
      if (error) error.textContent = "没有识别出「周几 + 时间」的组合，请手动添加或调整文字";
      return;
    }
    if (scheduleDraft.entries.length + parsed.length > 16) {
      if (error) error.textContent = "每位主播最多 16 条安排";
      return;
    }
    let n = 0;
    for (const p of parsed) {
      p.id = "w" + Date.now().toString(36) + "p" + n++;
      scheduleDraft.entries.push(p);
    }
    scheduleDraft.text = source;
    paintScheduleModal();
    toast("识别出 " + parsed.length + " 条安排，请确认后保存");
  } catch (e) {
    if (error) error.textContent = e.message;
  }
}
function parseAiLine(line) {
  const parts = String(line).split("|").map((s) => s.trim());
  if (parts.length < 3) return null;
  const minutes = (s) => {
    s = String(s).trim();
    const c = s.split(":");
    if (c.length !== 2) return -1;
    const h = Number(c[0]), mm = Number(c[1]);
    return Number.isFinite(h) && Number.isFinite(mm) && h < 24 && mm < 60 && c[0].length <= 2 && c[1].length === 2 ? h * 60 + mm : -1;
  };
  const start = minutes(parts[1]);
  if (start < 0) return null;
  const end = parts[2] ? minutes(parts[2]) : -1;
  let datePart = "", note = "";
  if (parts.length >= 5) {
    datePart = parts[3];
    note = parts.slice(4).join("|");
  } else note = parts.slice(3).join("|");
  let d = Number(parts[0]);
  let noteText = (note || "").slice(0, 40);
  if (datePart) {
    const wd = weekdayOfDate(datePart);
    if (wd !== null) d = wd;
    noteText = (datePart + " " + (note || "")).trim().slice(0, 40);
  }
  if (!(d >= 0 && d <= 6)) return null;
  return { days: 1 << d, start, end: end >= 0 ? end : -1, note: noteText };
}
function weekdayOfDate(text) {
  const t = String(text).trim();
  let y, mo, dy;
  let m = t.match(/^(\d{4})[\/\-.](\d{1,2})[\/\-.](\d{1,2})$/);
  if (m) {
    y = Number(m[1]);
    mo = Number(m[2]);
    dy = Number(m[3]);
  } else {
    m = t.match(/^(\d{1,2})[\/\-.月](\d{1,2})日?$/);
    if (m) {
      const now = /* @__PURE__ */ new Date();
      y = now.getFullYear();
      mo = Number(m[1]);
      dy = Number(m[2]);
      if (new Date(y, mo - 1, dy).getTime() < new Date(y, now.getMonth(), now.getDate()).getTime() - 864e5) y += 1;
    } else {
      m = t.match(/^(\d{1,2})号$/);
      if (!m) return null;
      const now = /* @__PURE__ */ new Date();
      y = now.getFullYear();
      mo = now.getMonth() + 1;
      dy = Number(m[1]);
    }
  }
  if (!(y >= 2e3 && y <= 2100 && mo >= 1 && mo <= 12 && dy >= 1 && dy <= 31)) return null;
  return (new Date(y, mo - 1, dy).getDay() + 6) % 7;
}
async function ocrScheduleIntoDraft() {
  const error = $("#sched-error");
  if (error) error.textContent = "";
  if (!scheduleDraft) return;
  if (S.config.aiOcr === false) {
    toast("请先在设置里开启 AI 图片识别");
    return;
  }
  const button = $('#modal [data-action="ocrSchedule"]');
  if (button) {
    button.disabled = true;
    button.textContent = "AI 识别中，约需十几秒…";
  }
  try {
    const text = await api("ocrScheduleImage", { id: scheduleDraft.id }, 3e5);
    const parsed = text.split(String.fromCharCode(10)).map((s) => s.trim()).filter(Boolean).map(parseAiLine).filter(Boolean);
    if (!parsed.length) {
      const box = $("#sched-text");
      if (box) box.value = text;
      if (error) error.textContent = "AI 返回的内容没有解析出安排，原文已放入文本框；可修改后点「识别为安排」";
      return;
    }
    if (scheduleDraft.entries.length + parsed.length > 16) {
      if (error) error.textContent = "加入后超过 16 条，请先删除部分再识别";
      return;
    }
    let n = 0;
    for (const p of parsed) {
      p.id = "w" + Date.now().toString(36) + "a" + n++;
      scheduleDraft.entries.push(p);
    }
    paintScheduleModal();
    toast("AI 识别出 " + parsed.length + " 条安排，请确认后保存");
  } catch (e) {
    if (error) error.textContent = e.message;
  } finally {
    if (button) {
      button.disabled = false;
      button.innerHTML = icon("spark") + "AI 识别图片安排";
    }
  }
}
async function saveScheduleDraft() {
  const error = $("#sched-error");
  if (error) error.textContent = "";
  if (!scheduleDraft) return;
  try {
    await api("saveSchedule", { id: scheduleDraft.id, entries: scheduleDraft.entries });
    const box = $("#sched-text");
    if (box) await api("saveScheduleText", { id: scheduleDraft.id, text: box.value });
    closeModal();
    await window.refreshNative(true);
    toast("周表已保存");
  } catch (e) {
    if (error) error.textContent = e.message;
  }
}
function viewScheduleImage(id) {
  openModal("周表图片", '<div class="sched-img"><img src="/schedule/' + esc(id) + '.img" alt="周表图片"></div><p class="hint">图片仅保存在本机，用于查看。</p>', '<div class="sheet-actions"><button class="primary" data-action="closeModal">关闭</button></div>');
}
function week() {
  const list = S.anchors || [];
  const today = ((/* @__PURE__ */ new Date()).getDay() + 6) % 7;
  const rows = list.flatMap((a) => (a.schedule || []).map((e) => ({ a, e })));
  let html = heading("WEEKLY TIMELINE", "这一周，谁在等你。", "来自主播周表；是否响铃仍由时段与响铃开关决定。");
  if (!rows.length && !list.some((a) => a.scheduleImage)) html += '<div class="card"><p class="sub">还没有任何周表。到“主播”页，在主播卡片上点「周表」添加安排；也可以粘贴周表文字识别，或上传周表图片。</p><button class="text-button" data-route="anchors">去主播页 ' + icon("arrow") + "</button></div>";
  for (let off = 0; off < 7; off++) {
    const d = (today + off) % 7;
    const dayRows = rows.filter((pair) => pair.e.days & 1 << d).sort((x, y) => x.e.start - y.e.start);
    if (!dayRows.length) continue;
    html += '<div class="section-label"><h2>周' + weekDays[d] + (off === 0 ? " · 今天" : off === 1 ? " · 明天" : "") + "</h2><small>" + dayRows.length + " 场</small></div>";
    html += dayRows.map((pair) => {
      const a = pair.a, e = pair.e;
      const live = (a.snapshot || {}).status === 1 && a.enabled;
      const t = time(e.start) + (e.end >= 0 ? " – " + time(e.end) : "");
      const cov = live && a.snapshot && a.snapshot.cover ? '<img class="sched-cover" src="/cover/?u=' + encodeURIComponent(a.snapshot.cover) + '" alt="">' : "";
      return '<article class="card sched-card ' + (a.enabled ? "" : "disabled-rule") + '"><div class="row">' + anchorArt(a) + '<div class="grow"><div class="anchor-name">' + esc(a.name) + '</div><div class="live-info"><span class="dot ' + (live ? "live" : "") + '"></span><span class="live-state">' + (live ? "正在直播" : "计划开播") + "</span>" + (e.note ? '<small class="sub"> · ' + esc(e.note) + "</small>" : "") + "</div></div>" + cov + "<strong>" + t + "</strong></div></article>";
    }).join("");
  }
  const withImg = list.filter((a) => a.scheduleImage);
  if (withImg.length) html += '<div class="section-label"><h2>周表图片</h2></div>' + withImg.map((a) => '<article class="card"><div class="row">' + anchorArt(a) + '<div class="grow"><strong>' + esc(a.name) + '</strong><small class="sub">上传的周表图片</small></div><button class="text-button" data-action="viewScheduleImage" data-anchor="' + esc(a.id) + '">查看 ' + icon("arrow") + "</button></div></article>").join("");
  if (rows.length) html += '<p class="hint">' + icon("info") + " 周表由你填写或识别，可能与实际开播不同；开播提醒仍按直播状态实时判断。</p>";
  $("#content").innerHTML = html;
}
function schedule() {
  const c = S.config;
  let bars = "";
  if (c.allDay) bars = '<span style="left:0;width:100%"></span>';
  else for (const w of c.windows.filter((x) => x.enabled)) {
    if (w.end > w.start) bars += `<span style="left:${w.start / 14.4}%;width:${(w.end - w.start) / 14.4}%"></span>`;
    else if (w.end === w.start) bars += '<span style="left:0;width:100%"></span>';
    else bars += `<span style="left:${w.start / 14.4}%;width:${(1440 - w.start) / 14.4}%"></span><span style="left:0;width:${w.end / 14.4}%"></span>`;
  }
  let html = heading("YOUR QUIET HOURS", "只在你想听到时响起。", "一天中的任何时段，都由你来定义。") + `<div class="segmented"><button data-all-day="true" class="${c.allDay ? "selected" : ""}">全天提醒</button><button data-all-day="false" class="${!c.allDay ? "selected" : ""}">自定义时段</button></div><section class="card"><div class="row between"><h3>24 小时时间轴</h3><small class="muted">${c.allDay ? "全天开启" : "选定时段的并集"}</small></div><div class="timeline">${bars}</div><div class="timeline-ticks"><span>00:00</span><span>06:00</span><span>12:00</span><span>18:00</span><span>24:00</span></div><p class="sub" style="margin-top:12px">${c.allDay ? "任何时间开播都可提醒。下方时段在切换到自定义模式后生效。" : "按每条规则所选星期重复；重叠时段合并生效。"}</p></section><div class="section-label"><h2>我的提醒时段</h2><small>${c.windows.length} / 32</small></div>`;
  html += c.windows.map((w) => `<article class="card time-card ${w.enabled ? "" : "disabled-rule"}"><div class="row"><div class="grow"><div class="rule-name">${icon(w.start >= 1080 || w.start < 360 ? "moon" : "sun")}${esc(w.name || "自定义时段")}</div><div class="time-range">${time(w.start)}<span class="time-arrow">—</span>${time(w.end)}</div></div><button class="switch" role="switch" aria-checked="${w.enabled}" aria-label="${esc(w.name)}启用状态" data-rule-toggle="${esc(w.id)}"></button></div><div class="rule-days">${["一", "二", "三", "四", "五", "六", "日"].map((d, i) => `<span class="day-pill ${w.days & 1 << i ? "on" : ""}">${d}</span>`).join("")}</div><div class="rule-footer"><small class="muted">${w.end === w.start ? "持续 24 小时" : w.end < w.start ? "跨午夜 · 结束在次日" : "当天时段"}${w.days === 127 ? " · 每天" : ""}</small><button class="text-button" data-edit-rule="${esc(w.id)}">编辑 ${icon("arrow")}</button></div></article>`).join("");
  html += `<button class="add-rule" data-action="addRule">${icon("plus")}添加提醒时段</button><div class="section-label"><h2>一键添加</h2></div><div class="chips"><button class="chip" data-preset="night">凌晨 01:00–06:00</button><button class="chip" data-preset="overnight">深夜 23:00–07:00</button><button class="chip" data-preset="evening">晚间 19:00–23:00</button></div><section class="card">${setting("提醒时区", `当前：${esc(S.zone)}`, '<button class="text-button" data-action="chooseTimezone">选择时区 ›</button>')}${setting("补报已开播", "开启守候或进入时段时，若已经在播也提醒；本场仍只自动提醒一次。", sw("catchUp", c.catchUp, "补报已开播"))}${setting("按周表预告提醒", "周表开播前 5 分钟发一条提醒通知，不响铃；仅对已开启响铃的主播。", sw("preStream", c.preStream !== false, "按周表预告提醒"))}</section><p class="hint">${icon("info")} 关闭补报时，仅提醒在所选时段内开始的直播。起点包含，终点不包含；跨午夜时段按开始那天的星期计算。若接口缺少开播时间，则按首次检测时间判断；首次开启时不猜测已在播的开播时间。</p>`;
  $("#content").innerHTML = html;
}
function sound() {
  const c = S.config;
  let html = heading("MAKE IT YOURS", "这一声，为你而响。", "选喜欢的铃声，也选一个能听见的音量。所有主播共用。");
  html += `<section class="card"><div class="volume-header"><div><h3>响铃音量</h3><p class="sub">使用系统闹钟音量通道</p></div><div class="volume-number" id="volume-value">${c.volume}<span>%</span></div></div><input id="volume" aria-label="响铃音量百分比" class="volume-track" type="range" min="1" max="100" value="${c.volume}"><div class="volume-labels"><span>轻一些</span><span>响一些</span></div></section><div class="section-label"><h2>选择铃声</h2><small>内置原创音色</small></div><div class="tones">${[["starlight", "spark", "星铃", "明亮清脆 · 默认"], ["morning", "sun", "清晨", "柔和起音 · 温暖"], ["urgent", "bell", "强提醒", "节奏明显 · 更醒神"], ["system", "music", "系统闹钟", "使用手机默认铃声"]].map(([id, i, name, desc]) => `<button class="tone ${c.ringtone === id ? "selected" : ""}" data-tone="${id}" aria-pressed="${c.ringtone === id}">${icon(i)}<div class="tone-copy"><strong>${name}</strong><small>${desc}</small></div></button>`).join("")}</div><button class="audio-file" data-action="pickAudio"><div class="label-icon">${icon("file")}</div><div class="grow"><strong>从手机选择铃声 ${c.ringtone === "custom" ? "✓" : ""}</strong><small>${esc(c.customName === "未选择" ? "MP3、M4A、OGG、WAV · 30 MB 以内" : c.customName)}</small></div>${icon("plus")}</button>`;
  if (c.customName !== "未选择" && c.ringtone !== "custom") html += `<button class="secondary" data-tone="custom" style="margin-bottom:16px">使用上次导入的铃声</button>`;
  html += `<section class="card">${setting("渐强响铃", "从轻到响，约 5 秒达到设定音量。", sw("ramp", c.ramp, "渐强响铃"))}${setting("同时振动", "按节奏循环振动，直到响铃结束。", sw("vibrate", c.vibrate, "响铃振动"))}${setting("自动停止", "避免错过后长时间持续响铃。", select("duration", c.duration, [[15, "15 秒"], [30, "30 秒"], [60, "1 分钟"], [120, "2 分钟"], [300, "5 分钟"]]))}${setting("暂缓多久", "点击“稍后提醒”后再次确认直播。", select("snoozeMinutes", c.snoozeMinutes, [[3, "3 分钟"], [5, "5 分钟"], [10, "10 分钟"], [15, "15 分钟"]]))}${setting("多主播接续响铃", "第一位响铃结束后，期间开播的其他主播按顺序接续提醒；关闭则只记录不补响。", sw("ringQueue", c.ringQueue === true, "多主播接续响铃"))}${setting("通话时不强响铃", "检测到通话模式时，只显示通知并按设置振动。", sw("quietCalls", c.quietCalls, "通话时不强响铃"))}</section><button class="primary" data-action="testDialog">${icon("play")}试一试这声铃响</button><p class="hint">响铃期间临时调整闹钟音量；结束后恢复。若你在响铃时手动改变系统音量，应用保留你的调整。静音、勿扰及耳机的具体表现，请通过锁屏测试确认。</p>`;
  $("#content").innerHTML = html;
}
function settings() {
  const c = S.config, p = S.permissions, rows = [["notifications", "bell", "允许通知", p.notifications ? "系统已允许发送通知" : p.notificationPolicy === "revoked" ? "系统策略拒绝授权，点按查看排查方法" : p.notificationMismatch ? "授权状态尚未一致，请打开系统通知设置检查" : p.notificationRuntime === false ? "请允许系统通知授权，并开启应用通知" : "接收开播提醒与守候状态", p.notifications], ["alarmChannel", "sound", "开播强提醒通道", "请保留横幅与锁屏显示", p.alarmChannel], ["fullScreen", "screen", "全屏提醒", "锁屏时显示可操作的提醒页面", p.fullScreen], ["battery", "battery", "后台电池权限", "允许后台运行，减少检测中断", p.battery], ["exact", "clock", "精确闹钟", "用于时段边界、暂缓与锁屏测试", p.exact], ["accessibility", "shield", "无障碍守候辅助（可选）", p.accessibilityConnected ? "已连接；系统清理本应用后由系统重新拉起并恢复守候" : "不读取屏幕；从最近任务划掉后恢复最快的一条路", p.accessibility], ["dnd", "moon", "勿扰模式", "如使用勿扰，请在系统中允许闹钟", !p.dnd]];
  let html = heading("A RELIABLE LITTLE WATCH", "把每次提醒，照顾好。", "权限是否允许，由你的手机最终决定。") + `<div class="section-label"><h2>权限检查</h2><small>点按可进入系统设置</small></div><section class="card permissions-card">${rows.map(([key, i, name, desc, ok]) => `<button class="permission-row" data-permission="${key}">${icon(i)}<span class="grow"><strong>${name}</strong><small>${desc}</small></span><span class="badge ${ok ? "" : "warn"}">${key === "dnd" ? ok ? "未开启" : "请检查" : ok ? "已就绪" : key === "notifications" && p.notificationPolicy === "revoked" ? "策略限制" : "去设置"}</span><span class="chevron">›</span></button>`).join("")}</section><div class="permission-actions"><button class="text-button" data-permission="notificationSettings">打开系统通知设置 ${icon("external")}</button><button class="text-button" data-action="recheckPermissions">重新检查 ${icon("refresh")}</button></div><div class="card warning"><p>小米 / Redmi、华为、荣耀、OPPO、vivo 等手机，还可能需要在系统中允许自启动，把后台电池策略设为“不限制”，并在最近任务里长按本应用的卡片将它锁定。无法由应用自动验证。</p><p class="hint" style="margin-top:5px">荣耀 MagicOS：设置 → 应用和服务 → 应用启动管理，找到VR闹钟，关闭“自动管理”后勾选允许自启动、允许关联启动、允许后台活动。</p><button class="text-button" data-permission="app" style="margin-top:5px">打开应用系统设置 ${icon("arrow")}</button></div><div class="section-label"><h2>AI 图片识别（周表）</h2></div><section class="card">${setting("AI 识别周表图片", "上传周表图片后可让 AI 自动提取安排；图片会上传到你配置的 AI 服务商处理，默认开启，可随时关闭。", sw("aiOcr", c.aiOcr !== false, "AI 图片识别"))}<div class="setting-row"><div class="grow"><h3>接口密钥</h3><p class="sub">DeepSeek 兼容接口；仅保存在本机</p></div></div><input class="input" data-setting="aiKey" value="${esc(c.aiKey || "")}" placeholder="sk-…" style="margin-top:6px"><div class="setting-row"><div class="grow"><h3>模型</h3><p class="sub">需要支持图片输入，例如 deepseek-flash</p></div></div><input class="input" data-setting="aiModel" value="${esc(c.aiModel || "deepseek-flash")}" style="margin-top:6px"></section><div class="section-label"><h2>小米与通知兼容</h2></div><section class="card">${setting("通知异常时仍响铃", "通知未获准时，继续按时段检测并播放闹铃。通知栏与锁屏卡片可能不显示。", sw("soundWithoutNotifications", c.soundWithoutNotifications, "通知异常时仍响铃"))}<button class="permission-row" data-permission="overlay">${icon("screen")}<span class="grow"><strong>悬浮关闭按钮 · 可选</strong><small>仅响铃时显示，方便在其他应用上关闭闹铃。</small></span><span class="badge ${p.overlay ? "" : "warn"}">${p.overlay ? "已就绪" : "去设置"}</span></button></section><div class="section-label"><h2>后台守候</h2></div>${watchNoticeCard()}<section class="card">${setting("优先保证提醒", "提醒时段内保持持续检测，更耗电；长时间守候建议接通电源。", sw("reliable", c.reliable, "优先保证提醒"))}${setting("检测间隔", "网络异常会逐步放慢重试；时段外约 3 分钟检查一次。", select("pollSeconds", c.pollSeconds, [[15, "15 秒"], [30, "30 秒"], [60, "60 秒"], [120, "120 秒"]]))}${setting("重启后恢复守候", "仅恢复此前已开启的守候；仍受系统自启动限制。", sw("boot", c.boot, "重启后恢复守候"))}${setting("定时恢复检查", "约每 15 分钟确认守候服务是否还活着，中断时自动拉起；不影响正常检测节奏，系统休眠时可能推迟。", sw("recovery", c.recovery !== false, "定时恢复检查"))}${S.recoveryAt ? `<p class="sub" style="padding:0 4px 10px;margin-top:-8px">已安排恢复检查 · ${stamp(S.recoveryAt)}</p>` : ""}${setting("从最近任务隐藏缩略图", "最近任务卡片里隐藏应用内容，减少误划与窥屏；不影响提醒功能。", sw("hideRecents", c.hideRecents, "隐藏最近任务缩略图"))}</section><section class="card">${setting("界面主题", "选择舒服的明暗。", select("theme", c.theme, [["light", "奶白"], ["dark", "夜色"], ["system", "跟随系统"]]))}</section><div class="section-label"><h2>外观与个性化</h2><small>配色方案与背景图片</small></div><section class="card"><div class="setting-row"><div class="grow"><h3>主题色</h3><p class="sub">${c.seedColor ? "当前 " + esc(c.seedColor) : "使用内置蓝灰配色"}</p></div></div><div class="swatches">${[["", "#516b82"], ["#A65C83", "#A65C83"], ["#6D7DB4", "#6D7DB4"], ["#4F7FA4", "#4F7FA4"], ["#447A6A", "#447A6A"], ["#88743C", "#88743C"], ["#B36A46", "#B36A46"], ["#9865AB", "#9865AB"], ["#B85872", "#B85872"], ["#536B81", "#536B81"], ["#6D7650", "#6D7650"], ["#B2748C", "#B2748C"], ["#655C74", "#655C74"]].map(([hex, bg]) => `<button class="swatch ${(c.seedColor || "") === hex ? "selected" : ""}" style="background:${bg}" aria-label="主题色 ${hex || "默认"}" data-seed="${hex}">${(c.seedColor || "") === hex ? "✓" : ""}</button>`).join("")}</div><input class="input" data-setting="seedColor" value="${esc(c.seedColor || "")}" placeholder="留空使用默认，或输入 #536B81" style="margin-top:10px">${setting("AMOLED 纯黑模式", "深色主题使用纯黑底色；自选背景仍按你的设置显示。", sw("amoled", c.amoled, "AMOLED 纯黑模式"))}</section><div class="section-label"><h2>背景图片</h2><small>只读取你选择的图片，不需要访问整个相册</small></div><section class="card"><div class="setting-row"><div class="grow"><h3>${S.backgroundSet ? "已设置背景" : "还没有设置背景"}</h3><p class="sub">${S.backgroundSet ? esc(S.backgroundName || "自选背景") : "选择一张喜欢的图片作为应用背景"}</p></div></div><div class="bg-actions"><button class="secondary" data-action="pickBackground">选择图片</button>${S.backgroundSet ? '<button class="secondary outline" data-action="removeBackground">移除背景</button>' : ""}</div><div class="slider-row"><div class="grow"><h3>背景遮罩</h3><p class="sub">压暗背景，保证文字可读</p></div><span class="muted">${c.backgroundDim || 0}%</span></div><input aria-label="背景遮罩百分比" class="volume-track" type="range" min="0" max="90" value="${c.backgroundDim || 0}" data-setting="backgroundDim" style="margin:8px 0 2px"><div class="slider-row"><div class="grow"><h3>卡片不透明度</h3><p class="sub">越低透出的背景越多</p></div><span class="muted">${c.cardOpacity || 94}%</span></div><input aria-label="卡片不透明度百分比" class="volume-track" type="range" min="75" max="100" value="${c.cardOpacity || 94}" data-setting="cardOpacity" style="margin:8px 0 2px"><p class="hint" style="margin:8px 0 0">支持 JPG、PNG、WebP，最大 20 MB。图片复制并缩放后只保存在应用内；备份不包含背景图片。</p></section><div class="section-label"><h2>数据与帮助</h2></div><section class="card" style="padding-top:5px;padding-bottom:5px">${[["notificationHelp", "bell", "通知授权帮助"], ["notificationReport", "download", "导出通知排查包"], ["testDialog", "shield", "锁屏与响铃测试"], ["export", "download", "导出设置与诊断记录"], ["import", "upload", "从备份恢复设置"], ["privacy", "info", "隐私与使用说明"]].map(([action, i, label]) => `<button class="plain-row" data-action="${action}">${icon(i)}<span>${label}</span><span class="chevron">›</span></button>`).join("")}</section><p class="footnote">VR闹钟 ${esc(S.version)} · 本机守候 ${enabledAnchors().length} 位主播<br>主播可在“主播”页随时增删或关闭<br>非哔哩哔哩或主播官方应用</p>`;
  if (p.powerSave) html = `<div class="card warning"><p>手机当前处于省电模式，后台提醒可能延迟。</p></div>` + html;
  $("#content").innerHTML = html;
}
async function history() {
  const parent = $("#content");
  parent.innerHTML = heading("EVERY LITTLE MOMENT", "每一声，都有记录。", "最近 200 条记录仅保存在你的手机。") + `<div class="section-label"><h2>通知与运行记录</h2><button class="text-button" data-action="clearHistory">清空</button></div><div id="history-list" class="card"></div>`;
  try {
    const list = await api("history");
    if (route !== "history") return;
    $("#history-list").innerHTML = list.length ? list.map((e) => `<article class="history-entry"><div class="label-icon ${e.type === "live" ? "green" : e.type === "warning" ? "amber" : ""}">${icon(e.type === "live" ? "bell" : e.type === "warning" ? "info" : e.type === "snooze" ? "clock" : e.type === "test" ? "sound" : "history")}</div><div class="grow"><h3>${esc(e.title)}</h3><p>${esc(e.detail)}</p><time>${stamp(e.at, true)}</time></div></article>`).join("") : `<div class="empty"><div class="label-icon">${icon("history")}</div><h2>故事还没开始</h2><p>开启守候或完成一次响铃测试后，<br>这里就会留下记录。</p></div>`;
  } catch (e) {
    toast(e.message);
  }
}
function openModal(title, body, actions = "") {
  var _a;
  const m = $("#modal");
  m.innerHTML = `<section class="sheet" role="dialog" aria-modal="true" aria-label="${esc(title)}"><div class="sheet-head"><h2>${esc(title)}</h2><button data-action="closeModal" aria-label="关闭弹窗">${icon("close")}</button></div>${body}${actions}</section>`;
  m.hidden = false;
  document.body.style.overflow = "hidden";
  (_a = m.querySelector("button")) == null ? void 0 : _a.focus();
}
function closeModal() {
  const m = $("#modal");
  if (!m) return;
  m.hidden = true;
  m.innerHTML = "";
  document.body.style.overflow = "";
  ruleDraft = null;
  anchorDraft = null;
}
function editRule(id, preset) {
  const existing = S.config.windows.find((w2) => w2.id === id);
  ruleDraft = existing ? clone(existing) : { id: "r" + Date.now(), name: "自定义时段", start: 60, end: 360, days: 127, enabled: true };
  if (preset) {
    const sets = { night: ["凌晨守候", 60, 360], overnight: ["深夜守候", 1380, 420], evening: ["晚间守候", 1140, 1380] };
    const [name, start, end] = sets[preset];
    Object.assign(ruleDraft, { name, start, end });
  }
  const w = ruleDraft;
  openModal(existing ? "编辑提醒时段" : "添加提醒时段", `<label class="form-label" for="rule-name">时段名称</label><input class="input" id="rule-name" maxlength="24" value="${esc(w.name)}"><div class="time-inputs"><label><span class="form-label">开始时间</span><input class="input" type="time" id="rule-start" value="${time(w.start)}"></label><label><span class="form-label">结束时间</span><input class="input" type="time" id="rule-end" value="${time(w.end)}"></label></div><div class="form-label">重复星期（按开始那天计算）</div><div class="weekday-picker">${["一", "二", "三", "四", "五", "六", "日"].map((d, i) => `<button data-weekday="${i}" class="${w.days & 1 << i ? "selected" : ""}" aria-pressed="${!!(w.days & 1 << i)}" aria-label="星期${d}">${d}</button>`).join("")}</div><p class="hint">结束早于开始，自动跨到次日。开始与结束相同，表示从该时刻起持续 24 小时。保存后会切换为自定义时段模式。</p><div class="form-error" id="rule-error"></div>`, `<div class="sheet-actions">${existing ? '<button class="secondary outline" data-action="deleteRule">删除时段</button>' : '<button class="secondary outline" data-action="closeModal">取消</button>'}<button class="primary" data-action="saveRule">保存时段</button></div>`);
}
async function save(patch, notify = false) {
  const c = await api("save", patch);
  stateRevision++;
  S.config = c;
  configFingerprint = JSON.stringify(c);
  render();
  await window.refreshNative(true);
  if (notify) toast("已保存");
}
async function saveRule() {
  const error = $("#rule-error");
  try {
    const start = $("#rule-start").value, end = $("#rule-end").value, name = $("#rule-name").value.trim();
    if (!start || !end) throw new Error("请选择开始和结束时间");
    if (!ruleDraft.days) throw new Error("请至少选择一个星期");
    const minutes = (s) => Number(s.slice(0, 2)) * 60 + Number(s.slice(3));
    const w = { ...ruleDraft, name: name || "自定义时段", start: minutes(start), end: minutes(end) };
    let rules = S.config.windows.filter((x) => x.id !== w.id);
    rules.push(w);
    if (rules.length > 32) throw new Error("最多支持 32 个提醒时段");
    await save({ windows: rules, allDay: false });
    closeModal();
    toast("提醒时段已保存");
  } catch (e) {
    error.textContent = e.message;
  }
}
const zoneNames = { "Asia/Shanghai": "中国 · 北京 / 上海", "Asia/Tokyo": "日本 · 东京", "Asia/Seoul": "韩国 · 首尔", "Asia/Hong_Kong": "中国 · 香港", "Asia/Taipei": "中国 · 台北", "Asia/Singapore": "新加坡", "Asia/Kathmandu": "尼泊尔 · 加德满都", "Asia/Kolkata": "印度 · 加尔各答", "Asia/Dubai": "阿联酋 · 迪拜", "Asia/Bangkok": "泰国 · 曼谷", "America/New_York": "美国 · 纽约 / 东部", "America/Chicago": "美国 · 芝加哥 / 中部", "America/Denver": "美国 · 丹佛 / 山地", "America/Los_Angeles": "美国 · 洛杉矶 / 太平洋", "America/Toronto": "加拿大 · 多伦多", "America/Vancouver": "加拿大 · 温哥华", "America/Sao_Paulo": "巴西 · 圣保罗", "Europe/London": "英国 · 伦敦", "Europe/Paris": "法国 · 巴黎", "Europe/Berlin": "德国 · 柏林", "Europe/Moscow": "俄罗斯 · 莫斯科", "Australia/Sydney": "澳大利亚 · 悉尼", "Australia/Perth": "澳大利亚 · 珀斯", "Pacific/Auckland": "新西兰 · 奥克兰", "UTC": "协调世界时" };
async function chooseTimezone() {
  zoneList = await api("zones");
  openModal("全球时区", `<p class="body-copy">提醒范围按所选地区当地时间计算，自动适应该地区夏令时。跟随手机会在旅行或手机时区改变时自动调整。</p><input class="input" style="margin:16px 0" id="tz-search" type="search" placeholder="搜索国家、城市或时区，如 东京 / New_York" aria-label="搜索全球时区"><div id="timezone-list"></div>`);
  renderZones("");
}
function renderZones(query) {
  const q = query.trim().toLowerCase();
  const rows = zoneList.filter((z) => (z.id + " " + (zoneNames[z.id] || "") + " UTC" + z.offset).toLowerCase().includes(q));
  rows.sort((a, b) => (zoneNames[a.id] ? 0 : 1) - (zoneNames[b.id] ? 0 : 1) || a.id.localeCompare(b.id));
  const device = `<button class="timezone-row" data-zone="device"><span class="grow"><strong>跟随手机时区</strong><small>${esc(S.deviceZone)}</small></span>${S.config.timezone === "device" ? icon("check") : icon("refresh")}</button>`;
  $("#timezone-list").innerHTML = (!q ? device : "") + rows.map((z) => `<button class="timezone-row" data-zone="${esc(z.id)}"><span class="grow"><strong>${esc(zoneNames[z.id] || z.id.replace(/_/g, " "))}</strong><small>${esc(z.id)}${z.offset ? " · UTC" + esc(z.offset) : ""}</small></span><span class="muted small">${esc(z.time)}</span>${S.config.timezone === z.id ? icon("check") : ""}</button>`).join("") + (rows.length ? "" : '<p class="hint">没有找到，请试试英文城市名，如 London。</p>');
}
function accessibilityInfo() {
  openModal("可选的守候辅助", `<div class="body-copy"><p>开启后，当你已打开守候而后台服务中断时，会尝试恢复服务。主动停止守候后不会重新启动。</p><p><strong>从最近任务划掉应用后，它是恢复最快的一条路。</strong>系统会停止被划掉应用的前台服务和闹钟，但无障碍服务由系统自己重新绑定，所以系统清理本应用后仍会把它拉起来，随后由它恢复守候。自启动、电池不限制与最近任务锁定仍然是前提。</p><p><strong>不读取屏幕或聊天内容，不执行点击、手势、截屏或按键操作。</strong>只检查本应用的运行情况。</p><p>它不能替代通知、电池、自启动权限，也不能保证突破系统强制停止。部分安卓版本对手动安装应用的无障碍开关有“受限设置”保护：请确认安装包来源，再在应用系统设置中按手机指引处理。</p><p>你可以随时在系统无障碍设置中关闭“VR闹钟 · 守候辅助”。</p></div>`, `<div class="sheet-actions"><button class="secondary" data-action="closeModal">暂不开启</button><button class="primary" data-action="enableAccessibility">前往系统设置</button></div>`);
}
function testDialog() {
  const c = S.config;
  openModal("确认这一声能听见", `<div class="card"><div class="row"><div class="label-icon">${icon("sound")}</div><div><h3>${esc(toneNames[c.ringtone])} · ${c.volume}% 闹钟音量</h3><p class="sub">${c.ramp ? "渐强响铃" : "立即响铃"} · ${c.duration} 秒后自动停止</p></div></div></div><p class="body-copy">会按正式提醒的方式响铃，可随时关闭。选择锁屏测试后，你有 15 秒时间锁上手机。请避免将手机或耳机贴近耳朵进行首次高音量测试。</p><p class="hint">锁屏测试需要精确闹钟权限。勿扰、蓝牙耳机和厂商后台设置，都建议在这里实际试一次。</p>`, `<div class="sheet-actions"><button class="secondary" data-action="testLater">15 秒后锁屏测试</button><button class="primary" data-action="test">立即测试</button></div>`);
}
function privacy() {
  openModal("隐私与使用说明", `<div class="body-copy"><p><strong>只守候你添加的主播。</strong><br>通过 B 站公开接口检测你在“主播”页添加的直播间。仅“正在直播”触发开播提醒，轮播不会触发。不需要登录，不读取 Cookie，不访问私信。删除主播后，本机保存的头像与场次记录一并清除。</p><p><strong>数据留在你的手机。</strong><br>设置、铃声、主播列表、缓存的头像和最多 200 条运行记录保存在应用内，无广告、无统计上报。自选背景图片通过系统选择器选取，复制并缩放后保存于应用内部，不需要整册相册权限；备份不包含背景图片。网络请求仅用于连接 B 站公开直播接口（读取直播状态、主播名称与头像）。点击个人空间或直播间时会打开 B 站或浏览器。</p><p><strong>强响铃有明确边界。</strong><br>使用闹钟音量通道、可选振动及全屏通知。应用不会关闭系统勿扰模式，仍需由你在系统中允许闹钟。响铃到期自动停止，同场直播会去重；暂缓提醒是你主动请求的再次提醒。</p><p><strong>通知异常兼容响铃。</strong><br>需你明确开启，通知未获准时仍按所选时段播放闹铃。系统通知权限保持真实状态。悬浮关闭按钮完全可选，仅响铃时显示；可在应用内或系统中随时关闭相关设置。</p><p><strong>无障碍守候辅助。</strong><br>完全可选。只接收本应用自身的窗口变化信号，每约 45 秒检查守候服务；发现中断时请求恢复，失败后限速重试。不读取窗口内容、不执行手势或点击、不监听按键、不截屏。只有你已开启守候时才尝试恢复，不能突破系统强制停止。</p><p><strong>不是云端推送。</strong><br>守候依靠手机后台联网检测。正常情况下延迟约为检测间隔加网络耗时；B 站限制、接口改变、断网、系统休眠或强行停止都可能造成延迟或漏报。关闭“优先保证提醒”可降低耗电，但更容易延迟。</p><p><strong>时间按所选时区计算。</strong><br>跨午夜的时段属于开始那天。缺少接口开播时间时按首次检测时间判断；补报开关允许在进入时段后提醒已经开始的直播。</p><p><strong>文件备份。</strong><br>导出含设置、主播列表、状态和运行记录，不包含自选铃声与已缓存头像本身。恢复时会一并替换时段、声音设置和主播列表，不自动开始守候。卸载会删除本地设置与铃声，请先备份。</p><p>图标使用你提供的图片。应用为个人使用制作，与哔哩哔哩及主播无官方关联。</p></div>`);
}
async function perform(action, anchorId = "") {
  if (action === "accessibilityInfo") {
    accessibilityInfo();
    return;
  }
  if (action === "compatibilityInfo") {
    compatibilityInfo();
    return;
  }
  if (action === "confirmCompatibility") {
    if (compatibilitySaving) return;
    compatibilitySaving = true;
    const next = compatibilityAction;
    try {
      await save({ soundWithoutNotifications: true });
      compatibilityAction = null;
      closeModal();
      if (next === "start") await toggleWatch(true);
      else if (next) await perform(next);
      else toast("已开启通知异常兼容响铃");
    } finally {
      compatibilitySaving = false;
    }
    return;
  }
  if (action === "notificationReport") {
    notificationReport();
    return;
  }
  if (action === "reportOnly" || action === "reportWithComponents") {
    await api("exportNotificationReport", { includeComponents: action === "reportWithComponents" });
    return;
  }
  if (action === "notificationHelp" || action === "watchNotificationHelp") {
    await window.refreshNative(true);
    notificationHelp();
    return;
  }
  if (action === "notificationProbe") {
    await api("notificationProbe");
    toast("测试通知已提交，请下拉通知栏确认是否收到");
    await window.refreshNative(true);
    return;
  }
  if (action === "recheckPermissions") {
    await window.refreshNative(true);
    paintNotificationHelp();
    toast("已重新读取系统权限状态");
    return;
  }
  if (action === "history") {
    route = "history";
    render();
    return;
  }
  if (action === "back") {
    go("home");
    return;
  }
  if (action === "closeModal") {
    closeModal();
    return;
  }
  if (action === "addRule") {
    editRule();
    return;
  }
  if (action === "saveRule") {
    await saveRule();
    return;
  }
  if (action === "deleteRule") {
    const rules = S.config.windows.filter((w) => w.id !== ruleDraft.id);
    await save({ windows: rules, allDay: rules.some((w) => w.enabled) ? S.config.allDay : true });
    closeModal();
    toast("时段已删除");
    return;
  }
  if (action === "testDialog") {
    testDialog();
    return;
  }
  if (action === "privacy") {
    privacy();
    return;
  }
  if (action === "chooseTimezone") {
    await chooseTimezone();
    return;
  }
  if (action === "clearHistory") {
    openModal("清空通知记录？", '<p class="body-copy">将删除手机上已有的通知与运行记录，提醒设置不变。</p>', '<div class="sheet-actions"><button class="secondary" data-action="closeModal">取消</button><button class="primary" data-action="confirmClear">清空记录</button></div>');
    return;
  }
  if (action === "confirmClear") {
    await api("clearHistory");
    closeModal();
    history();
    return;
  }
  if (action === "addAnchor") {
    editAnchor("");
    return;
  }
  if (action === "addScheduleEntry") {
    addScheduleEntry();
    return;
  }
  if (action === "parseScheduleText") {
    await parseScheduleIntoDraft();
    return;
  }
  if (action === "saveSchedule") {
    await saveScheduleDraft();
    return;
  }
  if (action === "ocrSchedule") {
    await ocrScheduleIntoDraft();
    return;
  }
  if (action === "clearScheduleDraft") {
    scheduleDraft.entries = [];
    paintScheduleModal();
    return;
  }
  if (action === "refreshAvatars") {
    const r = await api("refreshAvatars", {}, 6e4);
    await window.refreshNative(true);
    toast("已为 " + r + " 位主播刷新头像");
    return;
  }
  if (action === "pickScheduleImage") {
    if (scheduleDraft) {
      try {
        await api("pickScheduleImage", { id: scheduleDraft.id }, 6e4);
        scheduleDraft.hasImage = true;
        paintScheduleModal();
      } catch (e) {
        toast(e.message);
      }
    }
    return;
  }
  if (action === "removeScheduleImage") {
    if (scheduleDraft) {
      await api("removeScheduleImage", { id: scheduleDraft.id });
      scheduleDraft.hasImage = false;
      paintScheduleModal();
      toast("图片已删除");
    }
    return;
  }
  if (action === "viewScheduleImage") {
    viewScheduleImage(anchorId);
    return;
  }
  if (action === "resolveAnchor") {
    await resolveAnchorDraft();
    return;
  }
  if (action === "refreshAvatar") {
    await refreshAvatarDraft();
    return;
  }
  if (action === "saveAnchorDraft") {
    await saveAnchorDraft();
    return;
  }
  if (action === "deleteAnchor") {
    askDeleteAnchor();
    return;
  }
  if (action === "confirmDeleteAnchor") {
    await removeAnchor();
    return;
  }
  if (action === "enableAccessibility") {
    closeModal();
    await api("permission", { kind: "accessibility" });
    return;
  }
  if (action === "start") {
    await toggleWatch(true);
    return;
  }
  if (action === "test" || action === "testLater") {
    if (!alarmAvailable()) {
      compatibilityInfo(action);
      return;
    }
    const value = await api(action);
    closeModal();
    if (native && action === "testLater") toast("15 秒后响铃，现在可以锁屏");
    await window.refreshNative(true);
    return;
  }
  const result = await api(action, anchorId ? { id: anchorId } : {});
  if (action === "refresh") toast("正在检查直播间…");
  if (action === "cancelTest") toast("锁屏测试已取消");
  if (action === "cancelSnooze") toast("已取消稍后提醒");
  await window.refreshNative(true);
}
function paintNotificationHelp() {
  const box = $("[data-notification-help]");
  if (!box || !S) return;
  const p = S.permissions, blocked = p.notificationPolicy === "revoked";
  const advice = $("[data-notification-advice]");
  if (advice) advice.textContent = blocked ? "系统报告通知权限受策略限制，普通授权弹窗无法改变它。当前还不能确定是哪一个系统组件或管理策略设置了限制，请导出排查包继续定位。" : "可以从下面的系统设置入口允许通知，再返回重新检查。如果开启后仍恢复关闭，请导出通知排查包。" + (S.xiaomi ? " 当前手机优先打开小米通知管理。" : "");
  const probe = $('#modal [data-action="notificationProbe"]');
  if (probe) probe.disabled = !p.notifications;
  box.innerHTML = `<p><strong>${p.notifications ? "系统已允许发送通知" : blocked ? "通知授权受系统策略限制" : "系统尚未允许发送通知"}</strong></p><p>通知授权：${p.notificationRuntime ? "已允许" : "未允许"}<br>应用通知开关：${p.notificationAppEnabled ? "已开启" : "未开启"}<br>策略检查：${blocked ? "被策略拒绝" : p.notificationPolicy === "not_revoked" ? "未检测到策略拒绝" : p.notificationPolicy === "not_applicable" ? "此系统无需运行时通知授权" : "暂时无法读取"}</p><p class="hint">检查时间：${esc(new Date(S.permissionsCheckedAt || S.now).toLocaleTimeString("zh-CN", { hour12: false }))}</p>`;
}
function notificationHelp() {
  openModal("通知授权帮助", `<div class="card body-copy" data-notification-help></div><p class="body-copy" data-notification-advice></p><div class="permission-actions"><button class="text-button" data-permission="notificationSettings">打开通知设置 ${icon("external")}</button><button class="text-button" data-permission="standardNotifications">备用通知设置 ${icon("external")}</button><button class="text-button" data-permission="appPermissions">应用权限管理 ${icon("external")}</button></div><p class="hint">兼容响铃仍可使用，但它不会解除系统通知限制。排查包仅保存在你选择的位置。</p><button class="text-button" data-action="notificationReport">导出通知排查包 ${icon("download")}</button><button class="text-button" data-action="compatibilityInfo">选择通知异常兼容响铃 ${icon("arrow")}</button>`, `<div class="sheet-actions"><button class="secondary" data-action="recheckPermissions">重新检查</button><button class="primary" data-action="notificationProbe">发送测试通知</button></div>`);
  paintNotificationHelp();
}
function notificationReport() {
  openModal("导出通知排查包", `<div class="body-copy"><p>用来定位通知开关无法保持的问题。包含VR闹钟的通知状态、最近检查时间、手机系统版本及指定权限组件的版本。</p><p><strong>建议选择“包含权限组件”。</strong><br>会额外复制可读取的系统权限组件安装包，方便核对这台手机使用的具体实现；不读取这些组件的应用数据、聊天、照片或其他个人文件。文件可能有数十 MB。</p><p>选择保存位置后，请把 ZIP 文件发回。不会自动上传，也不会修改系统权限策略。</p></div>`, `<div class="sheet-actions"><button class="secondary" data-action="reportOnly">仅诊断记录</button><button class="primary" data-action="reportWithComponents">包含权限组件</button></div>`);
}
async function toggleWatch(enabled) {
  if (enabled && !alarmAvailable()) {
    compatibilityInfo("start");
    return;
  }
  if (busy) return;
  busy = true;
  try {
    await api("toggle", { enabled });
    await window.refreshNative(true);
  } finally {
    busy = false;
  }
}
function go(next) {
  route = next;
  render();
  window.scrollTo({ top: 0, behavior: "auto" });
}
document.addEventListener("click", async (e) => {
  const b = e.target.closest("button");
  if (!b) return;
  try {
    if (b.dataset.route) {
      go(b.dataset.route);
      return;
    }
    if (b.dataset.action) {
      await perform(b.dataset.action, b.dataset.anchor || "");
      return;
    }
    if (b.dataset.anchorEdit) {
      editAnchor(b.dataset.anchorEdit);
      return;
    }
    if (b.dataset.anchorSchedule) {
      editSchedule(b.dataset.anchorSchedule);
      return;
    }
    if (b.dataset.anchorOpen) {
      await api("openLive", { id: b.dataset.anchorOpen });
      return;
    }
    if (b.dataset.anchorProfile) {
      await api("openProfile", { id: b.dataset.anchorProfile });
      return;
    }
    if (b.dataset.toggle) {
      const key = b.dataset.toggle;
      if (key === "enabled") await toggleWatch(!S.enabled);
      else if (key.startsWith("anchor:")) await toggleAnchor(key.slice(7));
      else if (key.startsWith("ring:")) await toggleRing(key.slice(5));
      else if (key === "draftAlarm") {
        if (anchorDraft) {
          anchorDraft.alarm = !(anchorDraft.alarm !== false);
          b.setAttribute("aria-checked", anchorDraft.alarm);
        }
      } else if (key === "soundWithoutNotifications" && !S.config[key]) compatibilityInfo();
      else await save({ [key]: !S.config[key] });
      return;
    }
    if (b.dataset.allDay) {
      await save({ allDay: b.dataset.allDay === "true" });
      return;
    }
    if (b.dataset.tone) {
      await save({ ringtone: b.dataset.tone });
      return;
    }
    if (b.dataset.seed !== void 0) {
      await save({ seedColor: b.dataset.seed });
      return;
    }
    if (b.dataset.schedDel) {
      scheduleDraft.entries = scheduleDraft.entries.filter((x) => x.id !== b.dataset.schedDel);
      paintScheduleModal();
      return;
    }
    if (b.dataset.schedWeekday !== void 0) {
      const day = Number(b.dataset.schedWeekday);
      schedDraftDays ^= 1 << day;
      b.classList.toggle("selected");
      b.setAttribute("aria-pressed", !!(schedDraftDays & 1 << day));
      return;
    }
    if (b.dataset.editRule) {
      editRule(b.dataset.editRule);
      return;
    }
    if (b.dataset.preset) {
      editRule(null, b.dataset.preset);
      return;
    }
    if (b.dataset.ruleToggle) {
      const rules = clone(S.config.windows), r = rules.find((x) => x.id === b.dataset.ruleToggle);
      r.enabled = !r.enabled;
      await save({ windows: rules });
      return;
    }
    if (b.dataset.weekday !== void 0) {
      const day = Number(b.dataset.weekday);
      ruleDraft.days ^= 1 << day;
      b.classList.toggle("selected");
      b.setAttribute("aria-pressed", !!(ruleDraft.days & 1 << day));
      return;
    }
    if (b.dataset.zone) {
      await save({ timezone: b.dataset.zone });
      closeModal();
      toast("已按所选时区计算提醒范围");
      return;
    }
    if (b.dataset.permission) {
      if (b.dataset.permission === "accessibility") {
        accessibilityInfo();
        return;
      }
      if (b.dataset.permission === "notifications") {
        await window.refreshNative(true);
        if (S.permissions.notificationPolicy === "revoked") {
          notificationHelp();
          return;
        }
      }
      await api("permission", { kind: b.dataset.permission });
      return;
    }
  } catch (error) {
    toast(error.message);
  }
});
document.addEventListener("change", async (e) => {
  try {
    if (e.target.id === "volume") {
      await save({ volume: Number(e.target.value) });
      return;
    }
    const key = e.target.dataset.setting;
    if (key) {
      let value = e.target.value;
      if (["duration", "pollSeconds", "snoozeMinutes", "backgroundDim", "cardOpacity"].includes(key)) value = Number(value);
      await save({ [key]: value });
    }
  } catch (error) {
    toast(error.message);
    render();
  }
});
document.addEventListener("input", (e) => {
  if (e.target.id === "tz-search") renderZones(e.target.value);
  if (e.target.id === "volume") $("#volume-value").innerHTML = `${Number(e.target.value)}<span>%</span>`;
});
document.addEventListener("error", (e) => {
  const img = e.target;
  if (img && img.tagName === "IMG" && img.closest(".anchor-art")) img.remove();
}, true);
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") window.appBack();
  if (e.key === "Tab" && $("#modal") && !$("#modal").hidden) {
    const items = [...$("#modal").querySelectorAll("button,input,select")].filter((x) => !x.disabled), first = items[0], last = items[items.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      last.focus();
      e.preventDefault();
    } else if (!e.shiftKey && document.activeElement === last) {
      first.focus();
      e.preventDefault();
    }
  }
});
window.appBack = () => {
  if ($("#modal") && !$("#modal").hidden) {
    closeModal();
    return;
  }
  if (route !== "home") {
    go("home");
    return;
  }
  api("minimize");
};
let refreshPromise = null, refreshQueued = false, forceQueued = false, stateRevision = 0;
window.refreshNative = (force = false) => {
  refreshQueued = true;
  forceQueued = forceQueued || !!force;
  if (refreshPromise) return refreshPromise;
  refreshPromise = (async () => {
    do {
      const repaint = forceQueued, revision = stateRevision;
      refreshQueued = false;
      forceQueued = false;
      try {
        const value = await api("state");
        if (revision !== stateRevision) {
          refreshQueued = true;
          forceQueued = true;
          continue;
        }
        const old = S, changed = configFingerprint !== JSON.stringify(value.config);
        const permissionsChanged = !old || JSON.stringify(old.permissions) !== JSON.stringify(value.permissions);
        const zoneChanged = !old || old.zone !== value.zone || old.deviceZone !== value.deviceZone;
        S = value;
        configFingerprint = JSON.stringify(S.config);
        applyTheme();
        if (document.body.classList.contains("alarm-page")) renderAlarm();
        else if (!old || changed || permissionsChanged || zoneChanged || repaint) render();
        else if (route === "home" && (!$("#modal") || $("#modal").hidden)) home();
        paintNotificationHelp();
        paintWatchNotice();
      } catch (error) {
        toast(error.message);
      }
    } while (refreshQueued);
  })().then(() => {
    refreshPromise = null;
    if (refreshQueued) return window.refreshNative(forceQueued);
  }, (error) => {
    refreshPromise = null;
    throw error;
  });
  return refreshPromise;
};
function renderAlarm() {
  const test = S.alarmTest || S.preview, active = S.ringing || S.preview, who = S.alarmAnchor || "主播";
  const key = [test, active, S.alarmTitle, S.config.snoozeMinutes, who].join("|");
  if (alarmPainted !== key) {
    alarmPainted = key;
    $("#alarm-root").innerHTML = `<div class="alarm-eyebrow"><span class="dot ${active ? "live" : ""}"></span>${test ? "RINGTONE TEST" : "LIVE NOW"}</div><div class="alarm-halo"><img src="hazel.png" alt="正在提醒你的VR闹钟插画"></div><h1>${active ? test ? "这一声，听见了吗？" : esc(who) + " 开播啦。" : "响铃已结束。"}</h1><p class="alarm-name">${test ? "VR闹钟 · 和正式提醒一样响" : esc(who)}</p><p class="alarm-title">${esc(S.alarmTitle || (test ? "确认声音、振动与锁屏显示" : "去直播间，和主播见个面。"))}</p><p class="countdown" id="remaining"></p><div class="alarm-actions">${active ? test ? `<button class="primary" data-action="dismiss">${icon("check")}听见了，结束测试</button>` : `<button class="primary" data-action="openLive" data-anchor="${esc(S.alarmAnchorId || "")}">${icon("external")}去直播间 · 关闭响铃</button><button class="secondary" data-action="snooze">${icon("clock")}${S.config.snoozeMinutes} 分钟后再提醒</button><button class="quiet-close" data-action="dismiss">本场不再响铃</button>` : `<button class="primary" data-action="closeAlarm">知道了</button>`}</div><p class="footnote">${active ? "返回键只收起页面，铃声仍继续。<br>请点按上方按钮或通知中的关闭按钮。" : "同一场直播不会再次自动响铃。"}</p>`;
  }
  if ($("#remaining")) $("#remaining").textContent = active ? S.preview ? "界面预览 · 实际响铃需安装安卓应用" : `${Math.max(0, Math.ceil((S.alarmUntil - Date.now()) / 1e3))} 秒后自动停止` : "";
}
window.refreshNative();
setInterval(() => {
  if (!document.hidden) window.refreshNative();
}, 2e3);
document.addEventListener("visibilitychange", () => {
  if (!document.hidden) window.refreshNative(true);
});
