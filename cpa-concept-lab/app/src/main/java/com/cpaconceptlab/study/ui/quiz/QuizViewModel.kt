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
    val result: AnswerResult? = null,
    val correctCount: Int = 0,
    val answeredCount: Int = 0,
    val finished: Boolean = false,
    val history: List<Pair<QuestionEntity, AnswerResult>> = emptyList(),
    val emptyReason: String? = null,
) {
    val current: QuestionEntity? get() = questions.getOrNull(index)
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
                QuestionSelector().select(
                    candidates = candidates,
                    mode = mode,
                    scopeType = if (scope.type == ScopeType.QUESTION) ScopeType.MOCK else scope.type,
                    size = if (scope.type == ScopeType.QUESTION) 1 else size,
                    progress = user.progressMap,
                    wrongNotes = user.wrongNotes,
                    now = now,
                    recentlyAnswered = container.studyRepository.recentlyAnswered(10 * 60_000, now),
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

    fun submit(skip: Boolean = false) {
        val s = _state.value
        val q = s.current ?: return
        if (s.result != null) return
        val answer = if (skip) "" else s.input.trim()
        if (!skip && answer.isEmpty()) return
        val graded = AnswerGrader.grade(answer, q.blankAnswer, q.acceptedAnswers, q.unit)
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
            else it.copy(index = it.index + 1, input = "", result = null)
        }
        questionShownAt = System.currentTimeMillis()
    }

    private fun emptyMessage(type: ScopeType) = when (type) {
        ScopeType.WRONG -> "현재 모드에서 미해결 오답이 없습니다."
        ScopeType.DUE -> "오늘 복습할 문항이 없습니다."
        ScopeType.UNSTUDIED -> "모든 개념을 학습했습니다."
        else -> "출제할 문항이 없습니다."
    }

    companion object {
        /** 출제 범위별 후보 문항(활성 문항만) */
        fun candidatesFor(scope: QuizScope, content: ContentSnapshot, user: UserSnapshot, now: Long): List<QuestionEntity> {
            val active = content.questions.filter { q -> content.conceptById[q.primaryConceptId]?.isActive == true }
            return when (scope.type) {
                ScopeType.ALL, ScopeType.WRONG, ScopeType.DUE, ScopeType.MOCK -> active
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
            }
        }
    }
}
