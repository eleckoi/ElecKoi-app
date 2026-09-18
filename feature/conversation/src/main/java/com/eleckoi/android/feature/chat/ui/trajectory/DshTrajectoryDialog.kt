package com.eleckoi.android.feature.chat.ui.trajectory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryPage
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryReadOptions
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecord
import com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRequest
import com.eleckoi.android.foundation.design.AppearanceTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun DshTrajectoryDialog(
    runtimeThreadId: String,
    isSending: Boolean,
    appearance: AppearanceTheme,
    load: suspend (DshTrajectoryReadOptions) -> DshTrajectoryPage,
    onDismiss: () -> Unit,
) {
    var snapshot by remember(runtimeThreadId) { mutableStateOf<DshTrajectoryPage?>(null) }
    var loading by remember(runtimeThreadId) { mutableStateOf(true) }
    var loadingOlder by remember(runtimeThreadId) { mutableStateOf(false) }
    var error by remember(runtimeThreadId) { mutableStateOf("") }
    var selectedRecordId by rememberSaveable(runtimeThreadId) { mutableStateOf("") }
    var selectedRequestSeq by rememberSaveable(runtimeThreadId) { mutableStateOf<Long?>(null) }
    var searchQuery by rememberSaveable(runtimeThreadId) { mutableStateOf("") }
    var actualDuration by rememberSaveable(runtimeThreadId) { mutableStateOf(false) }
    var collapsedTurns by remember(runtimeThreadId) { mutableStateOf(emptySet<String>()) }
    var callsCollapsed by rememberSaveable(runtimeThreadId) { mutableStateOf(false) }
    var detailTab by rememberSaveable(runtimeThreadId) {
        mutableStateOf(DshTrajectoryDetailTab.Summary)
    }
    // The mobile inspector replaces the ledger in the composition. Keep its scroll owner here so
    // closing an event detail restores the exact row and pixel offset instead of treating the
    // ledger as a fresh open and jumping to the latest event again.
    val ledgerState = rememberDshTrajectoryLedgerState(runtimeThreadId)
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        loading = true
        runCatching { load(DshTrajectoryReadOptions(limit = PageSize)) }
            .onSuccess { next ->
                snapshot = mergeLatest(snapshot, next)
                error = ""
            }
            .onFailure { throwable -> error = throwable.message ?: "轨迹读取失败" }
        loading = false
    }

    LaunchedEffect(runtimeThreadId, isSending) {
        do {
            refresh()
            if (!isSending) break
            delay(LiveRefreshIntervalMillis)
        } while (true)
    }

    val records = snapshot?.records.orEmpty()
    val selectedRecord = records.firstOrNull { it.id == selectedRecordId }
    val selectedRequest = records.asSequence()
        .flatMap { it.requests.asSequence() }
        .firstOrNull { it.seq == selectedRequestSeq }
    val selection = when {
        selectedRequest != null -> DshTrajectorySelection.Request(selectedRequest)
        selectedRecord != null -> DshTrajectorySelection.Record(selectedRecord)
        else -> null
    }
    val matchedRecords = records.filter { record -> record.matches(searchQuery) }
    val displayedRecords = if (callsCollapsed && searchQuery.isBlank()) {
        matchedRecords.filter { record ->
            record.kind != com.eleckoi.android.engine.agent.deepseek.trajectory.DshTrajectoryRecordKind.Tool
        }
    } else {
        matchedRecords
    }
    val turnGroups = groupTrajectoryByTurn(displayedRecords)
    val collapsibleTurns = turnGroups.map(DshTrajectoryTurnGroup::key).toSet()
    val allTurnsCollapsed = collapsibleTurns.isNotEmpty() && collapsibleTurns.all(collapsedTurns::contains)

    fun clearSelection() {
        selectedRecordId = ""
        selectedRequestSeq = null
    }

    fun selectRecord(record: DshTrajectoryRecord) {
        selectedRequestSeq = null
        selectedRecordId = record.id
        detailTab = DshTrajectoryDetailTab.Summary
    }

    fun selectRequest(request: DshTrajectoryRequest) {
        selectedRecordId = ""
        selectedRequestSeq = request.seq
        detailTab = DshTrajectoryDetailTab.Context
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = appearance.mobileBg) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                val wide = maxWidth >= WideLayoutThreshold
                BackHandler(enabled = selection != null && !wide) { clearSelection() }
                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                    DshTrajectoryTitleBar(
                        eventCount = snapshot?.takeIf { it.runtimeThreadId.isNotBlank() }?.totalRecords,
                        appearance = appearance,
                        onDismiss = onDismiss,
                    )
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = appearance.mobileLine.copy(alpha = 0.22f),
                    )
                    if (selection != null && !wide) {
                        DshTrajectoryInspector(
                            selection = selection,
                            tab = detailTab,
                            appearance = appearance,
                            compact = true,
                            onTabChange = { detailTab = it },
                            onClose = ::clearSelection,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        DshTrajectoryToolbar(
                            actualDuration = actualDuration,
                            allTurnsCollapsed = allTurnsCollapsed,
                            callsCollapsed = callsCollapsed,
                            searchQuery = searchQuery,
                            appearance = appearance,
                            onToggleDuration = { actualDuration = !actualDuration },
                            onToggleTurns = {
                                collapsedTurns = if (allTurnsCollapsed) emptySet() else collapsibleTurns
                            },
                            onToggleCalls = { callsCollapsed = !callsCollapsed },
                            onSearchQueryChange = { searchQuery = it },
                        )
                        DshTrajectoryOverview(
                            records = records,
                            selectedRecordId = selectedRecordId,
                            actualDuration = actualDuration,
                            appearance = appearance,
                            onSelect = { record ->
                                if (!wide) ledgerState.preserveViewportForInspector()
                                selectRecord(record)
                            },
                        )
                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = appearance.mobileLine.copy(alpha = 0.22f),
                        )
                        Row(Modifier.weight(1f)) {
                            Box(Modifier.weight(1f)) {
                                when {
                                    loading && snapshot == null -> DshTrajectoryState(
                                        text = "正在读取轨迹…",
                                        loading = true,
                                        appearance = appearance,
                                    )
                                    error.isNotBlank() && snapshot == null -> DshTrajectoryState(
                                        text = error,
                                        action = "重试",
                                        onAction = { scope.launch { refresh() } },
                                        appearance = appearance,
                                    )
                                    snapshot != null && snapshot?.runtimeThreadId.isNullOrBlank() ->
                                        DshTrajectoryState("这个对话还没有 DSH 轨迹", appearance)
                                    snapshot?.runtimeThreadId?.isNotBlank() == true && records.isEmpty() ->
                                        DshTrajectoryState("当前会话还没有可显示的事件", appearance)
                                    records.isNotEmpty() && displayedRecords.isEmpty() ->
                                        DshTrajectoryState("没有匹配的事件", appearance)
                                    else -> DshTrajectoryLedger(
                                        state = ledgerState,
                                        latestRecordId = displayedRecords.lastOrNull()?.id.orEmpty(),
                                        groups = turnGroups,
                                        collapsedTurns = collapsedTurns,
                                        selectedId = selection?.id().orEmpty(),
                                        hasMore = snapshot?.hasMore == true,
                                        loadingOlder = loadingOlder,
                                        error = error.takeIf { snapshot != null }.orEmpty(),
                                        appearance = appearance,
                                        onToggleTurn = { key ->
                                            collapsedTurns = if (key in collapsedTurns) {
                                                collapsedTurns - key
                                            } else {
                                                collapsedTurns + key
                                            }
                                        },
                                        onSelectRecord = { record ->
                                            if (!wide) ledgerState.preserveViewportForInspector()
                                            selectRecord(record)
                                        },
                                        onSelectRequest = { request ->
                                            if (!wide) ledgerState.preserveViewportForInspector()
                                            selectRequest(request)
                                        },
                                        onLoadOlder = {
                                            val current = snapshot
                                            if (
                                                current != null && current.hasMore &&
                                                current.beforeIndex != null && !loadingOlder
                                            ) {
                                                scope.launch {
                                                    loadingOlder = true
                                                    runCatching {
                                                        load(
                                                            DshTrajectoryReadOptions(
                                                                beforeIndex = current.beforeIndex,
                                                                limit = PageSize,
                                                            ),
                                                        )
                                                    }.onSuccess { older ->
                                                        snapshot = mergeOlder(snapshot, older)
                                                        error = ""
                                                    }.onFailure { throwable ->
                                                        error = throwable.message ?: "更早的轨迹读取失败"
                                                    }
                                                    loadingOlder = false
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                            if (selection != null && wide) {
                                VerticalDivider(
                                    thickness = 0.5.dp,
                                    color = appearance.mobileLine.copy(alpha = 0.22f),
                                )
                                DshTrajectoryInspector(
                                    selection = selection,
                                    tab = detailTab,
                                    appearance = appearance,
                                    compact = false,
                                    onTabChange = { detailTab = it },
                                    onClose = ::clearSelection,
                                    modifier = Modifier.width(InspectorWidth),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun mergeLatest(current: DshTrajectoryPage?, next: DshTrajectoryPage): DshTrajectoryPage {
    if (current == null || current.runtimeThreadId != next.runtimeThreadId) return next
    val records = mergeRecords(current.records, next.records)
    return next.copy(
        records = records,
        hasMore = records.size < next.totalRecords,
        beforeIndex = records.firstOrNull()?.index,
    )
}

private fun mergeOlder(current: DshTrajectoryPage?, older: DshTrajectoryPage): DshTrajectoryPage {
    if (current == null || current.runtimeThreadId != older.runtimeThreadId) return older
    val records = mergeRecords(older.records, current.records)
    return current.copy(
        records = records,
        hasMore = older.hasMore,
        beforeIndex = records.firstOrNull()?.index,
    )
}

private fun mergeRecords(
    first: List<DshTrajectoryRecord>,
    second: List<DshTrajectoryRecord>,
): List<DshTrajectoryRecord> = (first + second)
    .associateBy(DshTrajectoryRecord::id)
    .values
    .sortedBy(DshTrajectoryRecord::index)

private const val PageSize = 400
private const val LiveRefreshIntervalMillis = 900L
private val WideLayoutThreshold = 840.dp
private val InspectorWidth = 380.dp
