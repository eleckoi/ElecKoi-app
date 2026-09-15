package com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime

/**
 * Gives each complete-document message iframe its own SDK transport while keeping the existing
 * transcript-to-native bridge as the single native boundary.
 */
internal val RoleplayTranscriptEmbeddedAuthorRuntime = """    const embeddedAuthorFrames = new WeakMap();
    const embeddedAuthorTargets = new Set();
    const pendingEmbeddedAuthorRequests = new Map();
    let embeddedAuthorSequence = 0;
    const embeddedAuthorBridge = Object.freeze({
      postMessage(sourceWindow, request) {
        const target = embeddedAuthorFrames.get(sourceWindow);
        if (!target || !richScopeAlive(target.slot, target.scope)) return;
        const raw = String(request || '');
        let parsed;
        try { parsed = JSON.parse(raw); } catch (_) { return; }
        const childRequestId = String(parsed?.id || '');
        if (!childRequestId) return;
        const nativeRequestId = 'embedded-author-' + (++embeddedAuthorSequence);
        pendingEmbeddedAuthorRequests.set(nativeRequestId, { target, childRequestId });
        post({
          type: 'author',
          messageId: target.slot.messageId,
          request: JSON.stringify({ ...parsed, id: nativeRequestId }),
        });
      },
    });
    Object.defineProperty(window, '__ElecKoiEmbeddedAuthorBridge', {
      value: embeddedAuthorBridge,
      configurable: false,
      enumerable: false,
      writable: false,
    });
    deliverEmbeddedAuthorResponse = response => {
      let parsed;
      try { parsed = JSON.parse(String(response || '')); } catch (_) { return false; }
      const nativeRequestId = String(parsed?.id || '');
      const pending = pendingEmbeddedAuthorRequests.get(nativeRequestId);
      if (!pending) return false;
      pendingEmbeddedAuthorRequests.delete(nativeRequestId);
      const target = pending.target;
      if (!richScopeAlive(target.slot, target.scope)) return true;
      const transport = target.frame.contentWindow?.ElecKoiNative;
      if (typeof transport?.onmessage === 'function') {
        transport.onmessage({ data: JSON.stringify({ ...parsed, id: pending.childRequestId }) });
      }
      return true;
    };
    deliverEmbeddedAuthorEvent = eventMessage => {
      embeddedAuthorTargets.forEach(target => {
        if (!richScopeAlive(target.slot, target.scope)) return;
        const transport = target.frame.contentWindow?.ElecKoiNative;
        if (typeof transport?.onmessage === 'function') {
          transport.onmessage({ data: eventMessage });
        }
      });
    };
    const embeddedAuthorBootstrapSource = () => `(() => {
      'use strict';
      const parentBridge = window.parent?.__ElecKoiEmbeddedAuthorBridge;
      const transport = {
        onmessage: null,
        postMessage(request) {
          if (typeof parentBridge?.postMessage === 'function') {
            parentBridge.postMessage(window, String(request || ''));
          }
        },
      };
      Object.defineProperty(window, 'ElecKoiNative', {
        value: transport,
        configurable: false,
        enumerable: false,
        writable: false,
      });
      const sdkSource = new TextDecoder().decode(
        Uint8Array.from(atob('${'$'}{authorSdkBase64}'), character => character.charCodeAt(0)),
      );
      (0, eval)(sdkSource);
    })();`;
    const injectEmbeddedAuthorBootstrap = source => {
      const bootstrap = authorLibrariesHead + '<script>' + embeddedAuthorBootstrapSource() + '<\/script>';
      const head = source.match(/<head(?:\s[^>]*)?>/i);
      if (head) return source.replace(head[0], head[0] + bootstrap);
      const html = source.match(/<html(?:\s[^>]*)?>/i);
      if (html) return source.replace(html[0], html[0] + '<head>' + bootstrap + '</head>');
      return bootstrap + source;
    };
    const registerEmbeddedAuthorFrame = (frame, slot, scope) => {
      const sourceWindow = frame.contentWindow;
      if (!sourceWindow) return;
      const target = { frame, slot, scope };
      embeddedAuthorFrames.set(sourceWindow, target);
      embeddedAuthorTargets.add(target);
      scope.cleanups.add(() => {
        embeddedAuthorFrames.delete(sourceWindow);
        embeddedAuthorTargets.delete(target);
        pendingEmbeddedAuthorRequests.forEach((pending, requestId) => {
          if (pending.target === target) pendingEmbeddedAuthorRequests.delete(requestId);
        });
      });
    };
"""
