package com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime

internal val RoleplayTranscriptProjection = """    const desiredVirtualItems = () => {
      const items = virtualizer.getVirtualItems();
      if (items.length || !state.messages.length) return items;
      const first = virtualizer.getMeasurements()[0];
      return first ? [first] : [];
    };
    const updateSpacers = (start = state.start, end = state.end) => {
      if (!state.messages.length || start >= end) {
        topSpacer.style.height = '0px';
        bottomSpacer.style.height = '0px';
        return;
      }
      const measurements = virtualizer.getMeasurements();
      const first = measurements[start];
      const last = measurements[end - 1];
      topSpacer.style.height = Math.max(0, first?.start || 0) + 'px';
      bottomSpacer.style.height = Math.max(0, virtualizer.getTotalSize() - (last?.end || 0)) + 'px';
    };
    const assertProjection = projectedItems => {
      const mounted = Array.from(turns.querySelectorAll(':scope > .turn'));
      const ids = new Set(mounted.map(turn => turn.dataset.id));
      if (ids.size !== mounted.length) throw new Error('projection contains duplicate turns');
      if (mounted.length !== projectedItems.length) {
        throw new Error('projection does not match TanStack virtual range');
      }
      mounted.forEach((turn, position) => {
        const item = projectedItems[position];
        const message = state.messages[item.index];
        if (!message || turn.dataset.id !== message.id || turn.dataset.index !== String(item.index)) {
          throw new Error('projection order diverged from TanStack virtual range');
        }
      });
    };
    const render = force => {
      const projectedItems = desiredVirtualItems();
      const start = projectedItems[0]?.index ?? 0;
      const end = projectedItems.length ? projectedItems[projectedItems.length - 1].index + 1 : 0;
      if (!force && start === state.start && end === state.end) {
        updateSpacers(start, end);
        virtualizer._willUpdate();
        assertProjection(projectedItems);
        refreshRichViewport();
        return false;
      }
      const reusable = new Map();
      turns.querySelectorAll(':scope > .turn').forEach(turn => reusable.set(turn.dataset.id, turn));
      const ordered = [];
      for (const item of projectedItems) {
        const message = state.messages[item.index];
        if (!message) continue;
        const previous = reusable.get(message.id);
        let turn;
        if (previous && previous.dataset.revision === String(message.revision || '')) {
          reusable.delete(message.id);
          previous.classList.toggle('card', state.cardPanel);
          turn = previous;
        } else {
          if (previous) {
            reusable.delete(message.id);
            captureTurnSnapshot(previous);
            releaseRichWithin(previous);
            previous.remove();
          }
          turn = createTurn(message, item.index);
        }
        turn.dataset.index = String(item.index);
        syncTurnFloor(turn, item.index);
        applyDeletePresentation(turn, item.index);
        ordered.push(turn);
      }
      let cursor = turns.firstElementChild;
      ordered.forEach(node => {
        if (node === cursor) {
          cursor = cursor.nextElementSibling;
        } else {
          turns.insertBefore(node, cursor);
        }
      });
      while (cursor) {
        const next = cursor.nextElementSibling;
        if (cursor.matches('.turn')) {
          captureTurnSnapshot(cursor);
          releaseRichWithin(cursor);
        }
        cursor.remove();
        cursor = next;
      }
      state.start = start; state.end = end;
      updateSpacers(start, end);
      ordered.forEach(turn => virtualizer.measureElement(turn));
      virtualizer.measureElement(null);
      updateSpacers(start, end);
      virtualizer._willUpdate();
      assertProjection(projectedItems);
      refreshRichViewport();
      return true;
    };
"""
