package com.eleckoi.android.feature.chat.data.rich

internal data class RichMessageCssTheme(
    val foreground: String,
    val muted: String,
    val accent: String,
    val fontSizePx: Float,
    val lineHeightPx: Float,
    val letterSpacingPx: Float,
    val dark: Boolean,
)

internal fun buildRichMessageHtml(
    document: RichMessageDocument,
    theme: RichMessageCssTheme,
    authorRuntimeHead: String,
): String {
    val head = richMessageHead(
        theme = theme,
        authorRuntimeHead = authorRuntimeHead,
    )
    val bootstrap = RichMessageBootstrap
    if (document.kind == RichMessageDocumentKind.Fragment) {
        return """<!doctype html>
<html>
<head>$head</head>
<body><main id="eleckoi-rich-root">${document.source}</main>$bootstrap</body>
</html>"""
    }

    var html = document.source
    html = when {
        HeadOpen.containsMatchIn(html) -> html.insertAfterFirst(HeadOpen, head)
        HtmlOpen.containsMatchIn(html) -> html.insertAfterFirst(HtmlOpen, "<head>$head</head>")
        else -> "<head>$head</head>$html"
    }
    html = if (BodyClose.containsMatchIn(html)) {
        html.insertBeforeLast(BodyClose, bootstrap)
    } else {
        "$html$bootstrap"
    }
    return html
}

private fun richMessageHead(
    theme: RichMessageCssTheme,
    authorRuntimeHead: String,
): String = """
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style id="eleckoi-host-style">
:root {
  color-scheme: ${if (theme.dark) "dark" else "light"};
  --eleckoi-foreground: ${theme.foreground};
  --eleckoi-muted: ${theme.muted};
  --eleckoi-accent: ${theme.accent};
}
html, body {
  width: 100%;
  min-width: 0;
  min-height: 0;
  margin: 0;
  padding: 0;
  overflow-x: hidden;
  background: transparent;
  color: var(--eleckoi-foreground);
  font-family: system-ui, -apple-system, sans-serif;
  font-size: ${theme.fontSizePx}px;
  line-height: ${theme.lineHeightPx}px;
  letter-spacing: ${theme.letterSpacingPx}px;
  overflow-wrap: anywhere;
}
*, *::before, *::after { box-sizing: border-box; }
#eleckoi-rich-root { width: 100%; min-width: 0; }
img, video, canvas, svg, iframe { max-width: 100%; }
a { color: var(--eleckoi-accent); }
button, input, textarea, select { font: inherit; }
</style>
$authorRuntimeHead
""".trimIndent()

private val HeadOpen = Regex("""(?i)<head(?:\s[^>]*)?>""")
private val BodyClose = Regex("""(?i)</body\s*>""")
private val HtmlOpen = Regex("""(?i)<html(?:\s[^>]*)?>""")

private fun String.insertBeforeFirst(pattern: Regex, addition: String): String {
    val match = pattern.find(this) ?: return this
    return substring(0, match.range.first) + addition + substring(match.range.first)
}

private fun String.insertBeforeLast(pattern: Regex, addition: String): String {
    val match = pattern.findAll(this).lastOrNull() ?: return this
    return substring(0, match.range.first) + addition + substring(match.range.first)
}

private fun String.insertAfterFirst(pattern: Regex, addition: String): String {
    val match = pattern.find(this) ?: return this
    val insertionPoint = match.range.last + 1
    return substring(0, insertionPoint) + addition + substring(insertionPoint)
}

private val RichMessageBootstrap = """
<script id="eleckoi-host-bootstrap">
(() => {
  const readHeight = () => {
    const body = document.body;
    if (!body) return 1;
    const bodyRect = body.getBoundingClientRect();
    let bottom = bodyRect.top;
    const contentRange = document.createRange();
    contentRange.selectNodeContents(body);
    bottom = Math.max(bottom, contentRange.getBoundingClientRect().bottom);
    for (const child of body.children) {
      if (child.id === 'eleckoi-host-bootstrap') continue;
      const rect = child.getBoundingClientRect();
      const style = getComputedStyle(child);
      const marginBottom = Number.parseFloat(style.marginBottom) || 0;
      bottom = Math.max(bottom, rect.bottom + marginBottom);
    }
    const style = getComputedStyle(body);
    const paddingBottom = Number.parseFloat(style.paddingBottom) || 0;
    return Math.max(1, Math.ceil(bottom - bodyRect.top + paddingBottom));
  };
  const postHeight = () => {
    const height = readHeight();
    if (window.ElecKoiRichHost) window.ElecKoiRichHost.postMessage(String(height));
  };
  let queued = false;
  const measure = () => {
    if (queued) return;
    queued = true;
    requestAnimationFrame(() => {
      queued = false;
      postHeight();
    });
  };
  window.__ElecKoiMeasure = measure;

  let readyRevision = 0;
  let readyReported = false;
  let readyRequestId = '';
  let pageLoaded = document.readyState === 'complete';
  let fontsReady = !document.fonts || !document.fonts.ready;
  const authorApiIdle = () => {
    const pendingCount = window.__ElecKoiAuthorPendingCount;
    return typeof pendingCount !== 'function' || pendingCount() === 0;
  };
  const scheduleReady = () => {
    const revision = ++readyRevision;
    if (readyReported || !pageLoaded || !fontsReady || !authorApiIdle()) return;
    requestAnimationFrame(() => requestAnimationFrame(() => {
      if (
        readyReported ||
        revision !== readyRevision ||
        !pageLoaded ||
        !fontsReady ||
        !authorApiIdle()
      ) return;
      readyReported = true;
      const height = readHeight();
      if (window.ElecKoiRichHost) {
        window.ElecKoiRichHost.postReady(String(readyRequestId) + '|' + String(height));
      }
    }));
  };
  window.__ElecKoiMeasureReady = (requestId) => {
    readyRequestId = String(requestId || '');
    readyReported = false;
    measure();
    scheduleReady();
  };
  const contentChanged = () => {
    measure();
    scheduleReady();
  };
  if (window.ResizeObserver) {
    new ResizeObserver(contentChanged).observe(document.documentElement);
  }
  new MutationObserver(contentChanged).observe(document.documentElement, {
    childList: true,
    subtree: true,
    attributes: true,
    characterData: true,
  });
  window.addEventListener('eleckoi:author-pending-change', scheduleReady);
  if (!pageLoaded) {
    window.addEventListener('load', () => {
      pageLoaded = true;
      contentChanged();
    }, { once: true });
  }
  if (document.fonts && document.fonts.ready) {
    document.fonts.ready.then(() => {
      fontsReady = true;
      contentChanged();
    });
  }
  contentChanged();
})();
</script>
""".trimIndent()
