package com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime

internal val RoleplayTranscriptRuntimeCore = """
  (() => {
    'use strict';
    const native = window.ElecKoiTranscript;
    const post = value => {
      if (native && typeof native.postMessage === 'function') native.postMessage(JSON.stringify(value));
    };
    const rendererErrorPayload = (error, phase, transactionId = 0, sessionId = '') => ({
      type: 'rendererError',
      message: String(error?.message || error || 'renderer fault'),
      stack: String(error?.stack || ''),
      phase: String(phase || ''),
      transactionId,
      sessionId,
    });
    let reportRendererFailure = (error, phase) => {
      post(rendererErrorPayload(error, phase));
    };
    addEventListener('error', event => {
      reportRendererFailure(
        event.error || new Error(String(event.message || 'uncaught window error')),
        'window-error',
      );
    });
    addEventListener('unhandledrejection', event => {
      const reason = event.reason instanceof Error
        ? event.reason
        : new Error(String(event.reason || 'unhandled promise rejection'));
      reportRendererFailure(reason, 'unhandled-promise-rejection');
    });
    const authorSdkBase64 = '__ELECKOI_AUTHOR_SDK_BASE64__';
    const authorSdkSource = new TextDecoder().decode(
      Uint8Array.from(atob(authorSdkBase64), character => character.charCodeAt(0)),
    );
    const authorLibrariesBase64 = '__ELECKOI_AUTHOR_LIBRARIES_BASE64__';
    const authorLibrariesHead = new TextDecoder().decode(
      Uint8Array.from(atob(authorLibrariesBase64), character => character.charCodeAt(0)),
    );
    const topSpacer = document.getElementById('top-spacer');
    const turns = document.getElementById('turns');
    const bottomSpacer = document.getElementById('bottom-spacer');
    const empty = document.getElementById('empty');
    const imageMenu = document.getElementById('image-menu');
    const virtualRuntime = window.ElecKoiTanStackVirtual;
    if (!virtualRuntime?.Virtualizer) throw new Error('TanStack Virtual runtime failed to load');
    const state = {
      sessionId: '', messages: [], byId: new Map(), floorStart: 0,
      start: 0, end: 0, ready: false, atEnd: true, loadRequested: false,
      readyPosted: false,
      initialPresentation: {
        phase: 'idle', transactionId: 0, sessionId: '', required: new Set(),
        epoch: 0, stableEpoch: -1, checkQueued: false, watchdog: 0,
      },
      layoutMode: 'roleplay',
      showRoleplayTimestamps: true,
      showRoleplayMessageFloors: true,
      cardPanel: false,
      frontendRendererEnabled: true,
      deleteMode: false,
      deleteFromMessageId: '',
      deleteFromIndex: -1,
      forceTail: false,
      scroll: {
        mode: 'follow-tail', frame: 0, renderRequested: false,
        forceRender: false, afterCommit: [], geometryDirty: false, urgentGeometryQueued: false,
        programmaticWrite: false, compensationCommitPending: false,
        programmaticTop: 0, observedTop: 0, virtualScrolling: false,
        gesturePointerId: null, gestureY: 0, gestureDistance: 0, historyIntent: false,
        transitionSequence: 0, transitions: [],
      },
      richSlots: new Set(), richSlotByRoot: new WeakMap(), activeRichScope: null,
      snapshots: new Map(), richHeights: new Map(),
      richViewport: {
        handle: 0, scheduler: '', pending: [], paused: false, refreshQueued: false,
        epoch: 0, stopped: false,
        activeCost: 0, demandCost: 0, capacity: 0,
      },
      fault: null, mutation: null,
      committedTransactionId: 0,
      metrics: {
        geometryCommits: 0, programmaticScrollWrites: 0,
        compensationScrollWrites: 0,
        lastCommitDurationMs: 0, maxCommitDurationMs: 0, longTasks: 0, lastLongTaskAt: -Infinity,
        richSlotFailures: 0,
      },
      icons: {}, style: {}, expandedToolbarId: null, activeAuthorMessageId: '',
    };
    const normalizedImageAspectRatio = image => {
      const value = Number(image?.aspectRatio);
      return Number.isFinite(value) && value > 0 ? Math.max(.2, Math.min(5, value)) : 1;
    };
    const singleImageWidthFraction = image => {
      if (Number(image?.frameCount) <= 1) return 1;
      return normalizedImageAspectRatio(image) < .85 ? .74 : .88;
    };
    const estimatedImageContentWidth = message => {
      const horizontalPadding = Math.max(0, Number(state.style.horizontalPaddingPx) || 0);
      const available = Math.max(120, window.innerWidth - (horizontalPadding * 2));
      if (state.layoutMode === 'agent') {
        const bubblePadding = message.role === 'user' || state.style.assistantBubbleEnabled ? 24 : 0;
        const width = message.role === 'user' ? available * .88 : available;
        return Math.max(80, width - bubblePadding);
      }
      const avatarLane = Math.max(0, Number(state.style.avatarWidthPx) || 0) +
        Math.max(0, Number(state.style.avatarGapPx) || 0);
      const main = Math.max(120, available - avatarLane);
      if (state.layoutMode === 'social') return Math.max(80, (main * .88) - 24);
      return main;
    };
    const estimateImageGalleryHeight = (message, images) => {
      const items = Array.isArray(images) ? images : [];
      if (!items.length) return 0;
      const contentWidth = estimatedImageContentWidth(message);
      if (items.length === 1) {
        const image = items[0];
        return (contentWidth * singleImageWidthFraction(image)) / normalizedImageAspectRatio(image);
      }
      const columns = items.length === 2 || items.length === 4 ? 2 : 3;
      const cellWidth = Math.max(1, (contentWidth - ((columns - 1) * 4)) / columns);
      let height = 0;
      for (let index = 0; index < items.length; index += columns) {
        const row = items.slice(index, index + columns);
        height += Math.max(...row.map(image => cellWidth / normalizedImageAspectRatio(image)));
        if (index + columns < items.length) height += 4;
      }
      return height;
    };
    const estimateHeight = message => {
      const textHeight = Math.max(150, 105 + ((message.copyText || '').length / 16));
      const imageParts = (message.parts || []).filter(part => part.type === 'images');
      const imageHeight = imageParts.reduce(
        (height, part) => height + estimateImageGalleryHeight(message, part.images),
        0,
      );
      return Math.max(150, Math.min(4000, textHeight + imageHeight + (imageParts.length * 4)));
    };
    const measureTurnHeight = (turn, entry) => {
      const style = getComputedStyle(turn);
      const marginTop = Number.parseFloat(style.marginTop) || 0;
      const marginBottom = Number.parseFloat(style.marginBottom) || 0;
      const box = entry?.borderBoxSize?.[0];
      const blockSize = box?.blockSize ?? turn.getBoundingClientRect().height;
      return Math.max(1, blockSize + marginTop + marginBottom);
    };
    let virtualizer = null;
    let disposeVirtualizer = () => {};
    const ScrollMode = Object.freeze({
      FOLLOW_TAIL: 'follow-tail',
      USER_BROWSING: 'user-browsing',
      PRESERVE_ANCHOR: 'preserve-anchor',
      PROGRAMMATIC_JUMP: 'programmatic-jump',
      VIEWPORT_RESIZE: 'viewport-resize',
    });
    const distanceFromEnd = () => Math.max(
      0,
      document.documentElement.scrollHeight - window.innerHeight - window.scrollY,
    );
    const isAtPhysicalEnd = () => distanceFromEnd() <= 1;
    const followsTail = () => state.scroll.mode === ScrollMode.FOLLOW_TAIL ||
      state.scroll.mode === ScrollMode.PROGRAMMATIC_JUMP ||
      state.scroll.mode === ScrollMode.VIEWPORT_RESIZE;
    const isAtUiEnd = () => followsTail() || distanceFromEnd() <= (state.atEnd ? 96 : 24);
    const virtualScrollTo = (offset, options, instance) => {
      const adjustments = Number(options.adjustments || 0);
      const target = Math.max(0, offset + adjustments);
      state.scroll.programmaticWrite = true;
      state.scroll.programmaticTop = target;
      state.scroll.compensationCommitPending = adjustments !== 0;
      state.metrics.programmaticScrollWrites += 1;
      if (adjustments !== 0) state.metrics.compensationScrollWrites += 1;
      virtualRuntime.windowScroll(offset, options, instance);
    };
    const snapToEnd = () => virtualizer?.scrollToEnd({ behavior: 'auto' });
    const setScrollMode = (next, cause) => {
      const previous = state.scroll.mode;
      if (previous === next) return;
      state.scroll.mode = next;
      if (next !== ScrollMode.USER_BROWSING && next !== ScrollMode.PRESERVE_ANCHOR) {
        state.scroll.historyIntent = false;
      }
      const transition = {
        sequence: ++state.scroll.transitionSequence,
        from: previous,
        to: next,
        cause,
        top: Math.round(window.scrollY),
        distanceFromEnd: Math.round(distanceFromEnd()),
      };
      state.scroll.transitions.push(transition);
      if (state.scroll.transitions.length > 24) state.scroll.transitions.shift();
    };
    const claimHistoryBrowsing = cause => {
      state.scroll.programmaticWrite = false;
      state.scroll.historyIntent = true;
      state.forceTail = false;
      setScrollMode(ScrollMode.USER_BROWSING, cause);
    };
    const failRenderer = (error, phase) => {
      if (state.fault) return;
      state.fault = String(error?.message || error || 'renderer fault');
      if (state.scroll.frame) cancelAnimationFrame(state.scroll.frame);
      state.scroll.frame = 0;
      if (state.richViewport.handle) {
        if (state.richViewport.scheduler === 'idle' && 'cancelIdleCallback' in window) {
          cancelIdleCallback(state.richViewport.handle);
        } else {
          cancelAnimationFrame(state.richViewport.handle);
        }
      }
      state.richViewport.handle = 0;
      state.richViewport.scheduler = '';
      post(
        rendererErrorPayload(
          error,
          phase,
          state.committedTransactionId,
          state.sessionId,
        ),
      );
    };
    reportRendererFailure = failRenderer;
    const mutate = (label, operation) => {
      if (state.mutation) throw new Error(`mutation ${'$'}{label} reentered during ${'$'}{state.mutation}`);
      state.mutation = label;
      try { return operation(); } finally { state.mutation = null; }
    };
    const authorTransport = {
      onmessage: null,
      postMessage(request) {
        const messageId = state.activeAuthorMessageId;
        if (!messageId) return;
        post({ type: 'author', messageId, request: String(request || '') });
      },
    };
    let deliverEmbeddedAuthorResponse = () => false;
    let deliverEmbeddedAuthorEvent = () => {};
    window.ElecKoiNative = authorTransport;
    if (native) {
      native.onmessage = event => {
        let message;
        try { message = JSON.parse(event.data); } catch (_) { return; }
        if (message.type === 'authorResult') {
          if (deliverEmbeddedAuthorResponse(message.response)) return;
          if (typeof authorTransport.onmessage === 'function') {
            authorTransport.onmessage({ data: message.response });
          }
          return;
        }
        if (message.type === 'authorEvent') {
          const eventMessage = JSON.stringify({
            type: 'event',
            event: String(message.event || ''),
            payload: message.payload ?? null,
          });
          if (typeof authorTransport.onmessage === 'function') {
            authorTransport.onmessage({ data: eventMessage });
          }
          deliverEmbeddedAuthorEvent(eventMessage);
          return;
        }
        if (message.type === 'nativeCommand') {
          const target = window.__ElecKoiTranscript?.[message.method];
          if (typeof target === 'function') target(message.payload || {});
        }
      };
    }
    try {
      (0, eval)(authorSdkSource);
    } catch (error) {
      failRenderer(error, 'author-sdk-evaluation');
    }
    const notifyScrollState = () => {
      const nextAtEnd = isAtUiEnd();
      if (nextAtEnd !== state.atEnd) {
        state.atEnd = nextAtEnd;
        post({ type: 'scrollState', browsingHistory: !nextAtEnd, canScrollForward: !nextAtEnd });
      }
      if (window.scrollY < 420 && state.messages.length && !state.loadRequested) {
        state.loadRequested = true;
        post({ type: 'loadOlder' });
      }
    };
    const captureGeometryIntent = followEnd => {
      if (followEnd) {
        setScrollMode(ScrollMode.FOLLOW_TAIL, 'geometry-follow');
      } else {
        setScrollMode(ScrollMode.PRESERVE_ANCHOR, 'geometry-preserve-anchor');
        state.forceTail = false;
      }
    };
    const commitGeometry = () => {
      if (state.fault) return;
      const startedAt = performance.now();
      state.scroll.frame = 0;
      state.scroll.urgentGeometryQueued = false;
      const shouldRender = state.scroll.renderRequested;
      const shouldForceRender = state.scroll.forceRender;
      const callbacks = state.scroll.afterCommit.splice(0);
      state.scroll.renderRequested = false;
      state.scroll.forceRender = false;
      try {
        mutate('geometry-commit', () => {
          state.scroll.geometryDirty = false;
          if (shouldRender) render(shouldForceRender);
          const snapRequested = state.forceTail && state.messages.length > 0;
          state.forceTail = false;
          if (snapRequested) {
            snapToEnd();
            setScrollMode(ScrollMode.FOLLOW_TAIL, 'geometry-tail-commit');
            state.scroll.afterCommit.unshift(...callbacks);
            requestGeometryCommit({ renderRange: true, forceRender: true });
          } else {
            const nowAtEnd = isAtPhysicalEnd();
            if (!followsTail()) {
              setScrollMode(
                nowAtEnd ? ScrollMode.FOLLOW_TAIL : ScrollMode.USER_BROWSING,
                nowAtEnd ? 'virtual-anchor-reached-physical-end' : 'virtual-anchor-preserved',
              );
            }
            callbacks.forEach(callback => callback());
          }
          notifyScrollState();
          syncRichViewportToScroll();
          requestInitialPresentationCheck();
          const duration = performance.now() - startedAt;
          state.metrics.geometryCommits += 1;
          state.metrics.lastCommitDurationMs = duration;
          state.metrics.maxCommitDurationMs = Math.max(state.metrics.maxCommitDurationMs, duration);
        });
      } catch (error) {
        failRenderer(error, 'geometry-commit');
      }
    };
    const requestGeometryCommit = ({
      renderRange = false, forceRender = false, afterCommit = null, urgent = false,
    } = {}) => {
      if (state.fault) return;
      state.scroll.renderRequested ||= renderRange;
      state.scroll.forceRender ||= forceRender;
      if (typeof afterCommit === 'function') state.scroll.afterCommit.push(afterCommit);
      if (urgent) {
        if (state.scroll.frame) cancelAnimationFrame(state.scroll.frame);
        state.scroll.frame = 0;
        if (!state.mutation) {
          commitGeometry();
        } else if (!state.scroll.urgentGeometryQueued) {
          state.scroll.urgentGeometryQueued = true;
          queueMicrotask(commitGeometry);
        }
        return;
      }
      if (state.scroll.frame || state.scroll.urgentGeometryQueued) return;
      state.scroll.frame = requestAnimationFrame(() => {
        commitGeometry();
      });
    };
    const virtualizerOptions = (messages, followOnAppend, enabled = true) => ({
      count: messages.length,
      getScrollElement: () => window,
      estimateSize: index => estimateHeight(messages[index] || {}),
      getItemKey: index => messages[index]?.id ?? `missing-${'$'}{index}`,
      observeElementRect: virtualRuntime.observeWindowRect,
      observeElementOffset: virtualRuntime.observeWindowOffset,
      scrollToFn: virtualScrollTo,
      measureElement: (element, entry) => measureTurnHeight(element, entry),
      overscan: 2,
      anchorTo: 'end',
      followOnAppend: followOnAppend ? 'auto' : false,
      scrollEndThreshold: 48,
      useScrollendEvent: true,
      useAnimationFrameWithResizeObserver: true,
      enabled,
      onChange: (_, sync) => {
        state.scroll.virtualScrolling = !!sync;
        state.scroll.geometryDirty = true;
        // `sync` also means ordinary scrolling is in progress. Only a non-zero
        // TanStack scroll adjustment needs a same-paint projection commit.
        const compensationCommit = !!sync && state.scroll.compensationCommitPending;
        state.scroll.compensationCommitPending = false;
        requestGeometryCommit({ renderRange: true, urgent: compensationCommit });
      },
    });
    virtualizer = new virtualRuntime.Virtualizer(virtualizerOptions([], false));
    disposeVirtualizer = virtualizer._didMount();
    virtualizer._willUpdate();
    const syncVirtualizer = ({ reset = false, followEnd = false } = {}) => {
      if (reset) {
        virtualizer.setOptions(virtualizerOptions([], false, false));
        virtualizer._willUpdate();
        virtualizer.measure();
      }
      virtualizer.setOptions(virtualizerOptions(state.messages, followEnd));
    };
    const syncUserBubbleEditTarget = (body, message) => {
      const editable = !!message && message.role === 'user' && !message.pending && !state.deleteMode &&
        (state.layoutMode === 'agent' || state.layoutMode === 'social');
      body.classList.toggle('user-editable', editable);
      if (editable) {
        body.tabIndex = 0;
        body.title = '修改输入';
      } else {
        body.removeAttribute('tabindex');
        body.removeAttribute('title');
      }
    };
    const applyLayoutMode = value => {
      const layoutMode = ['social', 'agent', 'roleplay'].includes(value) ? value : 'roleplay';
      const changed = state.layoutMode !== layoutMode;
      state.layoutMode = layoutMode;
      document.body.dataset.layout = layoutMode;
      if (changed) turns.querySelectorAll('.turn.role-user .message-body').forEach(body => {
        const turn = body.closest('.turn');
        syncUserBubbleEditTarget(body, turn ? state.byId.get(turn.dataset.id) : null);
      });
      document.documentElement.style.setProperty(
        '--roleplay-text-shadow',
        layoutMode === 'roleplay' && state.style.dark ? '0 0 1px rgba(0,0,0,.3)' : 'none',
      );
      if (typeof refreshImageGalleryGeometry === 'function') refreshImageGalleryGeometry();
    };
    const applyStyle = style => {
      const root = document.documentElement.style;
      const avatarHeight = Math.max(0, Number(style.avatarHeightPx) || 0);
      const socialTailCenter = avatarHeight / 2;
      const properties = {
        '--text': style.text, '--body-text': style.bodyText, '--italic-text': style.italicText,
        '--assistant-text': style.assistantText, '--user-text': style.userText,
        '--underline-text': style.underlineText, '--quote-text': style.quoteText,
        '--inline-code-text': style.inlineCodeText, '--muted': style.muted, '--soft': style.soft,
        '--accent': style.accent, '--panel': style.panel, '--line': style.line,
        '--assistant-bubble': style.assistantBubble, '--user-bubble': style.userBubble,
        '--eleckoi-foreground': style.bodyText, '--eleckoi-muted': style.muted,
        '--eleckoi-accent': style.accent,
        '--jump-surface': style.jumpSurface,
        '--avatar-background': style.avatarBackground, '--avatar-placeholder': style.avatarPlaceholder,
        '--code-foreground': style.codeForeground, '--code-background': style.codeBackground,
        '--code-border': style.codeBorder, '--code-header': style.codeHeaderBackground,
        '--font-size': style.fontSizePx + 'px', '--line-height': style.lineHeightPx + 'px',
        '--letter-spacing': style.letterSpacingPx + 'px', '--paragraph-gap': style.paragraphSpacingPx + 'px',
        '--name-size': style.nameFontSizePx + 'px', '--name-line-height': style.nameLineHeightPx + 'px',
        '--avatar-width': style.avatarWidthPx + 'px',
        '--avatar-height': style.avatarHeightPx + 'px', '--avatar-radius': style.avatarRadiusPx + 'px',
        '--social-tail-top': Math.max(0, socialTailCenter - 5) + 'px',
        '--social-tail-center': socialTailCenter + 'px',
        '--social-tail-bottom': socialTailCenter + 5 + 'px',
        '--avatar-gap': style.avatarGapPx + 'px', '--horizontal-padding': style.horizontalPaddingPx + 'px',
        '--reply-gap': style.replySpacingPx + 'px', '--turn-gap': style.turnSpacingPx + 'px',
        '--bubble-radius': style.bubbleRadiusPx + 'px',
      };
      Object.entries(properties).forEach(([key, value]) => root.setProperty(key, value));
      root.setProperty(
        '--roleplay-text-shadow',
        state.layoutMode === 'roleplay' && style.dark ? '0 0 1px rgba(0,0,0,.3)' : 'none',
      );
      document.documentElement.style.colorScheme = style.dark ? 'dark' : 'light';
      document.body.classList.toggle('assistant-bubbles', !!style.assistantBubbleEnabled);
      state.cardPanel = !!style.cardPanel; state.style = style;
      if (typeof refreshImageGalleryGeometry === 'function') refreshImageGalleryGeometry();
    };
    const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, char => ({
      '&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'
    })[char]);
    if (!window.showdown || !window.DOMPurify) {
      throw new Error('Markdown runtime failed to load');
    }
"""
