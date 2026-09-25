package com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime

internal val RoleplayTranscriptUpdates = """    const authorApiIdle = () => {
      const pendingCount = window.__ElecKoiAuthorPendingCount;
      return typeof pendingCount !== 'function' || pendingCount() === 0;
    };
    const applyRoleplayMessageOptions = () => {
      document.body.classList.toggle('show-roleplay-timestamps', state.showRoleplayTimestamps);
      document.body.classList.toggle('show-roleplay-floors', state.showRoleplayMessageFloors);
    };
    const transactionIdOf = payload => Number(payload?.transactionId || 0);
    const rejectTransaction = (payload, reason) => post({
      type: 'transactionRejected',
      transactionId: transactionIdOf(payload),
      committedTransactionId: state.committedTransactionId,
      reason,
    });
    const acceptTransaction = (payload, requireBase) => {
      const transactionId = transactionIdOf(payload);
      if (!Number.isSafeInteger(transactionId) || transactionId <= 0) {
        rejectTransaction(payload, 'invalid-transaction');
        return false;
      }
      if (transactionId <= state.committedTransactionId) {
        post({ type: 'transactionCommitted', transactionId, sessionId: payload.sessionId || state.sessionId });
        return false;
      }
      const baseTransactionId = Number(payload.baseTransactionId || 0);
      if (requireBase && baseTransactionId !== state.committedTransactionId) {
        rejectTransaction(payload, 'base-mismatch');
        return false;
      }
      return true;
    };
    const commitTransaction = payload => {
      state.committedTransactionId = transactionIdOf(payload);
      post({
        type: 'transactionCommitted',
        transactionId: state.committedTransactionId,
        sessionId: payload.sessionId || state.sessionId,
      });
    };
    const applyFull = payload => {
      if (!acceptTransaction(payload, false)) return;
      state.fault = null;
      const sameSession = state.ready && state.sessionId === payload.sessionId;
      const needsInitialPresentation = !sameSession || state.initialPresentation.phase !== 'committed';
      const followEnd = !sameSession || followsTail() || isAtPhysicalEnd();
      captureGeometryIntent(followEnd);
      if (needsInitialPresentation) beginInitialPresentation(payload);
      if (!sameSession) {
        resetRichViewport();
        releaseRichWithin(turns);
        turns.replaceChildren();
        state.start = 0; state.end = 0;
        state.snapshots.clear();
      }
      state.sessionId = payload.sessionId; state.messages = payload.messages || [];
      state.showRoleplayTimestamps = payload.showRoleplayTimestamps !== false;
      state.showRoleplayMessageFloors = payload.showRoleplayMessageFloors !== false;
      applyRoleplayMessageOptions();
      state.floorStart = Math.max(0, Number(payload.floorStart) || 0);
      applyLayoutMode(payload.layoutMode);
      state.deleteMode = !!payload.deleteMode;
      state.deleteFromMessageId = String(payload.deleteFromMessageId || '');
      state.deleteFromIndex = state.deleteMode
        ? state.messages.findIndex(message => message.id === state.deleteFromMessageId)
        : -1;
      state.icons = payload.icons || state.icons;
      state.frontendRendererEnabled = payload.frontendRendererEnabled !== false;
      state.byId = new Map(state.messages.map(message => [message.id, message]));
      Object.entries(payload.richHeights || {}).forEach(([key, height]) => {
        const measured = Number(height);
        if (key && Number.isFinite(measured) && measured > 0) state.richHeights.set(key, measured);
      });
      applyStyle(payload.style);
      syncVirtualizer({ reset: !sameSession, followEnd });
      state.forceTail = needsInitialPresentation && state.messages.length > 0;
      state.loadRequested = !!payload.historyLoading || !payload.historyHasMore;
      empty.style.display = state.messages.length ? 'none' : 'block'; state.ready = true;
      requestGeometryCommit({
        renderRange: true,
        forceRender: true,
        afterCommit: () => {
          commitTransaction(payload);
          if (needsInitialPresentation) settleInitialPresentation(payload);
        },
      });
    };
    const applyPatch = payload => {
      if (!acceptTransaction(payload, true)) return;
      state.fault = null;
      const followEnd = followsTail() || isAtPhysicalEnd();
      captureGeometryIntent(followEnd);
      const frontendRendererChanged = typeof payload.frontendRendererEnabled === 'boolean' &&
        state.frontendRendererEnabled !== payload.frontendRendererEnabled;
      if (typeof payload.frontendRendererEnabled === 'boolean') {
        state.frontendRendererEnabled = payload.frontendRendererEnabled;
      }
      const deleteSelectionChanged =
        typeof payload.deleteMode === 'boolean' || typeof payload.deleteFromMessageId === 'string';
      if (typeof payload.deleteMode === 'boolean') state.deleteMode = payload.deleteMode;
      if (typeof payload.deleteFromMessageId === 'string') {
        state.deleteFromMessageId = payload.deleteFromMessageId;
      }
      if (typeof payload.layoutMode === 'string') applyLayoutMode(payload.layoutMode);
      const roleplayMessageOptionsChanged =
        typeof payload.showRoleplayTimestamps === 'boolean' ||
        typeof payload.showRoleplayMessageFloors === 'boolean';
      if (typeof payload.showRoleplayTimestamps === 'boolean') {
        state.showRoleplayTimestamps = payload.showRoleplayTimestamps;
      }
      if (typeof payload.showRoleplayMessageFloors === 'boolean') {
        state.showRoleplayMessageFloors = payload.showRoleplayMessageFloors;
      }
      if (roleplayMessageOptionsChanged) applyRoleplayMessageOptions();
      if (Number.isSafeInteger(payload.floorStart)) state.floorStart = Math.max(0, payload.floorStart);
      if (payload.style) applyStyle(payload.style);

      const previousById = state.byId;
      const incoming = payload.messages || [];
      if (Array.isArray(payload.order)) {
        const merged = new Map(previousById);
        incoming.forEach(message => merged.set(message.id, message));
        const allowed = new Set(payload.order);
        turns.querySelectorAll(':scope > .turn').forEach(turn => {
          if (allowed.has(turn.dataset.id)) return;
          captureTurnSnapshot(turn);
          releaseRichWithin(turn);
          turn.remove();
        });
        for (const id of state.snapshots.keys()) if (!allowed.has(id)) state.snapshots.delete(id);
        state.messages = payload.order.map(id => merged.get(id)).filter(Boolean);
      } else if (incoming.length) {
        const next = state.messages.slice();
        const indexById = new Map(next.map((message, index) => [message.id, index]));
        incoming.forEach(message => {
          const index = indexById.get(message.id);
          if (index === undefined) {
            indexById.set(message.id, next.length);
            next.push(message);
          } else {
            next[index] = message;
          }
        });
        state.messages = next;
      }
      state.byId = new Map(state.messages.map(message => [message.id, message]));
      state.deleteFromIndex = state.deleteMode
        ? state.messages.findIndex(message => message.id === state.deleteFromMessageId)
        : -1;
      if (frontendRendererChanged) {
        turns.querySelectorAll(':scope > .turn').forEach(turn => {
          const message = state.byId.get(turn.dataset.id);
          const nativePart = turn.querySelector(':scope > .turn-main > .message-body > .native-part');
          if (!message || !nativePart) return;
          releaseRichWithin(nativePart);
          nativePart.replaceChildren();
          reconcileContentParts(nativePart, message);
        });
      }
      virtualizer.measureElement(null);
      syncVirtualizer({ followEnd });

      incoming.forEach(message => {
        const previousMessage = previousById.get(message.id);
        const existing = turns.querySelector(`.turn[data-id="${'$'}{CSS.escape(message.id)}"]`);
        if (!existing || !previousMessage) return;
        if (previousMessage.selectedOpeningIndex !== message.selectedOpeningIndex && message.openingOptionIds?.length > 1) {
          replaceOpeningTurn(existing, message);
        } else {
          patchTurn(existing, previousMessage, message);
        }
      });
      if (payload.meta) {
        state.loadRequested = !!payload.meta.historyLoading || !payload.meta.historyHasMore;
      }
      empty.style.display = state.messages.length ? 'none' : 'block';
      requestGeometryCommit({
        renderRange: true,
        forceRender: Array.isArray(payload.order) || !!payload.style ||
          typeof payload.layoutMode === 'string' ||
          Number.isSafeInteger(payload.floorStart) || roleplayMessageOptionsChanged ||
          deleteSelectionChanged,
        afterCommit: () => {
          incoming.forEach(message => post({ type: 'messageRendered', messageId: message.id }));
          commitTransaction(payload);
        },
      });
    };
    window.__ElecKoiTranscript = {
      applyFull(payload) { return mutate('apply-full', () => applyFull(payload)); },
      applyPatch(payload) { return mutate('apply-patch', () => applyPatch(payload)); },
      scrollToEnd() {
        return mutate('scroll-to-end', () => {
          setScrollMode(ScrollMode.PROGRAMMATIC_JUMP, 'native-jump-to-end');
          state.forceTail = true;
          requestGeometryCommit({ renderRange: true, forceRender: true });
        });
      },
      diagnostics() {
        return {
          ...state.metrics,
          mountedTurns: turns.querySelectorAll(':scope > .turn').length,
          retainedHeights: virtualizer.itemSizeCache.size,
          virtualTotalSize: virtualizer.getTotalSize(),
          retainedSnapshots: state.snapshots.size,
          richSlots: state.richSlots.size,
          richSlotsRunning: Array.from(state.richSlots)
            .filter(slot => slot.phase === RichSlotPhase.RUNNING).length,
          richSlotsQueued: Array.from(state.richSlots)
            .filter(slot => slot.phase === RichSlotPhase.QUEUED).length,
          richViewportPaused: state.richViewport.paused,
          richViewportCapacity: state.richViewport.capacity,
          richDemandCost: state.richViewport.demandCost,
          richActiveCost: state.richViewport.activeCost,
          initialPresentationPhase: state.initialPresentation.phase,
          initialPresentationRequired: state.initialPresentation.required.size,
          initialPresentationReady: Array.from(state.initialPresentation.required)
            .filter(initialRootReady).length,
          scrollMode: state.scroll.mode,
          distanceFromEnd: distanceFromEnd(),
          scrollTransitions: state.scroll.transitions.slice(),
          committedTransactionId: state.committedTransactionId,
        };
      },
    };
"""
