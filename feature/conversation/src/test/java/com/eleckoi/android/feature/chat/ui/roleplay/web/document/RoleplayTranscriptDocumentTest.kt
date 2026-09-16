package com.eleckoi.android.feature.chat.ui.roleplay.web.document

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class RoleplayTranscriptDocumentTest {
    @Test
    fun inlineCodeUsesAQuietThemeTintInsteadOfTheDarkCodeBlockSurface() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(
            document.contains(
                "background: color-mix(in srgb, var(--inline-code-text) 10%, transparent)",
            ),
        )
        assertFalse(document.contains("var(--code-background) 72%, transparent"))
    }

    @Test
    fun lightRoleplayTextDoesNotInheritTheDarkWallpaperShadow() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("text-shadow: var(--roleplay-text-shadow);"))
        assertTrue(document.contains("style.dark ? '0 0 1px rgba(0,0,0,.3)' : 'none'"))
        assertFalse(document.contains("text-shadow: 0 0 2px rgba(0,0,0,.5);"))
    }

    @Test
    fun authorSdkIsEncodedInsteadOfInterpolatedIntoTheRendererScript() {
        val sdk = "const request = `author-${'$'}{Date.now()}`; // 中文"

        val document = buildRoleplayTranscriptDocument(sdk)

        assertFalse(document.contains("__ELECKOI_AUTHOR_SDK_BASE64__"))
        assertFalse(document.contains(sdk))
        assertTrue(
            document.contains(
                Base64.getEncoder().encodeToString(sdk.toByteArray(Charsets.UTF_8)),
            ),
        )
    }

    @Test
    fun documentContainsGeneratedImageLongPressActions() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("type: 'imageAction'"))
        assertTrue(document.contains("下载图片"))
        assertTrue(document.contains("重新生成"))
        assertTrue(document.contains("message.role !== 'user' && message.regenerateEnabled"))
        assertTrue(document.contains("contextmenu"))
    }

    @Test
    fun documentUsesOnePatchAndGeometryCoordinatorWithoutCorrectionTimers() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("const applyPatch = payload =>"))
        assertTrue(document.contains("const requestGeometryCommit ="))
        assertTrue(document.contains("reentered during"))
        assertTrue(document.contains("mutate('geometry-commit'"))
        assertTrue(document.contains("new virtualRuntime.Virtualizer"))
        assertTrue(document.contains("anchorTo: 'end'"))
        assertTrue(document.contains("followOnAppend: followOnAppend ? 'auto' : false"))
        assertFalse(document.contains("virtualizer.shouldAdjustScrollPositionOnItemSizeChange ="))
        assertTrue(document.contains("virtualizer.measureElement(turn)"))
        assertTrue(document.contains("projection does not match TanStack virtual range"))
        assertTrue(document.contains("measureTurnHeight"))
        assertTrue(document.contains("PerformanceObserver"))
        assertTrue(document.contains("overflow-anchor: none"))
        assertFalse(document.contains("MAX_NEARBY_MESSAGE_ROOTS"))
        assertFalse(document.contains("MAX_MOUNTED_MESSAGE_ROOTS"))
        assertFalse(document.contains("heightTree"))
        assertFalse(document.contains("middle-spacer"))
        assertFalse(document.contains("restoreAnchor"))
        assertFalse(document.contains("type: 'scrollAdjustment'"))
        assertFalse(document.contains("type: 'scrollDiagnostic'"))
        assertTrue(document.contains("id=\"content-tail-gap\""))
        assertTrue(document.contains("#content-tail-gap { flex: 0 0 10px; height: 10px; }"))
        assertFalse(document.contains("tailLocked"))
        assertFalse(document.contains("beginTailSettle"))
        assertFalse(document.contains("scheduleTailSettle"))
        assertFalse(document.contains("viewportResizeTimer"))
        assertFalse(document.contains("id=\"jump-bottom\""))
        assertFalse(document.contains("requestAnimationFrame(() => requestAnimationFrame"))
    }

    @Test
    fun documentKeepsMarkdownInsideCollapsibleHtmlBlocks() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("/asset/web-runtime/showdown-2.1.0.min.js"))
        assertTrue(document.contains("/asset/web-runtime/dompurify-3.3.2.min.js"))
        assertTrue(document.contains("/asset/web-runtime/tanstack-virtual-core-3.17.8.min.js"))
        assertTrue(document.contains("new window.showdown.Converter"))
        assertTrue(document.contains("window.DOMPurify.sanitize"))
        assertTrue(document.contains("const extractCollapsibleBlocks = input =>"))
        assertTrue(document.contains("tagPattern = /<\\/?details\\b[^>]*>/gi"))
        assertTrue(document.contains("content.className = 'collapsible-content'"))
        assertTrue(document.contains(".native-part details[open] > summary"))
        assertTrue(document.contains("markupStack.length === 0"))
        assertFalse(document.contains("toggleDetailsInViewport"))
        assertFalse(document.contains("event.preventDefault();\n        toggleDetailsInViewport"))
    }

    @Test
    fun roleplayWrappersAreNormalizedPerRenderedFragment() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("const normalizeRoleplayMarkup = root =>"))
        assertTrue(document.contains("element instanceof HTMLUnknownElement || element.localName.includes('-')"))
        assertTrue(document.contains("span.className = 'roleplay-wrapper'"))
        assertTrue(document.contains("normalizeRoleplayMarkup(template.content)"))
        assertTrue(document.contains(".roleplay-wrapper { white-space: pre-line; }"))
        assertFalse(document.contains("DOMPurify.addHook"))
        assertFalse(document.contains("MESSAGE_SANITIZE"))
        assertFalse(document.contains("document.createTreeWalker(node, NodeFilter.SHOW_TEXT)"))
    }

    @Test
    fun documentReconcilesOnlyTheChangingStreamingTail() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("__eleckoiMarkdownSource"))
        assertTrue(document.contains("const richBoundaryClosed ="))
        assertTrue(document.contains("if (!fence && markupStack.length === 0) flush();"))
        assertTrue(document.contains("previousMessage.contentRevision !== message.contentRevision"))
        assertTrue(document.contains("window.ElecKoiRichRuntime"))
        assertTrue(document.contains("slot.root.__eleckoiRuntime = scope.runtime"))
        assertTrue(document.contains("runtime: scope.runtime"))
        assertTrue(document.contains("Promise.resolve(result).catch"))
        assertTrue(document.contains("turns.replaceChildren()"))
        assertTrue(document.contains("AbortController"))
        assertTrue(document.contains("const richViewportCapacity = () =>"))
        assertTrue(document.contains("const measureRichWeight = root =>"))
        assertTrue(document.contains("requestIdleCallback(startOne"))
        assertFalse(document.contains("deadline.timeRemaining()"))
        assertFalse(document.contains("MAX_LIVE_RICH_ROOTS"))
        assertFalse(document.contains("maxActive:"))
        assertTrue(document.contains("const refreshRichViewport ="))
        assertTrue(document.contains("slot.phase = RichSlotPhase.QUEUED"))
        assertTrue(document.contains("script[data-eleckoi-runtime-source=\"true\"]"))
        assertTrue(document.contains("onChange: (_, sync)"))
        assertTrue(document.contains("state.scroll.virtualScrolling = !!sync"))
        assertTrue(
            document.contains(
                "const compensationCommit = !!sync && state.scroll.compensationCommitPending",
            ),
        )
        assertTrue(document.contains("urgent: compensationCommit"))
        assertTrue(document.contains("const commitGeometry = () =>"))
        assertTrue(document.contains("queueMicrotask(commitGeometry)"))
        assertTrue(document.contains("syncRichViewportToScroll()"))
        assertFalse(document.contains("addEventListener('scrollend'"))
        assertTrue(document.contains("const isAtPhysicalEnd = () => distanceFromEnd() <= 1"))
        assertFalse(document.contains("claimHistoryBrowsing('pointer-drag-to-history')"))
        assertTrue(document.contains("'history-gesture-scroll'"))
        assertTrue(document.contains("state.scroll.gestureDistance += deltaY"))
        assertTrue(
            document.contains(
                "if (Math.abs(state.scroll.gestureDistance) > 8 && " +
                    "!state.scroll.historyIntent)",
            ),
        )
        assertTrue(document.contains("claimHistoryBrowsing('pointer-drag')"))
        assertTrue(document.contains("const isAtUiEnd = () => followsTail()"))
        assertTrue(document.contains("state.atEnd ? 96 : 24"))
        assertTrue(document.contains("delta > 0 && isAtPhysicalEnd()"))
        assertTrue(document.contains("scrollTransitions: state.scroll.transitions.slice()"))
        assertFalse(document.contains("forEach(activateTurnScripts)"))
        assertFalse(document.contains("JSON.stringify(message.parts)"))
    }

    @Test
    fun paragraphSpacingSurvivesStreamingBlockSegmentation() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains(".native-part p { margin: 0 0 var(--paragraph-gap); }"))
        assertTrue(
            document.contains(
                ".native-part > .content-text-part > .stream-block:last-child > p:last-child { margin-bottom: 0; }",
            ),
        )
        assertFalse(document.contains(".native-part p:last-child { margin-bottom: 0; }"))
    }

    @Test
    fun openingPagerStaysDirectlyBelowTheAvatar() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("lane.append(pager)"))
        assertTrue(document.contains("refreshRichViewport();"))
        assertTrue(document.contains("const previousRichFrameHeights ="))
        assertTrue(document.contains("nextRichRoots.forEach((root, rootIndex) => activateRichRootNow"))
        assertTrue(document.contains("frame.style.height = previousHeight + 'px'"))
        assertTrue(document.contains("openingAnimationWatchdog = setTimeout(cleanup, 400)"))
        assertFalse(document.contains("height: 18px;\n      margin-top: auto;"))
    }

    @Test
    fun richReplacementSlotCannotBecomeAnIndentedMarkdownCodeBlock() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(
            document.contains(
                "(_, body) => '\\n<span data-eleckoi-rich-slot=\"' + " +
                    "(rich.push(body.trim()) - 1) + '\"></span>\\n'",
            ),
        )
    }

    @Test
    fun completeRichDocumentsAreIsolatedInAutoHeightFrames() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("const isStandaloneRichDocument = source =>"))
        assertTrue(document.contains("replacement.classList.add('eleckoi-rich-document')"))
        assertTrue(document.contains("template.dataset.eleckoiRichDocumentSource = 'true'"))
        assertTrue(document.contains("frame.className = 'eleckoi-rich-frame'"))
        assertTrue(document.contains("frame.setAttribute('allowtransparency', 'true')"))
        assertTrue(document.contains("const mountRichDocumentFrame = (slot, scope) =>"))
        assertTrue(document.contains("frame.srcdoc = injectEmbeddedAuthorBootstrap(source)"))
        assertTrue(document.contains("const probeEmbeddedDocument = () =>"))
        assertTrue(document.contains("embeddedLocation !== 'about:srcdoc'"))
        assertTrue(document.contains("embeddedDocument.readyState === 'loading'"))
        assertTrue(document.contains("loadWatchdog = setTimeout(probeEmbeddedDocument, 50)"))
        assertTrue(document.contains("embeddedDocument.documentElement.style.setProperty('color-scheme'"))
        assertTrue(document.contains("embeddedDocument.documentElement.style.setProperty('background-color', 'transparent'"))
        assertTrue(document.contains("resizeObserver.observe(embeddedDocument.body)"))
        assertTrue(document.contains("mutationObserver.observe(embeddedDocument.body"))
        assertTrue(document.contains(".eleckoi-rich-replacement.eleckoi-rich-document"))
        assertTrue(document.contains("const initialPresentationActive = () =>"))
        assertTrue(document.contains("const initialViewportTurns = () =>"))
        assertTrue(document.contains("presentation.required.add(root)"))
        assertTrue(document.contains("initial presentation did not settle"))
        assertTrue(document.contains("transactionId: state.committedTransactionId"))
        assertTrue(document.contains("sessionId: state.sessionId"))
        assertTrue(document.contains("Number(right.bootstrap) - Number(left.bootstrap)"))
        assertFalse(document.contains("const initialRichLayoutReady = () =>"))
        assertFalse(document.contains("post({ type: 'ready', degraded:"))
        assertFalse(document.contains("state.readyPosted && (rect.bottom < -overscan"))
        assertFalse(document.contains("runtimeResumeTimer"))
        assertTrue(document.contains("markRichLayoutReady(slot)"))
        assertTrue(document.contains("if (initialPresentationActive())"))
        assertTrue(document.contains("state.forceTail = true"))
        assertTrue(document.contains("let lockedHeight = Number.isFinite(cachedHeight)"))
        assertTrue(document.contains("if (lockedHeight > 0)"))
        assertTrue(document.contains("const commitFirstStableHeight = height =>"))
        assertTrue(document.contains("post({ type: 'richHeight', key: measuredKey, height: lockedHeight })"))
        assertTrue(document.contains("Promise.allSettled(blockers)"))
        assertTrue(document.contains("stableSamples >= 2 || settleAttempts >= 6"))
        assertTrue(document.contains("let provisionalHeight = 0"))
        assertTrue(document.contains("frame.style.height = provisionalHeight + 'px'"))
        assertTrue(document.contains("scheduleHeight();\n        settleEmbeddedResources(embeddedDocument)"))
        assertFalse(document.contains("frame.style.height = height + 'px'"))
        assertFalse(document.contains("rich-island-parking"))
        assertFalse(document.contains("parkRichWithin"))
        assertFalse(document.contains("state.richIslands"))
        assertTrue(document.contains("applyCachedRichHeights(turn, message)"))
        assertTrue(document.contains("Object.entries(payload.richHeights || {})"))
        assertTrue(document.contains("const preservedHeight = Math.max("))
        assertTrue(document.contains("frame.style.height = preservedHeight + 'px'"))
        assertFalse(document.contains("frame.style.height = '1px'"))
    }

    @Test
    fun richFrontendsUseIndependentSlotsAcrossRepeatedViewportProcessing() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("richSlotByRoot: new WeakMap()"))
        assertTrue(document.contains("const RichSlotPhase = Object.freeze"))
        assertTrue(document.contains("COLD: 'cold', QUEUED: 'queued', RUNNING: 'running'"))
        assertTrue(document.contains("SLEEPING: 'sleeping', FAILED: 'failed', DISPOSED: 'disposed'"))
        assertTrue(document.contains("new IntersectionObserver"))
        assertTrue(document.contains("rootMargin: '1000px 0px'"))
        assertTrue(document.contains("const slot = state.richViewport.pending.shift()"))
        assertTrue(document.contains("generation: slot.generation + 1"))
        assertTrue(document.contains("slot.scope === scope && !scope.closed"))
        assertTrue(document.contains("const failRichSlot = (slot, error, stage"))
        assertTrue(document.contains("state.metrics.richSlotFailures += 1"))
        assertTrue(document.contains("while (state.snapshots.size > 128)"))
    }

    @Test
    fun richRuntimeChunksKeepTheirDependencyOrder() {
        val document = buildRoleplayTranscriptDocument("")
        val markers = listOf(
            "const RichSlotPhase = Object.freeze",
            "const embeddedAuthorFrames = new WeakMap()",
            "const mountRichDocumentFrame = (slot, scope) =>",
            "const closeRichScope = (slot, reason, nextPhase) =>",
            "window.ElecKoiRichRuntime = Object.freeze",
            "let richVisibleObserver = null",
            "const refreshRichViewport = () =>",
            "const shutdownRichViewport = () =>",
        )

        val positions = markers.map(document::indexOf)
        assertTrue(positions.all { position -> position >= 0 })
        assertTrue(positions.zipWithNext().all { (left, right) -> left < right })
    }

    @Test
    fun completeRichDocumentsReceiveMessageScopedSdkWithoutOwningCardCompatibility() {
        val document = buildRoleplayTranscriptDocument("window.__sdkLoaded = true;")

        assertTrue(document.contains("window.parent?.__ElecKoiEmbeddedAuthorBridge"))
        assertTrue(document.contains("registerEmbeddedAuthorFrame(frame, slot, scope)"))
        assertTrue(document.contains("messageId: target.slot.messageId"))
        assertTrue(document.contains("nativeRequestId = 'embedded-author-'"))
        assertTrue(document.contains("deliverEmbeddedAuthorResponse = response =>"))
        assertTrue(document.contains("pending.target === target"))
        assertTrue(document.contains("(0, eval)(sdkSource)"))
        assertFalse(document.contains("const setOpeningFromTavernMessage"))
        assertFalse(document.contains("triggerSlash"))
        assertFalse(document.contains("window.parent.setChatMessages"))
    }

    @Test
    fun frontendCodeBlocksUseTheSameIsolatedRuntimePath() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("const isFrontendCodeBlock = source => isStandaloneRichDocument(source)"))
        assertTrue(document.contains("const promoteFrontendCodeBlocks = fragment =>"))
        assertTrue(document.contains("pre.replaceWith(createRichReplacement(source))"))
        assertTrue(document.contains("state.frontendRendererEnabled && !message.pending"))
        assertTrue(document.contains("block.__eleckoiFrontendAllowed !== allowFrontend"))
        assertTrue(document.contains("previousMessage.pending !== message.pending"))
        assertTrue(document.contains("const createFrontendCodeBlock = source =>"))
    }

    @Test
    fun deleteModeSelectsTheSuffixInTheExistingTranscript() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("const applyDeletePresentation = (turn, index) =>"))
        assertTrue(document.contains("index >= state.deleteFromIndex"))
        assertTrue(document.contains("turn.classList.toggle('delete-selected', selected)"))
        assertTrue(document.contains("post({ type: 'deleteSelect', messageId: message.id })"))
        assertTrue(document.contains("background: color-mix(in srgb, var(--accent) 7%, transparent)"))
        assertTrue(document.contains("padding-left: calc(var(--horizontal-padding) + 48px)"))
        assertTrue(document.contains("left: calc(var(--horizontal-padding) + 13px)"))
        assertTrue(document.contains("top: 19px"))
        assertTrue(document.contains("width: 24px"))
        assertTrue(document.contains("transform: translate(1px, 1px)"))
        assertTrue(document.contains("M9 16.17 5.53 12.7c-.39-.39-1.02-.39-1.41 0"))
        assertTrue(document.contains("center / 17px 17px no-repeat"))
    }

    @Test
    fun richReplacementInsideAnOuterCodeFenceCannotLeakItsPrivateSlot() {
        val document = buildRoleplayTranscriptDocument("")

        assertTrue(document.contains("const resolveEscapedRichSlots = (fragment, rich, allowFrontend) =>"))
        assertTrue(document.contains("data-eleckoi-rich-slot=[\"'](\\d+)[\"']"))
        assertTrue(document.contains("replacement.append(createRichReplacement"))
        assertTrue(document.contains("code.textContent = source.replace(tokenPattern"))
    }

}
