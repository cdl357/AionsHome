/* ============================================================
   AionsHome 移动端主题系统（布局定稿·方案 C）
   - 现成主题一键切换：樱粉 / 雾蓝 / 墨绿 / 藕荷 / 奶茶（无橘色）
   - 调色盘：只调一个主色，背景/卡片/文字由系统自动派生（方案 A）
   - 持久化：localStorage `aion_mobile_theme`
   页面用法：<head> 内引本文件后调用 MobileTheme.boot()（渲染前生效，防闪烁）
   ============================================================ */
(function (root) {
  'use strict';

  var STORE_KEY = 'aion_mobile_theme';

  /* 每个预设定义完整变量组；保持安静、低饱和、无橘色 */
  var PRESETS = {
    /* 当前默认：深夜蓝 × 雾蓝 × 暖白 × 淡金 */
    wulan: {
      name: '雾蓝', dark: true,
      vars: {
        '--bg': '#0b111f', '--surface': '#141d31', '--surface-2': '#1b2740',
        '--border': 'rgba(151,166,199,.16)', '--text': '#f1ede2',
        '--text-2': '#aab6c9', '--text-3': '#77839b',
        '--accent': '#d9b877', '--accent-strong': '#e6c98d',
        '--accent-soft': 'rgba(217,184,119,.14)', '--lilac': '#9d92b8',
        '--danger': '#d98a8a', '--ok': '#8fbf9a'
      }
    },
    /* 樱粉：暖纸白底 × 灰粉 */
    sakura: {
      name: '樱粉', dark: false,
      vars: {
        '--bg': '#f7f2f2', '--surface': '#ffffff', '--surface-2': '#fbeef1',
        '--border': 'rgba(178,140,152,.22)', '--text': '#3d3237',
        '--text-2': '#75676e', '--text-3': '#a3969c',
        '--accent': '#c97085', '--accent-strong': '#b95f76',
        '--accent-soft': 'rgba(201,112,133,.12)', '--lilac': '#a48cb0',
        '--danger': '#c26a6a', '--ok': '#7aa98b'
      }
    },
    /* 墨绿：深绿夜 × 苔白 */
    molyu: {
      name: '墨绿', dark: true,
      vars: {
        '--bg': '#0d1512', '--surface': '#15211c', '--surface-2': '#1c2c25',
        '--border': 'rgba(150,180,165,.16)', '--text': '#eef2ea',
        '--text-2': '#a9bcae', '--text-3': '#7a8f81',
        '--accent': '#a8c3a0', '--accent-strong': '#bcd4b3',
        '--accent-soft': 'rgba(168,195,160,.13)', '--lilac': '#9d92b8',
        '--danger': '#d98a8a', '--ok': '#8fbf9a'
      }
    },
    /* 藕荷：淡荷灰紫 */
    ouhe: {
      name: '藕荷', dark: false,
      vars: {
        '--bg': '#f4f2f7', '--surface': '#ffffff', '--surface-2': '#efeaf5',
        '--border': 'rgba(150,138,175,.22)', '--text': '#37323f',
        '--text-2': '#6f6879', '--text-3': '#9d95a6',
        '--accent': '#8f7bb0', '--accent-strong': '#7e6aa1',
        '--accent-soft': 'rgba(143,123,176,.12)', '--lilac': '#8f7bb0',
        '--danger': '#c26a6a', '--ok': '#7aa98b'
      }
    },
    /* 奶茶：暖奶白 × 灰棕 */
    naicha: {
      name: '奶茶', dark: false,
      vars: {
        '--bg': '#f6f1ea', '--surface': '#fffdf9', '--surface-2': '#f0e7dc',
        '--border': 'rgba(170,150,128,.24)', '--text': '#3b342c',
        '--text-2': '#786d5f', '--text-3': '#a89c8d',
        '--accent': '#a98a63', '--accent-strong': '#967a54',
        '--accent-soft': 'rgba(169,138,99,.13)', '--lilac': '#9d92b8',
        '--danger': '#c26a6a', '--ok': '#7aa98b'
      }
    }
  };

  /* ── 单主色 → 自动派生整组变量（深色基底，保证可读） ── */
  function hexToHsl(hex) {
    var m = /^#?([0-9a-f]{6})$/i.exec(String(hex || '').trim());
    if (!m) return null;
    var n = parseInt(m[1], 16), r = (n >> 16 & 255) / 255,
        g = (n >> 8 & 255) / 255, b = (n & 255) / 255;
    var max = Math.max(r, g, b), min = Math.min(r, g, b), h, s, l;
    l = (max + min) / 2;
    if (max === min) { h = 0; s = 0; }
    else {
      var d = max - min;
      s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
      if (max === r) h = ((g - b) / d + (g < b ? 6 : 0));
      else if (max === g) h = (b - r) / d + 2;
      else h = (r - g) / d + 4;
      h *= 60;
    }
    return { h: h, s: s, l: l };
  }

  function hsl(h, s, l, a) {
    var parts = Math.round(h) + ', ' + Math.round(s * 100) + '%, ' + Math.round(l * 100) + '%';
    return a == null ? 'hsl(' + parts + ')' : 'hsla(' + parts + ', ' + a + ')';
  }

  function deriveCustomVars(hex) {
    var c = hexToHsl(hex);
    if (!c) return null;
    var h = c.h, s = Math.min(0.5, Math.max(0.12, c.s));
    var isWarm = (h >= 20 && h <= 70); /* 避免落到橘色观感：压低暖橙饱和 */
    var sat = isWarm ? s * 0.55 : s;
    return {
      '--bg': hsl(h, sat * 0.55, 0.09),
      '--surface': hsl(h, sat * 0.6, 0.14),
      '--surface-2': hsl(h, sat * 0.65, 0.19),
      '--border': hsl(h, sat * 0.8, 0.68, '.16'),
      '--text': hsl(h, 0.18, 0.93),
      '--text-2': hsl(h, sat * 0.5, 0.72),
      '--text-3': hsl(h, sat * 0.4, 0.55),
      '--accent': hsl(h, Math.min(0.55, sat * 1.15), 0.66),
      '--accent-strong': hsl(h, Math.min(0.6, sat * 1.2), 0.73),
      '--accent-soft': hsl(h, sat, 0.6, '.14'),
      '--lilac': 'hsl(' + Math.round((h + 60) % 360) + ', 20%, 66%)',
      '--danger': '#d98a8a', '--ok': '#8fbf9a'
    };
  }

  function applyVars(vars) {
    var style = document.documentElement.style;
    Object.keys(vars).forEach(function (k) { style.setProperty(k, vars[k]); });
    var meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.setAttribute('content', vars['--bg']);
  }

  function read() {
    try { return JSON.parse(localStorage.getItem(STORE_KEY) || 'null'); }
    catch (e) { return null; }
  }

  function save(state) {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(state)); } catch (e) {}
  }

  function applyCustom(hex, persist) {
    var vars = deriveCustomVars(hex);
    if (!vars) return false;
    applyVars(vars);
    document.documentElement.setAttribute('data-theme', 'custom');
    if (persist !== false) save({ custom: hex });
    return true;
  }

  function applyPreset(key, persist) {
    var p = PRESETS[key];
    if (!p) return false;
    applyVars(p.vars);
    document.documentElement.setAttribute('data-theme', key);
    if (persist !== false) save({ preset: key });
    return true;
  }

  /* 页面加载时立即恢复（防闪烁） */
  function boot() {
    var state = read();
    if (state && state.custom) { applyCustom(state.custom, false); return; }
    if (state && state.preset && PRESETS[state.preset]) { applyPreset(state.preset, false); return; }
    applyPreset('wulan', false);
  }

  root.MobileTheme = {
    PRESETS: PRESETS,
    PRESET_ORDER: ['wulan', 'sakura', 'molyu', 'ouhe', 'naicha'],
    boot: boot,
    applyPreset: applyPreset,
    applyCustom: applyCustom,
    deriveCustomVars: deriveCustomVars,
    current: read
  };
})(window);
