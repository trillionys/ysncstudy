package com.cpaconceptlab.study.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cpaconceptlab.study.AppContainer
import com.cpaconceptlab.study.data.local.QuestionEntity
import com.cpaconceptlab.study.data.repository.ContentSnapshot
import com.cpaconceptlab.study.data.repository.UserSnapshot
import com.cpaconceptlab.study.domain.AnswerGrader
import com.cpaconceptlab.study.domain.model.MasteryLevel
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.domain.model.QuestionKind
import com.cpaconceptlab.study.data.model.INPUT_CHOICE
import com.cpaconceptlab.study.data.model.INPUT_SLOTS
import com.cpaconceptlab.study.data.model.INPUT_TEXT
import com.cpaconceptlab.study.domain.usecase.QuestionSelector
import com.cpaconceptlab.study.domain.usecase.ReviewScheduler
import com.cpaconceptlab.study.domain.usecase.StatsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnswerResult(val isCorrect: Boolean, val userAnswer: String, val skipped: Boolean)

data class QuizUiState(
    val loading: Boolean = true,
    val mode: QuizMode = QuizMode.BASIC,
    val scope: QuizScope = QuizScope(ScopeType.ALL),
    val questions: List<QuestionEntity> = emptyList(),
    val index: Int = 0,
    val input: String = "",
    /** 4지선다에서 고른 보기 */
    val selectedChoice: String? = null,
    /** 슬롯형에서 칸별로 고른 보기 */
    val slotPicks: List<String?> = emptyList(),
    val result: AnswerResult? = null,
    val correctCount: Int = 0,
    val answeredCount: Int = 0,
    val finished: Boolean = false,
    val history: List<Pair<QuestionEntity, AnswerResult>> = emptyList(),
    val emptyReason: String? = null,
) {
    val current: QuestionEntity? get() = questions.getOrNull(index)
    val accuracy: Double? get() = if (answeredCount == 0) null else correctCount.toDouble() / answeredCount
    /** 기본 모드 세션을 80% 이상 맞히면 같은 범위의 심화 모드로 넘어가도록 제안한다. */
    val suggestAdvanced: Boolean get() = finished && mode == QuizMode.BASIC && answeredCount >= 5 && (accuracy ?: 0.0) >= 0.8
    val canSubmit: Boolean get() = when (current?.inputType) {
        INPUT_CHOICE -> selectedChoice != null
        INPUT_SLOTS -> slotPicks.size == current?.slotLabels?.size && slotPicks.none { it == null }
        else -> input.isNotBlank()
    }
}

