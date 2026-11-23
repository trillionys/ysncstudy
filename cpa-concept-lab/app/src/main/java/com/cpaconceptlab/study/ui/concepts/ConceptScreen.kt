package com.cpaconceptlab.study.ui.concepts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.domain.model.MasteryLevel
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.domain.model.QuestionKind
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch
import com.cpaconceptlab.study.domain.model.StandardStatusResolver
import com.cpaconceptlab.study.ui.DashboardState
import com.cpaconceptlab.study.ui.components.EmptyState
import com.cpaconceptlab.study.ui.components.InfoBlock
import com.cpaconceptlab.study.ui.components.KeyValue
import com.cpaconceptlab.study.ui.components.MasteryPill
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.StandardBadges
import com.cpaconceptlab.study.ui.components.VSpace
import com.cpaconceptlab.study.ui.components.pct
import java.time.LocalDate

/** 개념 학습 화면: 요구사항 5의 11개 요소를 모두 표시한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConceptScreen(
    dash: DashboardState,
    conceptId: String,
    onViewed: (String) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onSaveMemo: (String, String) -> Unit,
    onOpenConcept: (String) -> Unit,
    onStartQuiz: (QuizMode, QuizScope) -> Unit,
) {
    val c = dash.content.conceptById[conceptId]
    if (c == null) { EmptyState("개념을 찾을 수 없습니다: $conceptId"); return }
    LaunchedEffect(conceptId) { onViewed(conceptId) }
    val cp = dash.user.conceptProgressMap[conceptId]
    val level = dash.overview.masteryByConcept[conceptId] ?: MasteryLevel.NOT_STARTED
    val chapter = dash.content.chapterById[c.chapterId]
    val section = dash.content.sectionById[c.sectionId]
    val questions = dash.content.questionsByPrimary[conceptId].orEmpty()
    var memo by remember(conceptId) { mutableStateOf(cp?.memo.orEmpty()) }
    val scroll = rememberScrollState()
    val scopeLaunch = rememberCoroutineScope()
    var explanationY by remember { mutableStateOf(0) }
    val linkedQuestions = dash.content.questionsByLinked[conceptId].orEmpty()
    val calcCount = linkedQuestions.count { it.questionType == "calculation" }
    val linkedIds = linkedQuestions.map { it.id }.toSet()
    val unresolvedWrong = dash.user.wrongNotes.count { !it.isResolved && it.questionId in linkedIds }

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
        Text("${chapter?.code}. ${chapter?.title} › ${section?.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        VSpace(4)
        // 1. 개념명(국문·영문)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${c.id}  ${c.koreanName}", style = MaterialTheme.typography.headlineSmall)
                Text(c.englishName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { onToggleBookmark(conceptId) }) { Text(if (cp?.isBookmarked == true) "★ 북마크" else "☆ 북마크") }
        }
        VSpace(6)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { MasteryPill(level) }
        VSpace(8)
        StandardBadges(
            StandardStatusResolver.resolve(c.effectiveFrom, c.effectiveTo, c.isHistorical, LocalDate.now()),
            c.requiresReview, c.standardReference, c.revisionNote,
        )
        VSpace(4)
        Text(
            "검수 상태: " + verificationLabel(c.verificationStatus),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(10)
        // 학습 동작 버튼: 개념 공부 · 기본 문제 · 심화 문제 · 계산 연습 · 오답 복습
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { onViewed(conceptId); scopeLaunch.launch { scroll.animateScrollTo(explanationY) } }) { Text("개념 공부") }
            Button(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.CONCEPT, c.id)) }) { Text("기본 문제") }
            OutlinedButton(onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.CONCEPT, c.id)) }) { Text("심화 문제") }
            OutlinedButton(
                onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.CONCEPT, c.id, kind = QuestionKind.CALC)) },
                enabled = calcCount > 0,
            ) { Text("계산 연습 $calcCount") }
            OutlinedButton(
                onClick = {
                    val mode = if (dash.user.wrongNotes.any { !it.isResolved && it.questionId in linkedIds && it.mode == QuizMode.ADVANCED.name }) QuizMode.ADVANCED else QuizMode.BASIC
                    onStartQuiz(mode, QuizScope(ScopeType.WRONG, c.id))
                },
                enabled = unresolvedWrong > 0,
            ) { Text("오답 복습 $unresolvedWrong") }
        }
        if (c.parentConceptId != null) {
            VSpace(4)
            TextButton(onClick = { onOpenConcept(c.parentConceptId) }) { Text("상위 개념: ${c.parentConceptId} ${dash.content.conceptById[c.parentConceptId]?.koreanName.orEmpty()}") }
        }
        VSpace(10)

        SectionCard(modifier = Modifier.onGloballyPositioned { explanationY = it.positionInParent().y.toInt() }) {
            InfoBlock("① 정확한 정의", c.definition, emphasize = true)
            InfoBlock("② 개념이 필요한 이유", c.whyItMatters)
            InfoBlock("③ 핵심 원리", c.detailedExplanation)
            InfoBlock("④ 유사 개념과의 차이", c.comparison)
            InfoBlock("⑤ 실제 회계 사례", c.example)
            InfoBlock("⑥ 공식과 계산과정", c.formula ?: "해당 없음 — 계산이 필요한 개념이 아닙니다.")
            InfoBlock("⑦ CPA 출제 포인트", c.examTips)
            InfoBlock("⑧ 자주 틀리는 함정", c.commonMistakes)
            c.recognitionRules?.let { InfoBlock("인식 요건", it) }
            c.measurementRules?.let { InfoBlock("측정 기준", it) }
            c.exceptions?.let { InfoBlock("예외", it) }
            c.journalEntries?.let { InfoBlock("분개", it) }
            if (c.sourceProblemIds.isNotEmpty()) InfoBlock("연결된 원본 문제", c.sourceProblemIds.joinToString(", "))
        }
        VSpace(12)

        // 관련 개념
        SectionCard(title = "⑨ 관련 개념") {
            val related = (c.relatedConceptIds + c.prerequisiteConceptIds).distinct()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                related.forEach { id ->
                    dash.content.conceptById[id]?.let { r -> AssistChip(onClick = { onOpenConcept(id) }, label = { Text("${r.id} ${r.koreanName}") }) }
                }
                dash.content.concepts.filter { it.parentConceptId == c.id }.forEach { child ->
                    AssistChip(onClick = { onOpenConcept(child.id) }, label = { Text("하위: ${child.id} ${child.koreanName}") })
                }
            }
            if (c.examReferenceNumbers.isNotEmpty()) {
                VSpace(6)
                Text("출제범위(기출 번호): ${c.examReferenceNumbers.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        VSpace(12)

        // 학습 현황 + 문제 시작
        SectionCard(title = "⑩ 관련 빈칸 문제 (${questions.size}문항)") {
            val rows = questions.mapNotNull { q -> dash.user.progressMap[q.id to QuizMode.BASIC.name] }
            val adv = questions.mapNotNull { q -> dash.user.progressMap[q.id to QuizMode.ADVANCED.name] }
            KeyValue("기본 모드 풀이", "${rows.size}/${questions.size} · 정답률 ${pct(rows.sumOf { it.totalAttempts }.takeIf { it > 0 }?.let { rows.sumOf { r -> r.correctAttempts }.toDouble() / it })}")
            KeyValue("심화 모드 풀이", "${adv.size}/${questions.size} · 정답률 ${pct(adv.sumOf { it.totalAttempts }.takeIf { it > 0 }?.let { adv.sumOf { r -> r.correctAttempts }.toDouble() / it })}")
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.CONCEPT, c.id)) }, modifier = Modifier.weight(1f)) { Text("기본 문제 (초성)") }
                OutlinedButton(onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.CONCEPT, c.id)) }, modifier = Modifier.weight(1f)) { Text("심화 문제") }
            }
        }
        VSpace(12)
        SectionCard(title = "⑪ 나의 메모") {
            OutlinedTextField(memo, { memo = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, placeholder = { Text("헷갈린 점, 암기 요령 등을 기록하세요") })
            TextButton(onClick = { onSaveMemo(conceptId, memo) }) { Text("메모 저장") }
        }
        VSpace(24)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 검수 상태 표시. 공식 기준서 원문과 대조하지 않은 콘텐츠는 '검증 완료'로 표시하지 않는다. */
fun verificationLabel(status: String): String = when (status) {
    "VERIFIED" -> "공식 자료 대조 완료"
    "NEEDS_REVIEW" -> "검토 필요(교재 관행·개정 확인 대상 포함)"
    "AUTHOR_REVIEWED" -> "작성자 검토 완료 · 기준서 원문 대조 전"
    else -> status
}
