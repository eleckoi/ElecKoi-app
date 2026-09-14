(function installElecKoiAuthorApi(global) {
  "use strict";

  const API_VERSION = "0.2.0-preview.6";
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

  const messagePresentations = new WeakMap();

  function isFrontendDocument(value) {
    return /^(?:<!doctype\s+html\b|<(?:html|head|body)(?:\s|>))/i.test(String(value || "").trim());
  }

  function splitRawFrontendDocument(source) {
    const start = /(?:<!doctype\s+html\b|<(?:html|head|body)(?:\s|>))/i.exec(source);
    if (!start) return null;
    const tail = source.slice(start.index);
    const endings = [/<\/html\s*>/gi, /<\/body\s*>/gi]
      .flatMap((pattern) => Array.from(tail.matchAll(pattern)))
      .sort((left, right) => right.index - left.index);
    const ending = endings[0];
    if (!ending && start.index > 0) return null;
    const documentEnd = ending ? start.index + ending.index + ending[0].length : source.length;
    const documentSource = source.slice(start.index, documentEnd).trim();
    if (!isFrontendDocument(documentSource)) return null;
    return [
      { kind: "text", source: source.slice(0, start.index) },
      { kind: "frontend", source: documentSource },
      { kind: "text", source: source.slice(documentEnd) },
    ].filter((part) => part.kind === "frontend" || part.source.trim());
  }

  function messagePresentationParts(value) {
    const source = String(value || "").replace(/\r\n?/g, "\n");
    const lines = source.split("\n");
    const parts = [];
    let textLines = [];
    let foundFrontendFence = false;
    const flushText = () => {
      const text = textLines.join("\n");
      if (text.trim()) parts.push({ kind: "text", source: text });
      textLines = [];
    };
    for (let lineIndex = 0; lineIndex < lines.length; lineIndex += 1) {
      const opening = lines[lineIndex].match(/^[ \t]{0,3}(`{3,}|~{3,})/);
      if (!opening) {
        textLines.push(lines[lineIndex]);
        continue;
      }
      const marker = opening[1][0];
      const markerLength = opening[1].length;
      let closingIndex = -1;
      for (let candidateIndex = lineIndex + 1; candidateIndex < lines.length; candidateIndex += 1) {
        const closing = lines[candidateIndex].match(/^[ \t]{0,3}(`+|~+)[ \t]*$/);
        if (closing && closing[1][0] === marker && closing[1].length >= markerLength) {
          closingIndex = candidateIndex;
          break;
        }
      }
      if (closingIndex < 0) {
        textLines.push(lines[lineIndex]);
        continue;
      }
      const fencedSource = lines.slice(lineIndex + 1, closingIndex).join("\n").trim();
      if (!isFrontendDocument(fencedSource)) {
        textLines.push(...lines.slice(lineIndex, closingIndex + 1));
        lineIndex = closingIndex;
        continue;
      }
      flushText();
      parts.push({ kind: "frontend", source: fencedSource });
      foundFrontendFence = true;
      lineIndex = closingIndex;
    }
    flushText();
    if (foundFrontendFence) return parts;
    return splitRawFrontendDocument(source) || [{ kind: "text", source }];
  }

  function injectEmbeddedAuthorApi(source) {
    const bootstrap = "<script>(()=>{try{if(window.parent&&window.parent.ElecKoi)" +
      "Object.defineProperty(window,'ElecKoi',{value:window.parent.ElecKoi,configurable:false});" +
      "}catch(_){}})();</script>";
    const head = source.match(/<head(?:\s[^>]*)?>/i);
    if (head) return source.replace(head[0], head[0] + bootstrap);
    const html = source.match(/<html(?:\s[^>]*)?>/i);
    if (html) return source.replace(html[0], html[0] + "<head>" + bootstrap + "</head>");
    return bootstrap + source;
  }

  function disposeMessagePresentation(element) {
    const active = messagePresentations.get(element);
    if (!active) return;
    active.dispose();
    messagePresentations.delete(element);
  }

  function createMessageFrontendFrame(ownerDocument, frontendSource, options) {
    const frame = ownerDocument.createElement("iframe");
    frame.dataset.eleckoiMessageFrontend = "true";
    frame.title = options.title || "消息内嵌界面";
    frame.setAttribute("scrolling", "no");
    frame.style.display = "block";
    frame.style.width = "100%";
    frame.style.height = `${Math.max(1, Number(options.initialHeight) || 240)}px`;
    frame.style.border = "0";
    frame.style.background = "transparent";

    let resizeObserver = null;
    let mutationObserver = null;
    let resizeFrame = 0;
    let probeTimer = 0;
    let disposed = false;
    const scheduleHeight = () => {
      if (disposed || resizeFrame) return;
      resizeFrame = global.requestAnimationFrame(() => {
        resizeFrame = 0;
        if (disposed) return;
        const embeddedDocument = frame.contentDocument;
        const root = embeddedDocument?.documentElement;
        const body = embeddedDocument?.body;
        if (!root || !body) return;
        const bodyRect = body.getBoundingClientRect();
        let contentBottom = bodyRect.top;
        const range = embeddedDocument.createRange();
        range.selectNodeContents(body);
        contentBottom = Math.max(contentBottom, range.getBoundingClientRect().bottom);
        Array.from(body.children).forEach((child) => {
          const rect = child.getBoundingClientRect();
          const style = embeddedDocument.defaultView?.getComputedStyle(child);
          contentBottom = Math.max(contentBottom, rect.bottom + (Number.parseFloat(style?.marginBottom) || 0));
        });
        const bodyStyle = embeddedDocument.defaultView?.getComputedStyle(body);
        const height = Math.max(
          1,
          Math.ceil(
            contentBottom - bodyRect.top +
            (Number.parseFloat(bodyStyle?.paddingBottom) || 0) +
            (Number.parseFloat(bodyStyle?.marginTop) || 0) +
            (Number.parseFloat(bodyStyle?.marginBottom) || 0),
          ),
        );
        if (Math.abs(frame.getBoundingClientRect().height - height) > 1) {
          frame.style.height = `${height}px`;
        }
      });
    };
    const connectDocument = () => {
      if (disposed) return false;
      const embeddedDocument = frame.contentDocument;
      if (!embeddedDocument?.documentElement || !embeddedDocument.body || embeddedDocument.readyState === "loading") {
        return false;
      }
      resizeObserver?.disconnect();
      mutationObserver?.disconnect();
      if ("ResizeObserver" in global) {
        resizeObserver = new global.ResizeObserver(scheduleHeight);
        resizeObserver.observe(embeddedDocument.documentElement);
        resizeObserver.observe(embeddedDocument.body);
      }
      mutationObserver = new global.MutationObserver(scheduleHeight);
      mutationObserver.observe(embeddedDocument.documentElement, {
        attributes: true,
        childList: true,
        characterData: true,
        subtree: true,
      });
      scheduleHeight();
      return true;
    };
    const onLoad = () => connectDocument();
    frame.addEventListener("load", onLoad);
    frame.srcdoc = injectEmbeddedAuthorApi(frontendSource);
    let probeCount = 0;
    const probe = () => {
      if (disposed || connectDocument()) return;
      probeCount += 1;
      if (probeCount < 40) probeTimer = global.setTimeout(probe, 50);
    };
    probeTimer = global.setTimeout(probe, 0);
    return {
      element: frame,
      dispose() {
        if (disposed) return;
        disposed = true;
        frame.removeEventListener("load", onLoad);
        resizeObserver?.disconnect();
        mutationObserver?.disconnect();
        if (resizeFrame) global.cancelAnimationFrame(resizeFrame);
        if (probeTimer) global.clearTimeout(probeTimer);
      },
    };
  }

  function renderMessageContent(element, messageOrContent, options = {}) {
    if (!element || element.nodeType !== 1 || typeof element.replaceChildren !== "function") {
      throw makeError("INVALID_TARGET", "renderMessageContent 需要一个 HTML 元素");
    }
    disposeMessagePresentation(element);
    const message = messageOrContent && typeof messageOrContent === "object"
      ? messageOrContent
      : { content: messageOrContent };
    const source = String(message.content || "");
    const fallback = source || (message.pending && !message.agentActivity ? "…" : "");
    const parts = message.pending || options.frontend === false
      ? [{ kind: "text", source: fallback }]
      : messagePresentationParts(source);
    if (!parts.some((part) => part.kind === "frontend")) {
      element.dataset.eleckoiContentKind = "text";
      element.textContent = fallback;
      return Object.freeze({ kind: "text", dispose: () => disposeMessagePresentation(element) });
    }
    const ownerDocument = element.ownerDocument || global.document;
    const fragment = ownerDocument.createDocumentFragment();
    const frames = [];
    const mountedFrontends = [];
    parts.forEach((part) => {
      if (part.kind === "text") {
        const text = ownerDocument.createElement("span");
        text.dataset.eleckoiMessageText = "true";
        text.style.display = "block";
        text.style.whiteSpace = "pre-wrap";
        text.textContent = part.source;
        fragment.append(text);
        return;
      }
      const mounted = createMessageFrontendFrame(ownerDocument, part.source, options);
      mounted.element.style.margin = parts.length > 1 ? "0.55em 0" : "0";
      mountedFrontends.push(mounted);
      frames.push(mounted.element);
      fragment.append(mounted.element);
    });
    const kind = parts.some((part) => part.kind === "text") ? "mixed" : "frontend";
    element.dataset.eleckoiContentKind = kind;
    element.replaceChildren(fragment);
    const dispose = () => {
      mountedFrontends.forEach((mounted) => mounted.dispose());
    };
    messagePresentations.set(element, { dispose });
    return Object.freeze({
      kind,
      element: frames[0] || null,
      elements: Object.freeze(frames.slice()),
      dispose: () => disposeMessagePresentation(element),
    });
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
      regenerate: (id) => call("messages.regenerate", { id }),
      edit: (id, text) => call("messages.edit", { id, text }),
      editAndRegenerate: (id, text) => call("messages.editAndRegenerate", { id, text }),
    }),
    chat: Object.freeze({
      current: () => call("chat.current"),
      list: () => call("chat.list"),
      getGenerationState: () => call("chat.getGenerationState"),
      getModels: () => call("chat.getModels"),
      send: (text) => call("chat.send", { text }),
      stopGeneration: () => call("chat.stopGeneration"),
      create: (options = {}) => call("chat.create", options),
      open: (sessionId) => call("chat.open", { sessionId }),
      delete: (sessionId) => call("chat.delete", { sessionId }),
      selectModel: (options) => call("chat.selectModel", options),
    }),
    character: Object.freeze({ current: () => call("character.current") }),
    appearance: Object.freeze({
      getChatBackground: () => call("appearance.getChatBackground"),
      openChatBackgroundSettings: () => call("appearance.openChatBackgroundSettings"),
    }),
    settingLibrary: Object.freeze({ getSummary: () => call("settingLibrary.getSummary") }),
    input: Object.freeze({
      get: () => call("input.get"),
      set: (text) => call("input.set", { text }),
      append: (text) => call("input.append", { text }),
      clear: () => call("input.clear"),
      send: () => call("input.send"),
    }),
    presentation: Object.freeze({ renderMessageContent, disposeMessagePresentation }),
    events: Object.freeze({ list: () => call("events.list"), on, off }),
  });
})(window);
