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
import com.cpaconceptlab.study.data.local.ConceptEntity
import com.cpaconceptlab.study.data.importer.detailSectionList
import com.cpaconceptlab.study.ui.common.ContentNotice
import com.cpaconceptlab.study.ui.common.ErrorReportDialog
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.cpaconceptlab.study.data.repository.ContentSnapshot
import com.cpaconceptlab.study.domain.model.LawApplicability
import com.cpaconceptlab.study.ui.components.Pill
import com.cpaconceptlab.study.ui.theme.LocalStatusColors

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
    initialTab: String = "SUMMARY",
    onTabChange: (String) -> Unit = {},
    /** 문제풀이 화면에서 들어온 경우 '문제로 돌아가기'(세션은 뒤 화면에 그대로 남아 있다) */
    onBackToQuiz: (() -> Unit)? = null,
    appVersion: String = "",
    onStartUnitQuiz: ((QuizScope, String) -> Unit)? = null,
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
        val subjectName = dash.content.subjects.firstOrNull { it.id == c.subjectId }?.name ?: c.subjectId
        Text("$subjectName › ${chapter?.partTitle?.ifBlank { null }?.let { "$it › " }.orEmpty()}${chapter?.code}. ${chapter?.title} › ${section?.title}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onBackToQuiz != null) {
            VSpace(4)
            Button(onClick = onBackToQuiz, modifier = Modifier.fillMaxWidth()) { Text("← 문제로 돌아가기") }
        }
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

        val isLaw = c.articleRefs.isNotEmpty()
        if (isLaw) {
            LawInfoCard(c, dash.content)
            VSpace(12)
        }
        // 간단 요약 / 상세 학습 탭(마지막으로 본 탭을 기억)
        var tab by remember(conceptId) { mutableStateOf(if (initialTab == "DETAIL") 1 else 0) }
        val sections = remember(c.id, c.detailSections) { c.detailSectionList() }
        TabRow(selectedTabIndex = tab, modifier = Modifier.onGloballyPositioned { explanationY = it.positionInParent().y.toInt() }) {
            Tab(selected = tab == 0, onClick = { tab = 0; onTabChange("SUMMARY") }, text = { Text("간단 요약") })
            Tab(selected = tab == 1, onClick = { tab = 1; onTabChange("DETAIL") }, text = { Text("상세 학습 (${sections.size})") })
        }
        VSpace(8)
        SectionCard {
            if (tab == 0) SummaryTab(c) else DetailTab(c, sections)
            if (c.sourceProblemIds.isNotEmpty()) InfoBlock("연결된 원본 문제", c.sourceProblemIds.joinToString(", "))
        }
        VSpace(12)
        SourceCard(c)
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
        // 하단 동작: 관련 문제 풀기 · 다음 개념 · 북마크 · 오류 신고
        val siblings = dash.content.concepts.filter { it.chapterId == c.chapterId }.sortedBy { it.sortOrder }
        val next = siblings.getOrNull(siblings.indexOfFirst { it.id == c.id } + 1)
        var reporting by remember { mutableStateOf(false) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = {
                if (onStartUnitQuiz != null) onStartUnitQuiz(QuizScope(ScopeType.CONCEPT, c.id), c.koreanName)
                else onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.CONCEPT, c.id))
            }) { Text("관련 문제 풀기") }
            OutlinedButton(onClick = { next?.let { onOpenConcept(it.id) } }, enabled = next != null) { Text("다음 개념 ›") }
            OutlinedButton(onClick = { onToggleBookmark(conceptId) }) { Text(if (cp?.isBookmarked == true) "★ 북마크됨" else "☆ 북마크") }
            OutlinedButton(onClick = { reporting = true }) { Text("오류 신고") }
        }
        if (reporting) {
            ErrorReportDialog(c.id, c.koreanName, null, dash.content.bundledVersion, appVersion, onDismiss = { reporting = false })
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

/** 기업법 개념의 법령 정보: 적용 기준 라벨, 법령 버전·시행일, 경과규정, 근거 조문 원문, 숫자 규칙 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LawInfoCard(c: ConceptEntity, content: ContentSnapshot) {
    val s = LocalStatusColors.current
    val cs = MaterialTheme.colorScheme
    var showText by remember(c.id) { mutableStateOf(false) }
    SectionCard(title = "법령 근거") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when (LawApplicability.of(c.examApplicability)) {
                LawApplicability.APPLIES_2027 -> Pill(LawApplicability.APPLIES_2027.label, s.correctContainer, s.correct)
                LawApplicability.PAST -> Pill(LawApplicability.PAST.label, cs.surfaceContainerHighest, cs.onSurfaceVariant)
                LawApplicability.NOT_IN_FORCE -> Pill(LawApplicability.NOT_IN_FORCE.label, cs.secondaryContainer, cs.onSecondaryContainer)
                LawApplicability.CHECK_DATE -> Pill(LawApplicability.CHECK_DATE.label, s.warnContainer, s.warn)
                LawApplicability.CURRENT_LAW -> Pill(LawApplicability.CURRENT_LAW.label, cs.primaryContainer, cs.onPrimaryContainer)
                LawApplicability.ANSWER_CHANGED -> Pill(LawApplicability.ANSWER_CHANGED.label, cs.errorContainer, cs.onErrorContainer)
                null -> {}
            }
        }
        VSpace(6)
        KeyValue("법령", c.lawName.orEmpty())
        KeyValue("조문", c.articleRefs.joinToString(", "))
        c.lawVersion?.let { KeyValue("법령 버전", it) }
        c.promulgationDate?.let { KeyValue("공포일", it) }
        c.effectiveFrom?.let { KeyValue("시행일", it) }
        c.examYear?.let { KeyValue("적용 시험", "${it}년 공인회계사 1차") }
        c.verifiedAt?.let { KeyValue("조문 대조일", it) }
        c.transitionRule?.let { InfoBlock("경과규정·개정 메모", it) }
        val rules = content.numericRulesOf(c.id)
        if (rules.isNotEmpty()) {
            InfoBlock("숫자·기간·정족수 규칙", rules.joinToString("\n") { "· ${it.ruleName}: ${it.value}${it.unit} (${it.article})" })
        }
        val notes = content.caseNotesOf(c.id)
        notes.forEach { n -> InfoBlock("판례 법리: ${n.topic}", "${n.holding}\n(사건번호 미기재 · 판례 원문 미대조 — 판례 법리 요약만 제공합니다)") }
        val articles = content.articlesOf(c)
        if (articles.isNotEmpty()) {
            TextButton(onClick = { showText = !showText }) { Text(if (showText) "조문 원문 접기" else "조문 원문 보기 (${articles.size})") }
            if (showText) {
                articles.forEach { a ->
                    InfoBlock("${a.lawShortName} ${a.articleId}(${a.title}) · ${a.statusLabel}", a.text.ifBlank { "(원문 없음)" })
                }
                c.sourceUrl?.let { Text("출처: 국가법령정보센터 $it", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
            }
        }
    }
}

/** 검수 상태 표시. 공식 기준서 원문과 대조하지 않은 콘텐츠는 '검증 완료'로 표시하지 않는다. */
fun verificationLabel(status: String): String = when (status) {
    "VERIFIED" -> "공식 자료 대조 완료"
    "NEEDS_REVIEW" -> "검토 필요(교재 관행·개정 확인 대상 포함)"
    "AUTHOR_REVIEWED" -> "작성자 검토 완료 · 기준서 원문 대조 전"
    "STATUTE_CHECKED" -> "법령 원문(국가법령정보센터) 대조 · 정답 자동 대조"
    else -> status
}


/** 공식 출처 확인 영역: 기준서·법령, 적용 기준일, 검증일, 검증 상태, 공식 링크, 짧은 안내문 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceCard(c: ConceptEntity) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    SectionCard(title = "공식 출처 확인") {
        Text(ContentNotice.SHORT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        VSpace(6)
        KeyValue("기준서·법령", c.lawName ?: c.standardReference.ifBlank { "—" })
        if (c.articleRefs.isNotEmpty()) KeyValue("조문", c.articleRefs.joinToString(", "))
        KeyValue("적용 기준일", c.effectiveFrom ?: "확인 필요")
        KeyValue("마지막 검증일", c.verifiedAt ?: "—")
        KeyValue("검증 상태", verificationLabel(c.verificationStatus))
        Text("앱의 설명은 기준서·법령 원문이 아니라 자체 작성한 학습용 해설입니다.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        val links = buildList {
            c.sourceUrl?.let { add("이 개념의 법령 원문" to it) }
            val std = c.standardReference + " " + (c.lawName ?: "")
            if (c.articleRefs.isEmpty() && (std.contains("K-IFRS") || std.contains("개념체계"))) add("한국회계기준원(K-IFRS)" to "https://www.kasb.or.kr")
            if (std.contains("국가회계") || std.contains("지방")) add("국가법령정보센터" to "https://www.law.go.kr")
            if (c.subjectId == "TX") add("국세청" to "https://www.nts.go.kr")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            links.forEach { (label, url) -> AssistChip(onClick = { ContentNotice.open(ctx, url) }, label = { Text("$label ↗") }) }
        }
    }
}
