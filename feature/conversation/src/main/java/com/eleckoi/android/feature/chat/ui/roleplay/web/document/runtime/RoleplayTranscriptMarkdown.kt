package com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime

internal val RoleplayTranscriptMarkdown = """    const markdownConverter = new window.showdown.Converter({
      emoji: true,
      literalMidWordUnderscores: true,
      parseImgDimensions: true,
      tables: true,
      underline: true,
      simpleLineBreaks: true,
      strikethrough: true,
      disableForced4SpacesIndentedSublists: true,
    });
    const isRoleplayWrapper = element =>
      element instanceof HTMLUnknownElement || element.localName.includes('-');
    const normalizeRoleplayMarkup = root => {
      Array.from(root.querySelectorAll('*'))
        .filter(isRoleplayWrapper)
        .reverse()
        .forEach(element => {
          const span = document.createElement('span');
          span.className = 'roleplay-wrapper';
          span.innerHTML = element.innerHTML.trim();
          element.replaceWith(span);
        });
    };
    const sanitizeMarkdown = html => {
      const template = document.createElement('template');
      template.innerHTML = html;
      normalizeRoleplayMarkup(template.content);
      return window.DOMPurify.sanitize(template.innerHTML, markdownSanitizerConfig);
    };
    const markdownSanitizerConfig = Object.freeze({
      RETURN_DOM: false,
      RETURN_DOM_FRAGMENT: true,
      RETURN_TRUSTED_TYPE: false,
      ALLOW_DATA_ATTR: true,
      ADD_TAGS: ['details', 'summary'],
      ADD_ATTR: ['open', 'data-eleckoi-details-slot', 'data-eleckoi-rich-slot'],
      FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'base', 'form', 'input', 'button', 'textarea', 'select', 'option'],
    });
    const extractCollapsibleBlocks = input => {
      const ranges = [];
      const tagPattern = /<\/?details\b[^>]*>/gi;
      let depth = 0;
      let blockStart = -1;
      let match;
      while ((match = tagPattern.exec(input)) !== null) {
        const closing = /^<\s*\//.test(match[0]);
        if (!closing) {
          if (depth === 0) blockStart = match.index;
          depth += 1;
        } else if (depth > 0) {
          depth -= 1;
          if (depth === 0 && blockStart >= 0) {
            ranges.push({ start: blockStart, end: tagPattern.lastIndex });
            blockStart = -1;
          }
        }
      }
      const blocks = [];
      let source = input;
      for (let rangeIndex = ranges.length - 1; rangeIndex >= 0; rangeIndex--) {
        const range = ranges[rangeIndex];
        const rawBlock = input.slice(range.start, range.end);
        const opening = rawBlock.match(/^<details\b([^>]*)>/i);
        if (!opening) continue;
        const inner = rawBlock
          .slice(opening[0].length)
          .replace(/<\/details\s*>\s*$/i, '');
        const summary = inner.match(/^\s*<summary\b([^>]*)>([\s\S]*?)<\/summary\s*>/i);
        if (!summary) continue;
        const index = blocks.push({
          detailsAttributes: opening[1] || '',
          summaryAttributes: summary[1] || '',
          summary: summary[2] || '',
          body: inner.slice(summary[0].length),
        }) - 1;
        source = source.slice(0, range.start) +
          '<span data-eleckoi-details-slot="' + index + '"></span>' + source.slice(range.end);
      }
      return { source, blocks };
    };
    const replaceStandaloneSlot = (slot, replacement) => {
      const parent = slot.parentElement;
      const siblings = parent
        ? Array.from(parent.childNodes).filter(node => node !== slot && (node.nodeType !== Node.TEXT_NODE || node.textContent.trim()))
        : [];
      if (parent?.tagName === 'P' && siblings.length === 0) parent.replaceWith(replacement);
      else slot.replaceWith(replacement);
    };
    const decorateInlineQuotes = root => {
      const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
      const nodes = [];
      while (walker.nextNode()) {
        const node = walker.currentNode;
        if (!node.parentElement?.closest('pre, code, script, style, textarea')) nodes.push(node);
      }
      nodes.forEach(node => {
        const text = node.textContent || '';
        const parts = text.split(/(“[^”\n]+”|"[^"\n]+")/g);
        if (parts.length < 3) return;
        const replacement = document.createDocumentFragment();
        parts.forEach((part, index) => {
          if (!part) return;
          if (index % 2 === 1) {
            const quote = document.createElement('span');
            quote.className = 'inline-quote'; quote.textContent = part; replacement.append(quote);
          } else replacement.append(document.createTextNode(part));
        });
        node.replaceWith(replacement);
      });
    };
    const enhanceMarkdownFragment = root => {
      root.querySelectorAll('img').forEach(image => {
        const replacement = document.createElement('span');
        replacement.textContent = '[' + (image.getAttribute('alt') || '') + ']';
        image.replaceWith(replacement);
      });
      root.querySelectorAll('pre').forEach(pre => {
        const code = pre.querySelector(':scope > code');
        const languageClass = code
          ? Array.from(code.classList).find(value => value.startsWith('language-'))
          : '';
        const language = languageClass ? languageClass.slice('language-'.length) : '代码';
        pre.classList.toggle('code-workbench', state.style.codeStyle === 'workbench');
        pre.classList.toggle('wrap', !!state.style.codeWrap);
        pre.classList.toggle('show-all', !!state.style.codeShowAll);
        if (state.style.codeStyle === 'workbench' && code) {
          const header = document.createElement('span');
          header.className = 'code-header'; header.textContent = language || '代码';
          pre.insertBefore(header, code);
        }
      });
      decorateInlineQuotes(root);
    };
    const renderInlineMarkdown = raw => {
      const fragment = sanitizeMarkdown(markdownConverter.makeHtml(String(raw || '').trim()));
      enhanceMarkdownFragment(fragment);
      const children = Array.from(fragment.childNodes).filter(node => node.nodeType !== Node.TEXT_NODE || node.textContent.trim());
      if (children.length === 1 && children[0].nodeName === 'P') return children[0].innerHTML;
      const container = document.createElement('span'); container.append(fragment); return container.innerHTML;
    };
    const isStandaloneRichDocument = source =>
      /(?:<!doctype\s+html\b|<(?:html|head|body)(?:\s|>))/i.test(String(source || ''));
    const populateRichReplacement = (replacement, source) => {
      if (!isStandaloneRichDocument(source)) {
        replacement.innerHTML = source;
        normalizeRoleplayMarkup(replacement);
        return;
      }
      // Complete frontend documents run in an iframe. Besides preserving that
      // behavior, the document boundary prevents authored html/body/:root CSS from changing the
      // transcript's typography and colors.
      replacement.classList.add('eleckoi-rich-document');
      const template = document.createElement('template');
      template.dataset.eleckoiRichDocumentSource = 'true';
      template.content.append(document.createTextNode(source));
      const frame = document.createElement('iframe');
      frame.className = 'eleckoi-rich-frame';
      frame.title = '消息内嵌界面';
      frame.setAttribute('allowtransparency', 'true');
      replacement.append(template, frame);
    };
    const createFrontendCodeBlock = source => {
      const pre = document.createElement('pre');
      const code = document.createElement('code');
      code.className = 'language-html';
      code.textContent = String(source || '').trim();
      pre.append(code);
      return pre;
    };
    const createRichReplacement = source => {
      const replacement = document.createElement('div');
      replacement.className = 'eleckoi-rich-replacement';
      populateRichReplacement(replacement, source);
      return replacement;
    };
    const isFrontendCodeBlock = source => isStandaloneRichDocument(source);
    const resolveEscapedRichSlots = (fragment, rich, allowFrontend) => {
      const tokenPattern = /<span\s+data-eleckoi-rich-slot=["'](\d+)["']\s*>\s*<\/span>/gi;
      fragment.querySelectorAll('pre').forEach(pre => {
        const code = pre.querySelector(':scope > code');
        if (!code) return;
        const source = code.textContent || '';
        tokenPattern.lastIndex = 0;
        if (!tokenPattern.test(source)) return;
        tokenPattern.lastIndex = 0;
        if (!allowFrontend) {
          code.textContent = source.replace(tokenPattern, (_, index) => rich[Number(index)] || '');
          return;
        }
        const replacement = document.createDocumentFragment();
        let cursor = 0;
        let match;
        while ((match = tokenPattern.exec(source)) !== null) {
          const before = source.slice(cursor, match.index);
          if (before.trim()) {
            const segment = document.createElement('pre');
            const segmentCode = document.createElement('code');
            segmentCode.className = code.className;
            segmentCode.textContent = before;
            segment.append(segmentCode);
            replacement.append(segment);
          }
          replacement.append(createRichReplacement(rich[Number(match[1])] || ''));
          cursor = tokenPattern.lastIndex;
        }
        const after = source.slice(cursor);
        if (after.trim()) {
          const segment = document.createElement('pre');
          const segmentCode = document.createElement('code');
          segmentCode.className = code.className;
          segmentCode.textContent = after;
          segment.append(segmentCode);
          replacement.append(segment);
        }
        pre.replaceWith(replacement);
      });
    };
    const resolveRichSlots = (fragment, rich, allowFrontend) => {
      fragment.querySelectorAll('[data-eleckoi-rich-slot]').forEach(slot => {
        const source = rich[Number(slot.getAttribute('data-eleckoi-rich-slot'))] || '';
        replaceStandaloneSlot(
          slot,
          allowFrontend ? createRichReplacement(source) : createFrontendCodeBlock(source),
        );
      });
      resolveEscapedRichSlots(fragment, rich, allowFrontend);
    };
    const promoteFrontendCodeBlocks = fragment => {
      fragment.querySelectorAll('pre').forEach(pre => {
        const code = pre.querySelector(':scope > code');
        const source = code?.textContent || '';
        if (!isFrontendCodeBlock(source)) return;
        pre.replaceWith(createRichReplacement(source));
      });
    };
    const markdown = (raw, allowFrontend = state.frontendRendererEnabled) => {
      let source = String(raw ?? '').replace(/\r\n?/g, '\n');
      const rich = [];
      source = source.replace(
        /<!--\s*eleckoi\s*:\s*rich-replacement\s*:\s*start\s*-->([\s\S]*?)<!--\s*eleckoi\s*:\s*rich-replacement\s*:\s*end\s*-->/gi,
        (_, body) => '\n<span data-eleckoi-rich-slot="' + (rich.push(body.trim()) - 1) + '"></span>\n',
      );
      const collapsibles = extractCollapsibleBlocks(source);
      const fragment = sanitizeMarkdown(markdownConverter.makeHtml(collapsibles.source));
      fragment.querySelectorAll('[data-eleckoi-details-slot]').forEach(slot => {
        const block = collapsibles.blocks[Number(slot.getAttribute('data-eleckoi-details-slot'))];
        if (!block) { slot.remove(); return; }
        const details = document.createElement('details');
        if (/(?:^|\s)open(?:\s*=\s*(?:"[^"]*"|'[^']*'|[^\s>]+))?(?=\s|${'$'})/i.test(block.detailsAttributes || '')) {
          details.open = true;
        }
        const summary = document.createElement('summary');
        summary.innerHTML = renderInlineMarkdown(block.summary || '详细内容');
        const content = document.createElement('div'); content.className = 'collapsible-content';
        content.innerHTML = markdown(String(block.body || '').trim(), allowFrontend);
        details.append(summary, content);
        replaceStandaloneSlot(slot, details);
      });
      resolveRichSlots(fragment, rich, allowFrontend);
      if (allowFrontend) promoteFrontendCodeBlocks(fragment);
      enhanceMarkdownFragment(fragment);
      const container = document.createElement('div'); container.append(fragment); return container.innerHTML;
    };
    const svgIcon = (name, size, strokeWidth = 1.85) => {
      const icon = state.icons[name]; if (!icon) return '';
      const viewport = icon.viewport || 24;
      const paths = (icon.paths || []).map(path => `<path d="${'$'}{escapeHtml(path)}"/>`).join('');
      if (icon.filled) return `<svg class="icon" width="${'$'}{size}" height="${'$'}{size}" viewBox="0 0 ${'$'}{viewport} ${'$'}{viewport}" aria-hidden="true" fill="currentColor">${'$'}{paths}</svg>`;
      return `<svg class="icon" width="${'$'}{size}" height="${'$'}{size}" viewBox="0 0 ${'$'}{viewport} ${'$'}{viewport}" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="${'$'}{strokeWidth}" stroke-linecap="round" stroke-linejoin="round">${'$'}{paths}</svg>`;
    };
    const filledImageIcon = (name, size) => {
      const icon = state.icons[name]; if (!icon) return '';
      const color = safeCss(state.style.muted || '#b8b8b2');
      const paths = (icon.paths || []).map(path => `<path fill="${'$'}{color}" d="${'$'}{escapeHtml(path)}"/>`).join('');
      const source = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${'$'}{icon.viewport || 24} ${'$'}{icon.viewport || 24}">${'$'}{paths}</svg>`;
      return `<img class="filled-icon" width="${'$'}{size}" height="${'$'}{size}" alt="" src="data:image/svg+xml;charset=utf-8,${'$'}{encodeURIComponent(source)}">`;
    };
    const transcriptVectorIcon = (icon, size) => {
      if (!icon || !Array.isArray(icon.paths)) return '';
      const viewportWidth = Number(icon.viewportWidth) || 24;
      const viewportHeight = Number(icon.viewportHeight) || 24;
      const paths = icon.paths.map(path => {
        const fill = path.fill ? 'currentColor' : 'none';
        const stroke = path.stroke ? 'currentColor' : 'none';
        const fillOpacity = Number.isFinite(Number(path.fillAlpha)) ? Number(path.fillAlpha) : 1;
        const strokeOpacity = Number.isFinite(Number(path.strokeAlpha)) ? Number(path.strokeAlpha) : 1;
        const strokeWidth = Number(path.strokeWidth) || 0;
        return `<path d="${'$'}{escapeHtml(path.data || '')}" fill="${'$'}{fill}" fill-opacity="${'$'}{fillOpacity}" stroke="${'$'}{stroke}" stroke-opacity="${'$'}{strokeOpacity}" stroke-width="${'$'}{strokeWidth}" stroke-linecap="round" stroke-linejoin="round"/>`;
      }).join('');
      const content = icon.mirrorX
        ? `<g transform="translate(${'$'}{viewportWidth} 0) scale(-1 1)">${'$'}{paths}</g>`
        : paths;
      return `<svg class="live-status-operation-icon" width="${'$'}{size}" height="${'$'}{size}" viewBox="0 0 ${'$'}{viewportWidth} ${'$'}{viewportHeight}" aria-hidden="true">${'$'}{content}</svg>`;
    };
    const thinkingMascot = status => {
      if (String(status.mascotStyle || '') === 'bars') {
        return `<span class="thinking-bars${'$'}{status.running ? ' animated' : ''}" aria-hidden="true"><span></span><span></span><span></span></span>`;
      }
      const head = String(status.mascotStyle || '').includes('bighead');
      const stem = head ? 'whale-maid-thinking-head' : 'whale-maid-thinking';
      const clock = performance.now();
      const animated = status.running ? ' animated' : '';
      const timing = `--blink-delay:-${'$'}{clock % 2700}ms;--tilt-delay:-${'$'}{clock % 2100}ms;--bulb-delay:-${'$'}{clock % 1120}ms;--spark-delay:-${'$'}{clock % 840}ms`;
      return `<span class="thinking-mascot${'$'}{animated}" style="${'$'}{timing}" aria-hidden="true">` +
        `<img class="mascot-sprite mascot-open" src="/asset/${'$'}{stem}.png" alt="">` +
        `<img class="mascot-sprite mascot-half" src="/asset/${'$'}{stem}-half.png" alt="">` +
        `<img class="mascot-sprite mascot-closed" src="/asset/${'$'}{stem}-closed.png" alt="">` +
        `<svg class="idea-bulb" viewBox="0 0 10 10">` +
        `<rect class="bulb-ray" x="1" y="2" width="1" height="1" fill="#FFD65A"/>` +
        `<rect class="bulb-core-ray" x="4.5" y="0" width="1" height="1" fill="#FFF2A6"/>` +
        `<rect class="bulb-ray" x="8" y="2" width="1" height="1" fill="#FFD65A"/>` +
        `<rect x="3" y="2" width="4" height="1" fill="#FFD65A"/>` +
        `<rect x="2" y="3" width="6" height="3" fill="#FFD65A"/>` +
        `<rect x="3" y="3" width="3" height="2" fill="#FFF2A6"/>` +
        `<rect x="3" y="6" width="4" height="1" fill="#E39A32"/>` +
        `<rect x="4" y="7" width="2" height="1" fill="#E39A32" fill-opacity=".86"/>` +
        `</svg></span>`;
    };
    const overflowIcon = () => '<svg class="icon overflow-icon" viewBox="0 0 24 24" aria-hidden="true"><circle cx="5" cy="12" r="2.25" fill="currentColor"/><circle cx="12" cy="12" r="2.25" fill="currentColor"/><circle cx="19" cy="12" r="2.25" fill="currentColor"/></svg>';
    const toolButton = (action, label, icon, size = 19, disabled = false, filled = false) => `<button class="tool-slot" data-action="${'$'}{action}" aria-label="${'$'}{label}" ${'$'}{disabled ? 'disabled' : ''}>${'$'}{filled ? filledImageIcon(icon, size) : svgIcon(icon, size, 1.85)}</button>`;
    const toolbarContent = (message, expanded) => {
      if (message.pending) return '';
      const edit = toolButton('edit', '编辑消息', 'edit', 18, false, true);
      if (!expanded) return `<button class="tool-slot" data-action="menu" aria-label="展开消息工具栏">${'$'}{overflowIcon()}</button>${'$'}{edit}`;
      let actions = '';
      if (message.role !== 'user') {
        if (message.hasAgentProcess) actions += toolButton('history', '查看过程', 'history');
        actions += toolButton('translate', '翻译', 'translate');
        actions += toolButton('speaker', '朗读', 'speaker');
        actions += toolButton('regenerate', '重新生成', 'refresh', 19, !message.regenerateEnabled);
      }
      actions += toolButton('copy', '复制', 'copy');
      return actions + edit;
    };
    const agentActionButton = (action, label, icon, disabled = false) =>
      `<button class="agent-action" data-action="${'$'}{action}" aria-label="${'$'}{label}" ${'$'}{disabled ? 'disabled' : ''}>${'$'}{svgIcon(icon, 20, 1.85)}</button>`;
    const agentOpeningPager = message => {
      const count = Array.isArray(message.openingOptionIds) ? message.openingOptionIds.length : 0;
      const index = Number(message.selectedOpeningIndex);
      if (count <= 1 || index < 0) return '';
      return `<div class="agent-opening-pager" aria-label="开场白 ${'$'}{index + 1}/${'$'}{count}">` +
        `<button class="agent-pager-action" data-action="opening-prev" aria-label="上一条开场白" ${'$'}{index <= 0 ? 'disabled' : ''}>${'$'}{svgIcon('chevronLeft', 20, 1.85)}</button>` +
        `<button class="agent-pager-index" data-action="opening-jump" aria-label="第 ${'$'}{index + 1} 条，共 ${'$'}{count} 条开场白，点击跳转">${'$'}{index + 1}/${'$'}{count}</button>` +
        `<button class="agent-pager-action" data-action="opening-next" aria-label="下一条开场白" ${'$'}{index >= count - 1 ? 'disabled' : ''}>${'$'}{svgIcon('chevronRight', 20, 1.85)}</button>` +
        `</div>`;
    };
    const agentFooterContent = message => {
      if (message.pending || message.role !== 'assistant') return '';
      let leading = state.layoutMode === 'agent' ? agentOpeningPager(message) : '';
      leading += agentActionButton('copy', '复制', 'copy');
      if (message.hasAgentProcess) leading += agentActionButton('history', '查看过程', 'history');
      leading += agentActionButton('speaker', '朗读', 'speaker');
      leading += agentActionButton('edit', '编辑消息', 'edit');
      return `<div class="agent-footer-leading">${'$'}{leading}</div>` +
        agentActionButton('regenerate', '重新生成', 'refresh', !message.regenerateEnabled);
    };
"""
