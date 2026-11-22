package com.cpaconceptlab.study.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpaconceptlab.study.AppContainer
import com.cpaconceptlab.study.BuildConfig
import com.cpaconceptlab.study.data.backup.BackupKind
import com.cpaconceptlab.study.data.backup.BackupManager
import com.cpaconceptlab.study.data.backup.RestoreMode
import com.cpaconceptlab.study.data.importer.ContentCoverageValidator
import com.cpaconceptlab.study.data.repository.ContentSnapshot
import com.cpaconceptlab.study.data.repository.UserSettings
import com.cpaconceptlab.study.data.repository.UserSnapshot
import com.cpaconceptlab.study.domain.usecase.ExamScopeStat
import com.cpaconceptlab.study.domain.usecase.Overview
import com.cpaconceptlab.study.domain.usecase.StatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DashboardState(
    val content: ContentSnapshot,
    val user: UserSnapshot,
    val overview: Overview,
    val examScopes: List<ExamScopeStat>,
    val now: Long,
)

sealed interface LoadState {
    data object Loading : LoadState
    data object Ready : LoadState
    data class Failed(val message: String) : LoadState
}

class MainViewModel(val container: AppContainer) : ViewModel() {
    private val contentRepo = container.contentRepository
    private val studyRepo = container.studyRepository
    private val settingsRepo = container.settingsRepository

    private val _load = MutableStateFlow<LoadState>(LoadState.Loading)
    val load: StateFlow<LoadState> = _load.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 날짜가 바뀌면(자정) 통계를 다시 계산하기 위한 시계 */
    private val clock = MutableStateFlow(System.currentTimeMillis())

    val settings: StateFlow<UserSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    val content: StateFlow<ContentSnapshot?> = contentRepo.snapshot

    val dashboard: StateFlow<DashboardState?> = combine(
        contentRepo.snapshot.filterNotNull(), studyRepo.userSnapshot, clock,
    ) { c, u, now ->
        val ov = StatsCalculator.overview(c, u, now)
        DashboardState(c, u, ov, StatsCalculator.examScopes(c, u, ov.masteryByConcept), now)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val contentVersions = contentRepo.versions.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { contentRepo.syncBundled() }
                _load.value = LoadState.Ready
            } catch (e: Exception) {
                _load.value = LoadState.Failed("콘텐츠를 불러오지 못했습니다: ${e.message}")
            }
        }
        viewModelScope.launch {
            while (isActive) { delay(60_000); clock.value = System.currentTimeMillis() }
        }
    }

    fun tick() { clock.value = System.currentTimeMillis() }

    fun consumeMessage() { _message.value = null }
    fun showMessage(m: String) { _message.value = m }

    fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    fun validationReport(): ContentCoverageValidator.Report? =
        content.value?.let { contentRepo.validationReport(it) }

    // ── 콘텐츠 가져오기 ──
    fun importContent(json: String, fileName: String) {
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { contentRepo.importUserPack(json, fileName) }
            _message.value = buildString {
                append(if (r.success) "가져오기 완료 — " else "가져오기 실패 — ")
                append(r.message)
                r.plan?.errors?.take(5)?.forEach { append("\n• ").append(it) }
                r.plan?.warnings?.take(3)?.forEach { append("\n• ").append(it) }
            }
        }
    }

    // ── 백업·복원 ──
    suspend fun exportBackup(kind: BackupKind): String = withContext(Dispatchers.IO) {
        container.backupManager.export(
            kind = kind,
            settings = settings.value,
            appVersionCode = BuildConfig.VERSION_CODE,
            contentVersion = content.value?.bundledVersion ?: 0,
            now = System.currentTimeMillis(),
        )
    }

    fun inspectBackup(json: String): Result<BackupManager.Inspection> = runCatching {
        val c = content.value ?: ContentSnapshot.EMPTY
        container.backupManager.inspect(
            json,
            knownConceptIds = c.allConcepts.map { it.id }.toSet(),
            knownQuestionIds = c.allQuestions.map { it.id }.toSet(),
            currentContentVersion = c.bundledVersion,
        )
    }

    fun restore(inspection: BackupManager.Inspection, mode: RestoreMode) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { container.backupManager.restore(inspection, mode) }
                val packMsgs = withContext(Dispatchers.IO) { contentRepo.reapplyPacks(result.importedPacks) }
                if (mode == RestoreMode.REPLACE && result.settings != null) settingsRepo.write(result.settings)
                _message.value = result.message + packMsgs.joinToString("") { "\n• $it" }
            } catch (e: Exception) {
                _message.value = "복원 중 오류가 발생하여 변경을 취소했습니다: ${e.message}"
            }
        }
    }

    // ── 학습 화면 공용 동작 ──
    fun markViewed(conceptId: String) {
        viewModelScope.launch { studyRepo.markConceptViewed(conceptId, System.currentTimeMillis()) }
    }

    fun toggleBookmark(conceptId: String) { viewModelScope.launch { studyRepo.toggleBookmark(conceptId) } }
    fun saveMemo(conceptId: String, memo: String) { viewModelScope.launch { studyRepo.saveMemo(conceptId, memo) } }

    val appVersion: String = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
}

val DashboardState.mastery get() = overview.masteryByConcept
fun StateFlow<DashboardState?>.mapOverview() = map { it?.overview }
