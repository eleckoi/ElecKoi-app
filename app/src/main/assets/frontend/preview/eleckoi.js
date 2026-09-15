(function installElecKoiAuthorApi(global) {
  "use strict";

  const API_VERSION = "0.1.0";
  if (global.ElecKoi && global.ElecKoi.api && global.ElecKoi.api.version === API_VERSION) {
    return;
  }
  const REQUEST_TIMEOUT_MS = 10000;
  const PRESENTATION_PENDING_EVENT = "eleckoi:author-pending-change";
  const pending = new Map();
  const eventListeners = new Map();
  let requestSequence = 0;
  let eventChannelStarted = false;

  function notifyPresentationPendingChanged() {
    global.dispatchEvent(new CustomEvent(PRESENTATION_PENDING_EVENT, {
      detail: { pendingCount: pending.size },
    }));
  }

  Object.defineProperty(global, "__ElecKoiAuthorPendingCount", {
    value: () => pending.size,
    configurable: false,
    enumerable: false,
    writable: false,
  });

  function makeError(code, message) {
    const error = new Error(message);
    error.code = code;
    return error;
  }

  function call(method, params = {}) {
    const transport = global.ElecKoiNative;
    if (!transport || typeof transport.postMessage !== "function") {
      return Promise.reject(makeError("BRIDGE_UNAVAILABLE", "当前 WebView 不支持 ElecKoi 作者 API 桥接"));
    }

    const id = `author-${Date.now()}-${++requestSequence}`;
    return new Promise((resolve, reject) => {
      const timeoutId = global.setTimeout(() => {
        pending.delete(id);
        notifyPresentationPendingChanged();
        reject(makeError("REQUEST_TIMEOUT", `API 调用超时：${method}`));
      }, REQUEST_TIMEOUT_MS);
      pending.set(id, { resolve, reject, timeoutId });
      notifyPresentationPendingChanged();
      transport.postMessage(JSON.stringify({ id, apiVersion: API_VERSION, method, params }));
    });
  }

  function dispatchEvent(message) {
    const listeners = eventListeners.get(message.event);
    if (!listeners) return;
    listeners.forEach((listener) => listener(message.payload));
  }

  if (global.ElecKoiNative) {
    global.ElecKoiNative.onmessage = (event) => {
      let message;
      try {
        message = JSON.parse(event.data);
      } catch (_) {
        return;
      }
      if (message.type === "event") {
        dispatchEvent(message);
        return;
      }
      const request = pending.get(message.id);
      if (!request) return;
      global.clearTimeout(request.timeoutId);
      pending.delete(message.id);
      notifyPresentationPendingChanged();
      if (message.ok) {
        request.resolve(message.result);
      } else {
        const apiError = message.error || {};
        request.reject(makeError(apiError.code || "API_ERROR", apiError.message || "API 调用失败"));
      }
    };
  }

  function on(eventName, listener) {
    const listeners = eventListeners.get(eventName) || new Set();
    listeners.add(listener);
    eventListeners.set(eventName, listeners);
    if (!eventChannelStarted) {
      eventChannelStarted = true;
      // The native bridge obtains its reply channel from the first request. Establish it here so
      // a frontend that only subscribes to events still receives unsolicited state updates.
      call("events.list").catch(() => {
        eventChannelStarted = false;
      });
    }
    return () => off(eventName, listener);
  }

  function off(eventName, listener) {
    const listeners = eventListeners.get(eventName);
    if (!listeners) return;
    listeners.delete(listener);
    if (listeners.size === 0) eventListeners.delete(eventName);
  }

  async function serializeAttachment(source) {
    if (!source || source.type !== "image") {
      throw makeError("INVALID_PARAMS", "当前聊天只支持把图片作为 Agent 附件发送");
    }
    if (source.data) {
      return { type: "image", mediaType: source.mimeType, name: source.name, data: source.data };
    }
    if (!source.url) {
      throw makeError("INVALID_PARAMS", "图片附件必须提供 data 或 url");
    }
    const response = await global.fetch(source.url);
    if (!response.ok) {
      throw makeError("MEDIA_LOAD_FAILED", `读取图片失败：${response.status}`);
    }
    const blob = await response.blob();
    const mediaType = source.mimeType || blob.type;
    const bytes = new Uint8Array(await blob.arrayBuffer());
    let binary = "";
    for (let offset = 0; offset < bytes.length; offset += 0x8000) {
      binary += String.fromCharCode(...bytes.subarray(offset, Math.min(offset + 0x8000, bytes.length)));
    }
    return { type: "image", mediaType, name: source.name, data: global.btoa(binary) };
  }

  global.ElecKoi = Object.freeze({
    api: Object.freeze({ stage: "preview", version: API_VERSION }),
    call,
    app: Object.freeze({
      getInfo: () => call("app.getInfo"),
      getCapabilities: () => call("app.getCapabilities"),
    }),
    context: Object.freeze({ current: () => call("context.current") }),
    variables: Object.freeze({
      getState: (options = {}) => call("variables.getState", options),
      getConfig: () => call("variables.getConfig"),
      setState: (state) => call("variables.setState", { state }),
      merge: (state) => call("variables.merge", { state }),
      applyPatch: (patch) => call("variables.applyPatch", { patch }),
      reset: () => call("variables.reset"),
    }),
    openings: Object.freeze({
      list: () => call("openings.list"),
      current: () => call("openings.current"),
      select: (id) => call("openings.select", { id }),
    }),
    messages: Object.freeze({
      list: () => call("messages.list"),
      get: (id) => call("messages.get", { id }),
      current: () => call("messages.current"),
      deleteFrom: (id) => call("messages.deleteFrom", { id }),
      regenerate: (id) => call("messages.regenerate", { id }),
      editAndRegenerate: (id, text) => call("messages.editAndRegenerate", { id, text }),
    }),
    chat: Object.freeze({
      current: () => call("chat.current"),
      list: () => call("chat.list"),
      getGenerationState: () => call("chat.getGenerationState"),
      getAgentTrajectory: (options = {}) => call("chat.getAgentTrajectory", options),
      getModels: () => call("chat.getModels"),
      send: async (text, options = {}) => call("chat.send", {
        text,
        attachments: await Promise.all((options.attachments || []).map(serializeAttachment)),
      }),
      stopGeneration: () => call("chat.stopGeneration"),
      create: (options = {}) => call("chat.create", options),
      open: (sessionId) => call("chat.open", { sessionId }),
      delete: (sessionId) => call("chat.delete", { sessionId }),
      selectModel: (options) => call("chat.selectModel", options),
    }),
    character: Object.freeze({ current: () => call("character.current") }),
    settingLibrary: Object.freeze({
      current: () => call("settingLibrary.current"),
      getSummary: () => call("settingLibrary.getSummary"),
    }),
    media: Object.freeze({
      getMessageAttachments: (messageId) => call("media.getMessageAttachments", { messageId }),
      getMessageAttachment: (messageId, attachmentId) => call("media.getMessageAttachment", { messageId, attachmentId }),
    }),
    audio: Object.freeze({
      play: (options) => call("audio.play", options),
      pause: (channel = "bgm") => call("audio.pause", { channel }),
      resume: (channel = "bgm") => call("audio.resume", { channel }),
      stop: (channel = "bgm") => call("audio.stop", { channel }),
      seek: (seconds, channel = "bgm") => call("audio.seek", { channel, seconds }),
      getState: (channel = "bgm") => call("audio.getState", { channel }),
      getPlaylist: (channel = "bgm") => call("audio.getPlaylist", { channel }),
      setPlaylist: (channel, items, options = {}) => call("audio.setPlaylist", { channel, items, ...options }),
      appendPlaylist: (channel, items) => call("audio.appendPlaylist", { channel, items }),
      getSettings: () => call("audio.getSettings"),
      setSettings: (settings) => call("audio.setSettings", { settings }),
    }),
    input: Object.freeze({
      get: () => call("input.get"),
      set: (text) => call("input.set", { text }),
      append: (text) => call("input.append", { text }),
      clear: () => call("input.clear"),
      send: () => call("input.send"),
    }),
    events: Object.freeze({ list: () => call("events.list"), on, off }),
  });
})(window);
