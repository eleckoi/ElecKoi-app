package com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime

internal val RoleplayTranscriptInteraction = """    const onViewportResize = () => {
      const followEnd = followsTail() || isAtPhysicalEnd();
      captureGeometryIntent(followEnd);
      refreshImageGalleryGeometry();
      if (followEnd) {
        setScrollMode(ScrollMode.VIEWPORT_RESIZE, 'viewport-resize');
        state.forceTail = true;
      }
      requestGeometryCommit({ renderRange: true });
    };
    addEventListener('resize', onViewportResize, { passive: true });
    if (window.visualViewport && window.visualViewport !== window) {
      window.visualViewport.addEventListener('resize', onViewportResize, { passive: true });
    }
    addEventListener('pointerdown', event => {
      const authorTurn = event.target.closest?.('.turn');
      if (authorTurn?.dataset.id) state.activeAuthorMessageId = authorTurn.dataset.id;
      state.scroll.programmaticWrite = false;
      state.forceTail = false;
      state.scroll.gesturePointerId = event.pointerId;
      state.scroll.gestureY = event.clientY;
      state.scroll.gestureDistance = 0;
      state.scroll.historyIntent = false;
    }, { passive: true, capture: true });
    addEventListener('pointermove', event => {
      if (event.pointerId !== state.scroll.gesturePointerId) return;
      const deltaY = event.clientY - state.scroll.gestureY;
      state.scroll.gestureY = event.clientY;
      state.scroll.gestureDistance += deltaY;
      if (Math.abs(state.scroll.gestureDistance) > 8 && !state.scroll.historyIntent) {
        state.scroll.historyIntent = true;
        claimHistoryBrowsing('pointer-drag');
      }
    }, { passive: true, capture: true });
    const finishScrollGesture = event => {
      if (event.pointerId !== state.scroll.gesturePointerId) return;
      state.scroll.gesturePointerId = null;
      if (followsTail()) state.scroll.historyIntent = false;
    };
    addEventListener('pointerup', finishScrollGesture, { passive: true, capture: true });
    addEventListener('pointercancel', finishScrollGesture, { passive: true, capture: true });
    addEventListener('wheel', event => {
      const canMove = event.deltaY < 0 ? window.scrollY > 1 : !isAtPhysicalEnd();
      if (!canMove) return;
      if (Math.abs(event.deltaY) > 0) state.scroll.historyIntent = true;
      if (event.deltaY < 0) claimHistoryBrowsing('wheel-to-history');
    }, { passive: true });
    const ingress = new Map();
    window.__ElecKoiTranscriptIngress = {
      start(token) { ingress.set(token, []); },
      append(token, chunk) { const target = ingress.get(token); if (target) target.push(chunk); },
      commit(token, method) {
        const chunks = ingress.get(token); ingress.delete(token);
        const target = window.__ElecKoiTranscript?.[method];
        if (!chunks || typeof target !== 'function') return;
        try {
          const binary = atob(chunks.join('')), bytes = new Uint8Array(binary.length);
          for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
          target(JSON.parse(new TextDecoder().decode(bytes)));
        } catch (error) { failRenderer(error, 'native-command-ingress'); }
      },
    };
    const presentToolbar = (turn, message, expanded) => {
      if (!turn || !message || message.pending) return;
      const header = turn.querySelector('.turn-header'), strip = turn.querySelector('.tool-strip');
      if (!header || !strip) return;
      if (expanded) header.classList.add('toolbar-expanded');
      strip.classList.add('swapping');
      setTimeout(() => {
        strip.innerHTML = toolbarContent(message, expanded);
        if (!expanded) header.classList.remove('toolbar-expanded');
        requestAnimationFrame(() => strip.classList.remove('swapping'));
      }, 125);
    };
    const setExpandedToolbar = messageId => {
      const previous = state.expandedToolbarId;
      if (previous === messageId) return;
      state.expandedToolbarId = messageId;
      if (previous) {
        const previousTurn = turns.querySelector(`.turn[data-id="${'$'}{CSS.escape(previous)}"]`);
        presentToolbar(previousTurn, state.byId.get(previous), false);
      }
      if (messageId) {
        const nextTurn = turns.querySelector(`.turn[data-id="${'$'}{CSS.escape(messageId)}"]`);
        presentToolbar(nextTurn, state.byId.get(messageId), true);
      }
    };
    turns.addEventListener('click', event => {
      const target = event.target.closest('[data-action]'); if (!target) return;
      const turn = target.closest('.turn'), message = turn ? state.byId.get(turn.dataset.id) : null; if (!message) return;
      const action = target.dataset.action;
      if (action === 'delete-select') {
        if (!message.pending && message.role !== 'system') {
          post({ type: 'deleteSelect', messageId: message.id });
        }
        return;
      }
      if (action === 'menu') {
        setExpandedToolbar(message.id); return;
      }
      if (action.startsWith('opening-')) {
        const count = message.openingOptionIds.length; let index = message.selectedOpeningIndex;
        if (action === 'opening-prev') index = Math.max(0, index - 1);
        if (action === 'opening-next') index = Math.min(count - 1, index + 1);
        if (action === 'opening-jump') {
          post({ type: 'openingJump' }); return;
        }
        if (index !== message.selectedOpeningIndex) post({ type: 'opening', optionId: message.openingOptionIds[index] });
        return;
      }
      if (action === 'avatar') post({ type: message.role === 'user' ? 'userAvatar' : 'assistantAvatar' });
      else if (action !== 'translate' && action !== 'speaker') post({ type: 'messageAction', action, messageId: message.id });
    });
    let imageLongPressTimer = 0;
    let imageLongPressFrame = null;
    let imageLongPressX = 0;
    let imageLongPressY = 0;
    const cancelImageLongPress = () => {
      clearTimeout(imageLongPressTimer);
      imageLongPressTimer = 0;
      imageLongPressFrame = null;
    };
    turns.addEventListener('pointerdown', event => {
      const frame = event.target.closest?.('.story-image-frame');
      if (!frame || event.button !== 0) return;
      cancelImageLongPress();
      imageLongPressFrame = frame; imageLongPressX = event.clientX; imageLongPressY = event.clientY;
      imageLongPressTimer = setTimeout(() => {
        const target = imageLongPressFrame;
        cancelImageLongPress();
        openImageMenu(target, imageLongPressX, imageLongPressY);
      }, 520);
    }, { passive: true });
    turns.addEventListener('pointermove', event => {
      if (!imageLongPressTimer) return;
      if (Math.hypot(event.clientX - imageLongPressX, event.clientY - imageLongPressY) > 10) {
        cancelImageLongPress();
      }
    }, { passive: true });
    turns.addEventListener('pointerup', cancelImageLongPress, { passive: true });
    turns.addEventListener('pointercancel', cancelImageLongPress, { passive: true });
    turns.addEventListener('contextmenu', event => {
      const frame = event.target.closest?.('.story-image-frame');
      if (!frame) return;
      event.preventDefault();
      cancelImageLongPress();
      openImageMenu(frame, event.clientX, event.clientY);
    });
    document.addEventListener('click', event => {
      if (!event.target.closest('#image-menu') && performance.now() - imageMenuOpenedAt > 350) {
        closeImageMenu();
      }
      if (!event.target.closest('.tools')) setExpandedToolbar(null);
      const link = event.target.closest('a[href]'); if (link) { event.preventDefault(); post({ type: 'openLink', url: link.href }); }
    });
    if ('PerformanceObserver' in window) {
      try {
        new PerformanceObserver(entries => {
          state.metrics.longTasks += entries.getEntries().length;
          state.metrics.lastLongTaskAt = performance.now();
        }).observe({ type: 'longtask', buffered: true });
      } catch (_) {}
    }
    addEventListener('pagehide', () => {
      shutdownRichViewport();
      disposeVirtualizer();
    }, { once: true });
    addEventListener('scroll', () => {
      cancelImageLongPress(); closeImageMenu();
      const currentTop = window.scrollY;
      const delta = currentTop - state.scroll.observedTop;
      const programmatic = state.scroll.programmaticWrite &&
        Math.abs(currentTop - state.scroll.programmaticTop) <= 1;
      state.scroll.programmaticWrite = false;
      if (!programmatic) {
        const gestureTowardHistory = state.scroll.gesturePointerId !== null && delta < -.5;
        if (
          (state.scroll.mode === ScrollMode.USER_BROWSING ||
            state.scroll.mode === ScrollMode.PRESERVE_ANCHOR) &&
          delta > 0 && isAtPhysicalEnd()
        ) {
          setScrollMode(ScrollMode.FOLLOW_TAIL, 'user-reached-physical-end');
        } else if (state.scroll.historyIntent || gestureTowardHistory) {
          claimHistoryBrowsing(
            state.scroll.historyIntent ? 'history-gesture-scroll' : 'pointer-scroll-to-history',
          );
        }
      }
      state.scroll.observedTop = currentTop;
      requestGeometryCommit({ renderRange: true });
    }, { passive: true });
  })();
  """
