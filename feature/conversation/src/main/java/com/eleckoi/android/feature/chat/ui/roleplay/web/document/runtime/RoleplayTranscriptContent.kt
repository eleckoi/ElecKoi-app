package com.eleckoi.android.feature.chat.ui.roleplay.web.document.runtime

internal val RoleplayTranscriptContent = """    const applyImageGalleryGeometry = (gallery, message, images) => {
      const count = images.length;
      const reservedContentWidth = Math.max(1, Math.round(estimatedImageContentWidth(message)));
      gallery.style.width = reservedContentWidth + 'px';
      gallery.style.maxWidth = '100%';
      if (count === 1) {
        const frame = gallery.querySelector(':scope > .story-image-frame');
        const image = images[0];
        if (frame && image) {
          frame.style.width = Math.max(
            1,
            Math.round(reservedContentWidth * singleImageWidthFraction(image)),
          ) + 'px';
          frame.style.maxWidth = '100%';
          frame.style.flex = '0 0 auto';
        }
      }
      return reservedContentWidth;
    };
    const refreshImageGalleryGeometry = () => {
      turns.querySelectorAll(':scope > .turn').forEach(turn => {
        const message = state.byId.get(turn.dataset.id || '');
        if (!message) return;
        const imageParts = (message.parts || []).filter(part => part.type === 'images');
        turn.querySelectorAll('.story-images').forEach((gallery, index) => {
          const images = imageParts[index]?.images || [];
          if (images.length) applyImageGalleryGeometry(gallery, message, images);
        });
      });
    };
    const createImageGallery = (message, images) => {
      const gallery = document.createElement('div');
      gallery.className = 'content-part story-images';
      const count = images.length;
      if (count === 1) gallery.classList.add('single');
      else gallery.style.gridTemplateColumns = `repeat(${'$'}{count === 2 || count === 4 ? 2 : 3}, minmax(0, 1fr))`;
      images.forEach(image => {
        const frame = document.createElement('div');
        frame.className = 'story-image-frame';
        frame.dataset.imageId = image.id || '';
        frame.dataset.status = image.status || '';
        frame.style.aspectRatio = String(normalizedImageAspectRatio(image));
        if (image.status === 'ready' && image.url) {
          const element = document.createElement('img');
          element.className = 'story-image'; element.src = image.url; element.alt = '本轮剧情插图';
          element.draggable = false; frame.append(element);
        } else {
          const status = document.createElement('div'); status.className = 'image-state';
          status.textContent = image.status === 'failed' ? (image.error || '图片生成失败') : '图片生成中…';
          frame.append(status);
        }
        if (Number(image.frameCount) > 1) {
          const badge = document.createElement('span'); badge.className = 'image-frame-badge';
          badge.textContent = `${'$'}{image.frameIndex}/${'$'}{image.frameCount}`; frame.append(badge);
        }
        gallery.append(frame);
      });
      applyImageGalleryGeometry(gallery, message, images);
      return gallery;
    };
    const splitMarkdownBlocks = raw => {
      const lines = String(raw || '').replace(/\r\n?/g, '\n').split('\n');
      const blocks = [];
      const markupStack = [];
      const voidMarkupTags = new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr']);
      let buffer = [], fence = '', rich = false;
      const flush = () => {
        if (!buffer.length) return;
        const value = buffer.join('\n');
        if (value.trim()) blocks.push(value);
        buffer = [];
      };
      lines.forEach(line => {
        if (/<!--\s*eleckoi\s*:\s*rich-replacement\s*:\s*start\s*-->/i.test(line)) rich = true;
        const fenceMatch = line.match(/^[ \t]{0,3}(```+|~~~+)/);
        if (!rich && fenceMatch) {
          const marker = fenceMatch[1][0];
          if (!fence) fence = marker;
          else if (fence === marker) fence = '';
        }
        if (!fence && !rich) {
          const tagPattern = /<\s*(\/?)\s*([A-Za-z][\w:.-]*)\b[^>]*>/g;
          let tagMatch;
          while ((tagMatch = tagPattern.exec(line)) !== null) {
            const tagName = tagMatch[2].toLowerCase();
            const closing = !!tagMatch[1];
            const selfClosing = /\/\s*>${'$'}/.test(tagMatch[0]);
            if (selfClosing || voidMarkupTags.has(tagName)) continue;
            if (!closing) {
              markupStack.push(tagName);
              continue;
            }
            let openingIndex = markupStack.length - 1;
            while (openingIndex >= 0 && markupStack[openingIndex] !== tagName) openingIndex -= 1;
            if (openingIndex >= 0) markupStack.splice(openingIndex);
          }
        }
        buffer.push(line);
        const richBoundaryClosed = /<!--\s*eleckoi\s*:\s*rich-replacement\s*:\s*end\s*-->/i.test(line);
        if (richBoundaryClosed) {
          rich = false;
          // A completed rich replacement must become immutable immediately. Otherwise later
          // streaming text shares this block and remounts the already-running iframe per token.
          if (!fence && markupStack.length === 0) flush();
        }
        if (!fence && !rich && markupStack.length === 0 && !line.trim()) flush();
      });
      flush();
      return blocks;
    };
    const reconcileTextPart = (container, source, allowFrontend) => {
      const blocks = splitMarkdownBlocks(source);
      blocks.forEach((blockSource, index) => {
        let block = container.children[index];
        if (!block || !block.classList.contains('stream-block')) {
          const replacement = document.createElement('div');
          replacement.className = 'stream-block';
          if (block) container.insertBefore(replacement, block); else container.append(replacement);
          block = replacement;
        }
        if (
          block.__eleckoiMarkdownSource !== blockSource ||
          block.__eleckoiFrontendAllowed !== allowFrontend
        ) {
          releaseRichWithin(block);
          block.innerHTML = markdown(blockSource, allowFrontend);
          block.__eleckoiMarkdownSource = blockSource;
          block.__eleckoiFrontendAllowed = allowFrontend;
        }
      });
      while (container.children.length > blocks.length) {
        const stale = container.lastElementChild;
        releaseRichWithin(stale);
        stale.remove();
      }
    };
    const imagePartSignature = images => (images || []).map(image => [
      image.id, image.url, image.status, image.error, image.aspectRatio, image.frameIndex, image.frameCount,
    ].join('\u001f')).join('\u001e');
    const reconcileContentParts = (nativePart, message) => {
      const parts = message.parts || [];
      parts.forEach((part, index) => {
        let element = nativePart.children[index];
        const expectedClass = part.type === 'images' ? 'story-images' : 'content-text-part';
        if (!element || !element.classList.contains(expectedClass)) {
          const replacement = part.type === 'images'
            ? createImageGallery(message, part.images || [])
            : document.createElement('div');
          if (part.type === 'images') replacement.__eleckoiImageSignature = imagePartSignature(part.images);
          if (part.type !== 'images') replacement.className = 'content-part content-text-part';
          if (element) {
            releaseRichWithin(element);
            nativePart.insertBefore(replacement, element);
          } else {
            nativePart.append(replacement);
          }
          element = replacement;
        }
        if (part.type === 'text') {
          reconcileTextPart(
            element,
            part.markdown || '',
            state.frontendRendererEnabled && !message.pending,
          );
        } else {
          const signature = imagePartSignature(part.images);
          if (element.__eleckoiImageSignature !== signature) {
            const replacement = createImageGallery(message, part.images || []);
            replacement.__eleckoiImageSignature = signature;
            element.replaceWith(replacement);
          }
        }
      });
      while (nativePart.children.length > parts.length) {
        const stale = nativePart.lastElementChild;
        releaseRichWithin(stale);
        stale.remove();
      }
    };
    let imageMenuOpenedAt = 0;
    const closeImageMenu = () => {
      imageMenu.hidden = true;
      imageMenu.replaceChildren();
    };
    const findTranscriptImage = (message, imageId) => {
      for (const part of message?.parts || []) {
        if (part.type !== 'images') continue;
        const image = (part.images || []).find(candidate => candidate.id === imageId);
        if (image) return image;
      }
      return null;
    };
    const openImageMenu = (frame, clientX, clientY) => {
      const turn = frame?.closest('.turn');
      const message = turn ? state.byId.get(turn.dataset.id) : null;
      const image = findTranscriptImage(message, frame?.dataset.imageId || '');
      if (!message || !image || image.status === 'generating') return;
      const actions = [];
      if (image.status === 'ready' && image.url) actions.push(['download', '下载图片']);
      if (message.role !== 'user' && message.regenerateEnabled && !message.pending) {
        actions.push(['regenerate', '重新生成']);
      }
      if (!actions.length) return;
      imageMenu.replaceChildren();
      actions.forEach(([action, label]) => {
        const button = document.createElement('button');
        button.type = 'button'; button.textContent = label;
        button.addEventListener('click', () => {
          closeImageMenu();
          post({ type: 'imageAction', action, messageId: message.id, attachmentId: image.id });
        });
        imageMenu.append(button);
      });
      imageMenu.hidden = false;
      imageMenu.style.left = '8px'; imageMenu.style.top = '8px';
      imageMenuOpenedAt = performance.now();
      requestAnimationFrame(() => {
        if (imageMenu.hidden) return;
        const width = imageMenu.offsetWidth, height = imageMenu.offsetHeight;
        const left = Math.max(8, Math.min(clientX, window.innerWidth - width - 8));
        const preferredTop = clientY + 8;
        const top = preferredTop + height <= window.innerHeight - 8
          ? preferredTop
          : Math.max(8, clientY - height - 8);
        imageMenu.style.left = left + 'px'; imageMenu.style.top = top + 'px';
      });
    };
"""
