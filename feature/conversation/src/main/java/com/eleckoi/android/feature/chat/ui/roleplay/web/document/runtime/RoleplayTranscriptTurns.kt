package com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime

internal val RoleplayTranscriptTurns = """    const missingAvatar = () => '<svg class="avatar-placeholder" viewBox="0 0 256 256" aria-hidden="true"><path d="M230.93,220a8,8,0,0,1-6.93,4H32a8,8,0,0,1-6.92-12c15.23-26.33,38.7-45.21,66.09-54.16a72,72,0,1,1,73.66,0c27.39,8.95,50.86,27.83,66.09,54.16A8,8,0,0,1,230.93,220Z"></path></svg>';
    const applyDeletePresentation = (turn, index) => {
      const active = state.deleteMode;
      const message = state.messages[index];
      const enabled = !!message && !message.pending && message.role !== 'system';
      const selected = active && state.deleteFromIndex >= 0 && index >= state.deleteFromIndex;
      turn.classList.toggle('delete-mode', active);
      turn.classList.toggle('delete-selected', selected);
      let selector = turn.querySelector(':scope > .delete-selector');
      if (!active) {
        if (selector) selector.remove();
        return;
      }
      if (!selector) {
        selector = document.createElement('button');
        selector.type = 'button';
        selector.className = 'delete-selector';
        selector.dataset.action = 'delete-select';
        selector.setAttribute('role', 'checkbox');
        turn.prepend(selector);
      }
      selector.disabled = !enabled;
      selector.classList.toggle('is-selected', selected);
      selector.setAttribute('aria-checked', selected ? 'true' : 'false');
      selector.setAttribute(
        'aria-label',
        selected ? '从这条消息开始删除，已选中' : '从这条消息开始删除',
      );
    };
    const createBody = message => {
      const body = document.createElement('div'); body.className = 'message-body';
      if (message.liveStatus && message.liveStatus.label) {
        const status = document.createElement('button');
        status.type = 'button'; status.dataset.action = 'history';
        status.className = 'live-status' + (message.liveStatus.running ? ' running' : '') + (message.liveStatus.thinking ? ' thinking' : '');
        status.setAttribute('aria-label', `查看处理过程：${'$'}{message.liveStatus.label}`);
        const indicator = message.liveStatus.thinking
          ? thinkingMascot(message.liveStatus)
          : transcriptVectorIcon(message.liveStatus.icon, 17);
        const shimmerDelay = -(performance.now() % 1400);
        status.innerHTML = `<span class="live-status-indicator" aria-hidden="true">${'$'}{indicator}</span><span class="live-status-label" style="--shimmer-delay:${'$'}{shimmerDelay}ms">${'$'}{escapeHtml(message.liveStatus.label)}</span><span class="live-status-chevron">${'$'}{svgIcon('chevronRight', 16, 1.7)}</span>`;
        body.append(status);
      } else {
        const nativePart = document.createElement('div'); nativePart.className = 'native-part';
        reconcileContentParts(nativePart, message);
        body.append(nativePart);
        if (message.reasoning) {
          const details = document.createElement('details'); details.className = 'reasoning';
          details.innerHTML = `<summary>思考过程</summary><div class="reasoning-content">${'$'}{escapeHtml(message.reasoning)}</div>`;
          body.append(details);
        }
      }
      return body;
    };
    const createTurn = message => {
      const turn = document.createElement('article');
      const pagerVisible = message.openingOptionIds && message.openingOptionIds.length > 1 && message.selectedOpeningIndex >= 0;
      const expanded = state.expandedToolbarId === message.id;
      turn.className = 'turn' + (state.cardPanel ? ' card' : '') + (pagerVisible ? ' has-pager' : '');
      turn.dataset.id = message.id; turn.dataset.revision = String(message.revision || '');
      const lane = document.createElement('div'); lane.className = 'portrait-lane';
      const avatar = document.createElement('button'); avatar.className = 'avatar'; avatar.type = 'button'; avatar.dataset.action = 'avatar';
      if (message.avatarUrl) avatar.innerHTML = `<img src="${'$'}{escapeHtml(message.avatarUrl)}" alt="">`;
      else avatar.innerHTML = missingAvatar();
      lane.append(avatar);
      if (pagerVisible) {
        const pager = document.createElement('div'); pager.className = 'pager';
        pager.innerHTML = `<button data-action="opening-prev" aria-label="上一条开场白" ${'$'}{message.selectedOpeningIndex <= 0 ? 'disabled' : ''}></button><button class="pager-index" data-action="opening-jump" aria-label="第 ${'$'}{message.selectedOpeningIndex + 1} 条，共 ${'$'}{message.openingOptionIds.length} 条开场白，点击跳转">${'$'}{message.selectedOpeningIndex + 1}/${'$'}{message.openingOptionIds.length}</button><button data-action="opening-next" aria-label="下一条开场白" ${'$'}{message.selectedOpeningIndex >= message.openingOptionIds.length - 1 ? 'disabled' : ''}></button>`;
        lane.append(pager);
      }
      const main = document.createElement('section'); main.className = 'turn-main';
      const header = document.createElement('header'); header.className = 'turn-header' + (expanded ? ' toolbar-expanded' : '');
      header.innerHTML = `<div class="name">${'$'}{escapeHtml(message.name)}</div><div class="tools"><div class="tool-strip">${'$'}{toolbarContent(message, expanded)}</div></div>`;
      main.append(header, createBody(message)); turn.append(lane, main);
      applyCachedRichHeights(turn, message);
      restoreTurnSnapshot(turn);
      return turn;
    };
    const patchLiveStatus = (body, previousMessage, message) => {
      const previousStatus = previousMessage.liveStatus;
      const nextStatus = message.liveStatus;
      const status = body.querySelector(':scope > .live-status');
      if (!previousStatus || !nextStatus || !status) return false;
      status.classList.toggle('running', !!nextStatus.running);
      status.classList.toggle('thinking', !!nextStatus.thinking);
      status.setAttribute('aria-label', `查看处理过程：${'$'}{nextStatus.label}`);
      const sameIndicator = previousStatus.thinking === nextStatus.thinking && (
        nextStatus.thinking
          ? previousStatus.mascotStyle === nextStatus.mascotStyle
          : JSON.stringify(previousStatus.icon || null) === JSON.stringify(nextStatus.icon || null)
      );
      if (!sameIndicator) {
        const indicator = status.querySelector('.live-status-indicator');
        if (indicator) {
          indicator.innerHTML = nextStatus.thinking
            ? thinkingMascot(nextStatus)
            : transcriptVectorIcon(nextStatus.icon, 17);
        }
      } else {
        const indicator = status.querySelector('.thinking-mascot, .thinking-bars');
        if (indicator) indicator.classList.toggle('animated', !!nextStatus.running);
      }
      const label = status.querySelector('.live-status-label');
      if (label && label.textContent !== String(nextStatus.label || '')) {
        label.textContent = nextStatus.label || '';
      }
      return true;
    };
    const patchContentBody = (body, previousMessage, message) => {
      if (previousMessage.liveStatus || message.liveStatus) return false;
      const nativePart = body.querySelector(':scope > .native-part');
      if (!nativePart) return false;
      if (
        previousMessage.contentRevision !== message.contentRevision ||
        previousMessage.pending !== message.pending
      ) {
        reconcileContentParts(nativePart, message);
      }
      let reasoning = body.querySelector(':scope > details.reasoning');
      if (message.reasoning) {
        if (!reasoning) {
          reasoning = document.createElement('details');
          reasoning.className = 'reasoning';
          reasoning.innerHTML = `<summary>思考过程</summary><div class="reasoning-content"></div>`;
          nativePart.after(reasoning);
        }
        const content = reasoning.querySelector('.reasoning-content');
        if (content && content.textContent !== message.reasoning) content.textContent = message.reasoning;
      } else if (reasoning) {
        reasoning.remove();
      }
      return true;
    };
    const patchTurn = (existing, previousMessage, message) => {
      const pagerVisible = message.openingOptionIds && message.openingOptionIds.length > 1 && message.selectedOpeningIndex >= 0;
      existing.dataset.revision = String(message.revision || '');
      existing.classList.toggle('card', state.cardPanel);
      existing.classList.toggle('has-pager', !!pagerVisible);
      const name = existing.querySelector(':scope > .turn-main > .turn-header .name');
      if (name && name.textContent !== String(message.name || '')) name.textContent = message.name || '';
      if (
        previousMessage.avatarUrl !== message.avatarUrl
      ) {
        const avatar = existing.querySelector(':scope > .portrait-lane > .avatar');
        if (avatar) {
          if (message.avatarUrl) avatar.innerHTML = `<img src="${'$'}{escapeHtml(message.avatarUrl)}" alt="">`;
          else avatar.innerHTML = missingAvatar();
        }
      }
      if (
        previousMessage.pending !== message.pending ||
        previousMessage.role !== message.role ||
        previousMessage.hasAgentProcess !== message.hasAgentProcess ||
        previousMessage.regenerateEnabled !== message.regenerateEnabled
      ) {
        const strip = existing.querySelector(':scope > .turn-main > .turn-header .tool-strip');
        if (strip) strip.innerHTML = toolbarContent(message, state.expandedToolbarId === message.id);
      }
      const body = existing.querySelector(':scope > .turn-main > .message-body');
      if (!body) return existing;
      if (!patchLiveStatus(body, previousMessage, message) && !patchContentBody(body, previousMessage, message)) {
        const replacement = createBody(message);
        releaseRichWithin(body);
        body.replaceWith(replacement);
      }
      applyCachedRichHeights(existing, message);
      return existing;
    };
    const replaceOpeningTurn = (existing, message) => {
      const oldHeight = existing.getBoundingClientRect().height;
      const oldWidth = existing.getBoundingClientRect().width;
      const previousRichFrameHeights = richRootsWithin(existing).map(root =>
        Math.max(1, Math.ceil(
          root.querySelector(':scope > .eleckoi-rich-frame')?.getBoundingClientRect().height || 0,
        )),
      );
      captureTurnSnapshot(existing);
      const next = createTurn(message);
      next.dataset.index = existing.dataset.index || '';
      applyDeletePresentation(next, Number(next.dataset.index));
      const nextRichRoots = richRootsWithin(next);
      if (nextRichRoots.length) {
        nextRichRoots.forEach((root, rootIndex) => {
          const frame = root.querySelector(':scope > .eleckoi-rich-frame');
          const previousHeight = Number(previousRichFrameHeights[rootIndex] || 0);
          if (frame && frame.getBoundingClientRect().height <= 1 && previousHeight > 1) {
            frame.style.height = previousHeight + 'px';
          }
        });
        existing.after(next);
        releaseRichWithin(existing);
        existing.remove();
        nextRichRoots.forEach((root, rootIndex) => activateRichRootNow(root, message.id, rootIndex));
        virtualizer.measureElement(next);
        refreshRichViewport();
        requestGeometryCommit({ renderRange: true });
        return next;
      }
      next.style.position = 'absolute'; next.style.visibility = 'hidden'; next.style.width = oldWidth + 'px';
      existing.after(next);
      const nextHeight = next.getBoundingClientRect().height;
      next.style.position = ''; next.style.visibility = ''; next.style.width = '';
      next.style.height = oldHeight + 'px'; next.style.overflow = 'hidden'; next.style.opacity = '.92';
      releaseRichWithin(existing); existing.remove();
      virtualizer.measureElement(next);
      refreshRichViewport();
      const animation = next.animate(
        [{ height: oldHeight + 'px', opacity: .92 }, { height: nextHeight + 'px', opacity: 1 }],
        { duration: 180, easing: 'cubic-bezier(.2,0,0,1)', fill: 'forwards' },
      );
      let cleaned = false;
      let openingAnimationWatchdog = 0;
      const cleanup = () => {
        if (cleaned) return;
        cleaned = true;
        if (openingAnimationWatchdog) clearTimeout(openingAnimationWatchdog);
        animation.onfinish = null; animation.oncancel = null;
        next.style.height = ''; next.style.overflow = ''; next.style.opacity = '';
        animation.cancel();
        virtualizer.measureElement(next);
        requestGeometryCommit({ renderRange: true });
      };
      animation.onfinish = cleanup; animation.oncancel = cleanup;
      // WebView may suppress finish/cancel callbacks while a frame is replaced or backgrounded.
      // Never let the temporary fixed height become the permanent opening-message layout.
      openingAnimationWatchdog = setTimeout(cleanup, 400);
      return next;
    };
"""
