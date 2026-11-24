package com.cpaconceptlab.study.ui.subjects

import com.cpaconceptlab.study.ui.quiz.QuizViewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.domain.model.MasteryLevel
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.domain.model.QuestionKind
import com.cpaconceptlab.study.domain.usecase.PartStat
import com.cpaconceptlab.study.domain.usecase.StatsCalculator
import com.cpaconceptlab.study.ui.DashboardState
import com.cpaconceptlab.study.ui.components.ChapterStatusPill
import com.cpaconceptlab.study.ui.components.MasteryPill
import com.cpaconceptlab.study.ui.components.ProgressLine
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.VSpace
import com.cpaconceptlab.study.ui.components.pct
import com.cpaconceptlab.study.domain.usecase.LawCoverage

/** 과목 → 대단원 → 중단원 → 세부 개념 계층 화면 */
@Composable
fun SubjectScreen(
    dash: DashboardState,
    subjectId: String,
    onOpenConcept: (String) -> Unit,
    onStartQuiz: (QuizMode, QuizScope) -> Unit,
) {
    val c = dash.content
    var expanded by rememberSaveable { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    val chapters = c.chaptersOf(subjectId)
    val stats = dash.overview.chapters.associateBy { it.chapterId }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(query, { query = it }, label = { Text("개념 검색 (이름·코드·영문)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (query.isBlank() && c.examAreas.any { it.subjectId == subjectId }) {
            item(key = "law-header") { LawSubjectHeader(dash, subjectId, onStartQuiz) }
        }
        if (query.isBlank()) {
            val parts = StatsCalculator.partStats(c, dash.user, dash.overview.masteryByConcept)
                .filter { p -> p.chapterIds.any { id -> c.chapterById[id]?.subjectId == subjectId } }
            items(parts, key = { "part-" + it.partTitle }) { p -> PartCard(p, onStartQuiz) }
        }
        if (query.isNotBlank()) {
            val found = c.concepts.filter {
                it.subjectId == subjectId && (it.id.contains(query, true) || it.koreanName.contains(query) || it.englishName.contains(query, true))
            }
            items(found, key = { "f" + it.id }) { con ->
                ConceptRow(con.id, con.koreanName, dash.overview.masteryByConcept[con.id] ?: MasteryLevel.NOT_STARTED) { onOpenConcept(con.id) }
            }
            return@LazyColumn
        }
        items(chapters, key = { it.id }) { ch ->
            val st = stats[ch.id]
            SectionCard {
                Row(Modifier.fillMaxWidth().clickable { expanded = if (ch.id in expanded) expanded - ch.id else expanded + ch.id }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${ch.code}. ${ch.title}", style = MaterialTheme.typography.titleMedium)
                        Text(ch.englishTitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (st != null) ChapterStatusPill(st.status)
                }
                if (st != null) {
                    ProgressLine("개념 ${st.conceptCount} · 문항 ${st.questionCount} · 정답률 ${pct(st.accuracy)}", st.studiedConcepts, st.conceptCount)
                }
                if (ch.id in expanded) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.CHAPTER, ch.id)) }, modifier = Modifier.weight(1f)) { Text("단원 기본") }
                        OutlinedButton(onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.CHAPTER, ch.id)) }, modifier = Modifier.weight(1f)) { Text("단원 심화") }
                    }
                    c.sectionsOf(ch.id).forEach { sec ->
                        VSpace(10)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sec.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.SECTION, sec.id)) }) { Text("중단원 풀기") }
                        }
                        c.conceptsOfSection(sec.id).forEach { con ->
                            ConceptRow(con.id, con.koreanName, dash.overview.masteryByConcept[con.id] ?: MasteryLevel.NOT_STARTED, indent = con.parentConceptId != null) { onOpenConcept(con.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConceptRow(id: String, name: String, level: MasteryLevel, indent: Boolean = false, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (indent) Spacer(Modifier.width(14.dp))
            Text(id, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp))
            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            MasteryPill(level)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 기업법: 두 가지 학습 모드 + 법률별 조문 커버리지 */
@Composable
fun LawSubjectHeader(dash: DashboardState, subjectId: String, onStartQuiz: (QuizMode, QuizScope) -> Unit) {
    val c = dash.content
    var showCoverage by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = "학습 모드") {
        Text("① 전체 개념 균등학습: 모든 개념을 한 번씩 출제한 뒤에 반복합니다.", style = MaterialTheme.typography.bodySmall)
        Text(
            "② CPA 실전 비중 모드: 공고 출제비중(" + c.examAreas.filter { it.subjectId == subjectId }
                .sortedBy { QuizViewModel.AREA_ORDER.indexOf(it.id) }
                .joinToString(" · ") { "${it.name} ${it.minPercent}~${it.maxPercent}%" } + ")에 맞춘 40문항·100점 모의고사입니다.",
            style = MaterialTheme.typography.bodySmall,
        )
        VSpace(6)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.SUBJECT, subjectId)) }, modifier = Modifier.weight(1f)) { Text("균등 기본") }
            OutlinedButton(onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.SUBJECT, subjectId)) }, modifier = Modifier.weight(1f)) { Text("균등 심화") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.WEIGHTED, subjectId)) }, modifier = Modifier.weight(1f)) { Text("실전 모의 기본") }
            OutlinedButton(onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.WEIGHTED, subjectId)) }, modifier = Modifier.weight(1f)) { Text("실전 모의 심화") }
        }
        TextButton(onClick = { showCoverage = !showCoverage }) { Text(if (showCoverage) "조문 커버리지 접기" else "조문 커버리지 보기") }
        if (showCoverage) {
            LawCoverage.rows(c.lawArticles, subjectId).forEach { r ->
                VSpace(6)
                Text(r.group, style = MaterialTheme.typography.titleSmall)
                ProgressLine("개념 연결 조문", r.linked, r.live)
                Text(
                    "현행 ${r.live} · 학습 완료 ${r.completed} · 관련 개념에 통합 ${r.integrated} · 작성 중 ${r.writing} · 검토 필요 ${r.review} · 삭제 ${r.deleted}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val laws = LawCoverage.groupsOf(subjectId).map { it.second }.toSet()
            val mine = c.lawArticles.filter { it.lawShortName in laws }
            val outOfScope = mine.count { it.status == "OUT_OF_SCOPE" }
            VSpace(6)
            Text(
                if (subjectId == "TX") "시험범위 밖 조문(지방세법 중 취득세·등록면허세·재산세 외): ${outOfScope}개 · 시행령·시행규칙은 원문 미대조"
                else "시험범위 밖 조문(상법 보험·해상·항공운송편): ${outOfScope}개",
                style = MaterialTheme.typography.bodySmall,
            )
            mine.filter { it.status == "REVIEW_NEEDED" }.forEach {
                Text("검토 필요: ${it.lawShortName} ${it.articleId}(${it.title}) — ${it.note.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** 파트 카드: 개념 수·학습 개념·문제 수·정답률·오답 수·숙달률 */
@Composable
fun PartCard(p: PartStat, onStartQuiz: (QuizMode, QuizScope) -> Unit) {
    SectionCard(title = p.partTitle) {
        ProgressLine("개념 ${p.conceptCount} · 학습 ${p.studiedConcepts}", p.studiedConcepts, p.conceptCount)
        Text(
            "문제 ${p.questionCount} (풀이 ${p.solvedQuestions}) · 정답률 ${pct(p.accuracy)} · 미해결 오답 ${p.wrongCount} · 숙달률 ${pct(p.masteryRate)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        VSpace(6)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.PART, p.partTitle)) }, modifier = Modifier.weight(1f)) { Text("기본") }
            OutlinedButton(onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.PART, p.partTitle)) }, modifier = Modifier.weight(1f)) { Text("심화") }
            OutlinedButton(
                onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.PART, p.partTitle, kind = QuestionKind.CALC)) },
                modifier = Modifier.weight(1f),
            ) { Text("계산") }
        }
    }
}
