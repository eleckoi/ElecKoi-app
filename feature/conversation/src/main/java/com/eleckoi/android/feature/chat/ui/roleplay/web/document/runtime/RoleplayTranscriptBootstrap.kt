package com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime

/** Waits for visible rich content to reach stable geometry before revealing the first frame. */
internal val RoleplayTranscriptBootstrap = """
    const initialPresentationActive = () => {
      const phase = state.initialPresentation.phase;
      return phase === 'projecting' || phase === 'settling';
    };
    const resetInitialPresentation = () => {
      const presentation = state.initialPresentation;
      if (presentation.watchdog) clearTimeout(presentation.watchdog);
      presentation.watchdog = 0;
      presentation.phase = 'idle';
      presentation.transactionId = 0;
      presentation.sessionId = '';
      presentation.required.clear();
      presentation.epoch += 1;
      presentation.stableEpoch = -1;
      presentation.checkQueued = false;
    };
    const beginInitialPresentation = payload => {
      resetInitialPresentation();
      const presentation = state.initialPresentation;
      presentation.phase = 'projecting';
      presentation.transactionId = Number(payload.transactionId || 0);
      presentation.sessionId = String(payload.sessionId || '');
      state.readyPosted = false;
      presentation.watchdog = setTimeout(() => {
        if (!initialPresentationActive() || state.fault) return;
        failRenderer(new Error('initial presentation did not settle'), 'initial-presentation-watchdog');
      }, 5000);
    };
    const initialViewportTurns = () => Array.from(turns.querySelectorAll(':scope > .turn')).filter(turn => {
      const rect = turn.getBoundingClientRect();
      return rect.bottom > 0 && rect.top < window.innerHeight;
    });
    const collectInitialPresentationRoots = () => {
      const presentation = state.initialPresentation;
      if (!initialPresentationActive()) return false;
      let changed = false;
      Array.from(presentation.required).forEach(root => {
        if (root.isConnected) return;
        presentation.required.delete(root);
        changed = true;
      });
      initialViewportTurns().forEach(turn => {
        richRootsWithin(turn).forEach(root => {
          if (presentation.required.has(root)) return;
          presentation.required.add(root);
          changed = true;
        });
      });
      if (changed) {
        presentation.epoch += 1;
        presentation.stableEpoch = -1;
        refreshRichViewport();
      }
      return changed;
    };
    const initialRootReady = root => {
      const slot = richSlotForRoot(root);
      return !!slot && (
        slot.phase === RichSlotPhase.FAILED ||
        (slot.phase === RichSlotPhase.RUNNING && slot.layoutReady && !slot.layoutPending)
      );
    };
    const noteInitialPresentationGeometryChange = root => {
      const presentation = state.initialPresentation;
      if (!initialPresentationActive() || !presentation.required.has(root)) return;
      presentation.epoch += 1;
      presentation.stableEpoch = -1;
      requestInitialPresentationCheck();
    };
    const completeInitialPresentation = () => {
      const presentation = state.initialPresentation;
      if (presentation.phase !== 'settling' || state.fault) return;
      if (presentation.watchdog) clearTimeout(presentation.watchdog);
      presentation.watchdog = 0;
      presentation.phase = 'committed';
      presentation.checkQueued = false;
      state.readyPosted = true;
      notifyScrollState();
      post({
        type: 'ready',
        transactionId: state.committedTransactionId,
        sessionId: state.sessionId,
      });
    };
    const requestInitialPresentationCheck = () => {
      const presentation = state.initialPresentation;
      if (presentation.phase !== 'settling' || presentation.checkQueued || state.fault) return;
      presentation.checkQueued = true;
      requestGeometryCommit({ renderRange: true, afterCommit: () => {
        presentation.checkQueued = false;
        if (presentation.phase !== 'settling' || state.fault) return;
        if (collectInitialPresentationRoots()) {
          requestInitialPresentationCheck();
          return;
        }
        if (!authorApiIdle() || Array.from(presentation.required).some(root => !initialRootReady(root))) {
          return;
        }
        if (state.scroll.geometryDirty) {
          requestInitialPresentationCheck();
          return;
        }
        if (!isAtPhysicalEnd()) {
          state.forceTail = state.messages.length > 0;
          requestGeometryCommit({ renderRange: true, forceRender: true });
          return;
        }
        if (presentation.stableEpoch !== presentation.epoch) {
          presentation.stableEpoch = presentation.epoch;
          requestInitialPresentationCheck();
          return;
        }
        completeInitialPresentation();
      } });
    };
    const settleInitialPresentation = payload => {
      const presentation = state.initialPresentation;
      if (
        presentation.phase !== 'projecting' ||
        presentation.transactionId !== Number(payload.transactionId || 0) ||
        presentation.sessionId !== String(payload.sessionId || '')
      ) return;
      presentation.phase = 'settling';
      collectInitialPresentationRoots();
      requestInitialPresentationCheck();
    };
    addEventListener('eleckoi:author-pending-change', requestInitialPresentationCheck);
"""