class QuizViewModel(
    private val container: AppContainer,
    mode: QuizMode,
    scope: QuizScope,
    private val size: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(QuizUiState(mode = mode, scope = scope))
    val state: StateFlow<QuizUiState> = _state.asStateFlow()
    private var questionShownAt = System.currentTimeMillis()
    private lateinit var content: ContentSnapshot

    init {
        viewModelScope.launch {
            content = container.contentRepository.snapshot.filterNotNull().first()
            val now = System.currentTimeMillis()
            val picked = withContext(Dispatchers.Default) {
                val user = container.studyRepository.currentSnapshot()
                val candidates = candidatesFor(scope, content, user, now)
                val quota = if (scope.type == ScopeType.WEIGHTED) weightedQuota(content, scope.id) else emptyMap()
                QuestionSelector().select(
                    candidates = candidates,
                    mode = mode,
                    scopeType = if (scope.type == ScopeType.QUESTION) ScopeType.MOCK else scope.type,
                    size = if (scope.type == ScopeType.QUESTION) 1 else if (quota.isNotEmpty()) quota.values.sum() else size,
                    progress = user.progressMap,
                    wrongNotes = user.wrongNotes,
                    now = now,
                    recentlyAnswered = container.studyRepository.recentlyAnswered(10 * 60_000, now),
                    areaQuota = quota,
                )
            }
            if (scope.type != ScopeType.QUESTION) {
                container.settingsRepository.update { it.copy(lastScope = scope.encode(), lastMode = mode.name) }
            }
            questionShownAt = System.currentTimeMillis()
            _state.update {
                it.copy(
                    loading = false,
                    questions = picked,
                    emptyReason = if (picked.isEmpty()) emptyMessage(scope.type) else null,
                )
            }
        }
    }

    fun onInput(v: String) = _state.update { if (it.result == null) it.copy(input = v) else it }

    fun onChoice(v: String) = _state.update { if (it.result == null) it.copy(selectedChoice = v) else it }

    fun onSlot(index: Int, v: String) = _state.update { st ->
        if (st.result != null) return@update st
        val size = st.current?.slotLabels?.size ?: 0
        val picks = MutableList(size) { st.slotPicks.getOrNull(it) }
        if (index in picks.indices) picks[index] = v
        st.copy(slotPicks = picks)
    }

    fun submit(skip: Boolean = false) {
        val s = _state.value
        val q = s.current ?: return
        if (s.result != null) return
        if (!skip && !s.canSubmit) return
        val answer = when {
            skip -> ""
            q.inputType == INPUT_CHOICE -> s.selectedChoice.orEmpty()
            q.inputType == INPUT_SLOTS -> AnswerGrader.encodeSlots(q.slotLabels, s.slotPicks)
            else -> s.input.trim()
        }
        val graded = when {
            skip -> AnswerGrader.Result(false, null)
            q.inputType == INPUT_CHOICE -> AnswerGrader.gradeChoice(s.selectedChoice, q.blankAnswer)
            q.inputType == INPUT_SLOTS -> AnswerGrader.gradeSlots(s.slotPicks, q.slotAnswers)
            else -> AnswerGrader.grade(answer, q.blankAnswer, q.acceptedAnswers, q.unit)
        }
        val res = AnswerResult(graded.isCorrect, if (skip) "(모름)" else answer, skip)
        val responseMs = System.currentTimeMillis() - questionShownAt
        _state.update {
            it.copy(
                result = res,
                correctCount = it.correctCount + if (res.isCorrect) 1 else 0,
                answeredCount = it.answeredCount + 1,
                history = it.history + (q to res),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val settings = container.settingsRepository.current()
            container.studyRepository.recordAnswer(
                question = q,
                mode = s.mode,
                userAnswer = res.userAnswer,
                isCorrect = res.isCorrect,
                responseMs = responseMs,
                now = System.currentTimeMillis(),
                scheduler = ReviewScheduler(settings.intervals),
                conceptQuestionIds = q.conceptIds.associateWith { cid -> content.questionsByPrimary[cid].orEmpty().map { it.id } },
            )
        }
    }

    fun next() {
        _state.update {
            if (it.index + 1 >= it.questions.size) it.copy(finished = true)
            else it.copy(index = it.index + 1, input = "", selectedChoice = null, slotPicks = emptyList(), result = null)
        }
        questionShownAt = System.currentTimeMillis()
    }

    private fun emptyMessage(type: ScopeType) = when (type) {
        ScopeType.WRONG -> "현재 모드에서 미해결 오답이 없습니다."
        ScopeType.DUE -> "오늘 복습할 문항이 없습니다."
        ScopeType.UNSTUDIED -> "모든 개념을 학습했습니다."
        ScopeType.WEAK -> "현재 취약 개념이 없습니다. 문제를 더 풀면 정답률 60% 미만 개념이 여기에 모입니다."
        else -> "선택한 범위·난이도·문항 형식에 해당하는 문항이 없습니다."
    }

    companion object {
        /** 출제 범위별 후보 문항(활성 문항만). 난이도·형식 필터를 함께 적용한다. */
        fun candidatesFor(scope: QuizScope, content: ContentSnapshot, user: UserSnapshot, now: Long): List<QuestionEntity> {
            val active = content.questions.filter { q -> content.conceptById[q.primaryConceptId]?.isActive == true }
            val scoped = when (scope.type) {
                ScopeType.ALL, ScopeType.DUE, ScopeType.MOCK -> active
                ScopeType.SUBJECT -> active.filter { it.subjectId == scope.id }
                ScopeType.WEIGHTED -> active.filter { it.subjectId == (scope.id ?: LAW_SUBJECT) && it.examArea != null }
                ScopeType.WRONG -> if (scope.id == null) active else active.filter { scope.id in it.conceptIds }
                ScopeType.PART -> {
                    val chapters = content.chapters.filter { it.partTitle == scope.id }.map { it.id }.toSet()
                    active.filter { it.chapterId in chapters }
                }
                ScopeType.CHAPTER -> active.filter { it.chapterId == scope.id }
                ScopeType.SECTION -> active.filter { content.conceptById[it.primaryConceptId]?.sectionId == scope.id }
                ScopeType.CONCEPT -> active.filter { scope.id in it.conceptIds }
                ScopeType.QUESTION -> active.filter { it.id == scope.id }
                ScopeType.EXAM -> {
                    val ids = content.examScopes.firstOrNull { it.number == scope.id }?.conceptIds.orEmpty().toSet()
                    active.filter { q -> q.conceptIds.any { it in ids } }
                }
                ScopeType.UNSTUDIED -> {
                    val mastery = StatsCalculator.masteryMap(content, user, now)
                    active.filter {
                        mastery[it.primaryConceptId] == MasteryLevel.NOT_STARTED || mastery[it.primaryConceptId] == MasteryLevel.STARTED
                    }
                }
                ScopeType.WEAK -> {
                    val weak = weakConcepts(content, user)
                    active.filter { it.primaryConceptId in weak }
                }
                ScopeType.RECENT -> {
                    val latest = content.concepts.maxOfOrNull { it.contentVersion } ?: 0
                    val recent = content.concepts.filter { it.contentVersion == latest }.map { it.id }.toSet()
                    active.filter { it.primaryConceptId in recent }
                }
            }
            if (scope.type == ScopeType.QUESTION) return scoped
            return scoped.filter { q ->
                q.difficulty in scope.minLevel..scope.maxLevel && when (scope.kind) {
                    QuestionKind.ALL -> true
                    QuestionKind.BLANK -> q.inputType == INPUT_TEXT && q.questionType != "calculation"
                    QuestionKind.CALC -> q.questionType == "calculation"
                    QuestionKind.OPTIONS -> q.inputType != INPUT_TEXT
                }
            }
        }

        const val LAW_SUBJECT = "CPA-LAW"

        /** 실전 비중 모의의 영역별 배정 문항 수(공고 비중 기준, 기업법 40문항) */
        fun weightedQuota(content: ContentSnapshot, subjectId: String?): Map<String, Int> =
            content.examAreas.filter { it.subjectId == (subjectId ?: LAW_SUBJECT) }.sortedBy { AREA_ORDER.indexOf(it.id) }
                .associate { it.id to it.mockQuestions }

        private val AREA_ORDER = listOf("GENERAL", "COMPANY", "CPA_ACT", "AUDIT_ACT")

        /** 취약 개념: 두 모드 합산 시도 3회 이상이면서 정답률 60% 미만이거나, 미해결 오답이 2개 이상인 개념 */
        fun weakConcepts(content: ContentSnapshot, user: UserSnapshot): Set<String> {
            val unresolved = user.wrongNotes.filter { !it.isResolved }
                .mapNotNull { content.questionById[it.questionId]?.primaryConceptId }
                .groupingBy { it }.eachCount()
            return content.concepts.map { it.id }.filter { cid ->
                val rows = content.questionsByPrimary[cid].orEmpty().flatMap { q ->
                    QuizMode.entries.mapNotNull { m -> user.progressMap[q.id to m.name] }
                }
                val attempts = rows.sumOf { it.totalAttempts }
                val correct = rows.sumOf { it.correctAttempts }
                (attempts >= 3 && correct.toDouble() / attempts < 0.6) || (unresolved[cid] ?: 0) >= 2
            }.toSet()
        }
    }
}
