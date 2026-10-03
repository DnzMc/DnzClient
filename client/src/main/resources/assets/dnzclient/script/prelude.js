// DNZ Script API v1. Runs before every mod; builds the friendly functions on top of the
// Java side (__n). Keep this in plain ES5 so it works everywhere.

var __handlers = {};

function on(event, handler) {
  if (typeof event !== 'string') throw new Error('on(): the first value must be an event name, e.g. on("tick", () => {})');
  if (typeof handler !== 'function') throw new Error('on("' + event + '"): the second value must be a function');
  if (__n.events.indexOf(event) < 0) throw new Error('on(): unknown event "' + event + '". Events: ' + __n.events.join(', '));
  (__handlers[event] = __handlers[event] || []).push(handler);
}

function __emit(event, value) {
  var list = __handlers[event];
  if (!list) return;
  for (var i = 0; i < list.length; i++) list[i](value);
}

function every(seconds, handler) { return __n.timer(Number(seconds), handler, true); }
function after(seconds, handler) { return __n.timer(Number(seconds), handler, false); }
function cancel(id) { __n.cancel(Number(id)); }
function key(name, handler) { __n.key(String(name), handler); }
function command(name, handler) { __n.command(String(name), handler); }

function log() {
  var parts = [];
  for (var i = 0; i < arguments.length; i++) {
    var a = arguments[i];
    parts.push(a !== null && typeof a === 'object' ? JSON.stringify(a) : String(a));
  }
  __n.log(parts.join(' '));
}

var hud = {
  add: function (id, label, value) { __n.hudAdd(String(id), label == null ? '' : String(label), value); },
  remove: function (id) { __n.hudRemove(String(id)); }
};

var screen = {
  title: function (text, options) {
    options = options || {};
    __n.title(String(text), options.subtitle == null ? '' : String(options.subtitle),
      options.color == null ? 'white' : String(options.color), options.seconds == null ? 3 : Number(options.seconds));
  },
  actionbar: function (text, color) { __n.actionbar(String(text), color == null ? 'white' : String(color)); },
  toast: function (title, text) { __n.toast(String(title), text == null ? '' : String(text)); }
};

var chat = {
  show: function (text, color) { __n.chatShow(String(text), color == null ? 'white' : String(color)); },
  send: function (text) { __n.chatSend(String(text)); }
};

var sound = {
  play: function (id, volume, pitch) { __n.sound(String(id), volume == null ? 1 : Number(volume), pitch == null ? 1 : Number(pitch)); }
};

var player = {
  inGame: function () { return __n.player('inGame'); },
  name: function () { return __n.player('name'); },
  health: function () { return __n.player('health'); },
  maxHealth: function () { return __n.player('maxHealth'); },
  food: function () { return __n.player('food'); },
  armor: function () { return __n.player('armor'); },
  xp: function () { return __n.player('xp'); },
  pos: function () { return { x: __n.player('x'), y: __n.player('y'), z: __n.player('z') }; },
  dimension: function () { return __n.player('dimension'); }
};

var game = {
  fps: function () { return __n.game('fps'); },
  ping: function () { return __n.game('ping'); },
  time: function () { return __n.game('time'); },
  day: function () { return __n.game('day'); },
  server: function () { return __n.game('server'); },
  version: function () { return __n.game('version'); },
  language: function () { return __n.game('language'); }
};

var storage = {
  get: function (name, fallback) {
    var saved = __n.storageGet(String(name));
    return saved == null ? fallback : JSON.parse(saved);
  },
  set: function (name, value) {
    __n.storageSet(String(name), value === undefined ? null : JSON.stringify(value));
  }
};
