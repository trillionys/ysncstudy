package com.cpaconceptlab.study.ui.subjects

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
import com.cpaconceptlab.study.ui.DashboardState
import com.cpaconceptlab.study.ui.components.ChapterStatusPill
import com.cpaconceptlab.study.ui.components.MasteryPill
import com.cpaconceptlab.study.ui.components.ProgressLine
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.VSpace
import com.cpaconceptlab.study.ui.components.pct

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

/** 기출범위별 학습현황(01~46) */
@Composable
fun ExamScopeScreen(dash: DashboardState, onOpenConcept: (String) -> Unit, onStartQuiz: (QuizMode, QuizScope) -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val scopes = dash.examScopes
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionCard(title = "기출범위 커버리지") {
                val studied = scopes.count { it.studiedConcepts == it.conceptIds.size && it.conceptIds.isNotEmpty() }
                ProgressLine("모든 핵심 개념을 학습한 출제범위", studied, scopes.size)
                Text("원래 문제번호는 출제범위 추적용 정보이며 기출 원문은 수록하지 않습니다. 모든 문항은 개념을 바탕으로 새로 작성한 교육용 빈칸문제입니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(scopes, key = { it.number }) { s ->
            SectionCard {
                Row(Modifier.fillMaxWidth().clickable { open = if (open == s.number) null else s.number }, verticalAlignment = Alignment.CenterVertically) {
                    Text(s.number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.width(36.dp))
                    Text(s.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(pct(s.accuracy), style = MaterialTheme.typography.labelLarge)
                }
                ProgressLine("개념 학습 ${s.studiedConcepts}/${s.conceptIds.size} · 문항 풀이", s.solvedQuestions, s.questionCount)
                if (open == s.number) {
                    s.conceptIds.forEach { id ->
                        val con = dash.content.conceptById[id] ?: return@forEach
                        ConceptRow(id, con.koreanName, dash.overview.masteryByConcept[id] ?: MasteryLevel.NOT_STARTED) { onOpenConcept(id) }
                    }
                    VSpace(6)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.EXAM, s.number)) }, modifier = Modifier.weight(1f)) { Text("기본 풀기") }
                        OutlinedButton(onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.EXAM, s.number)) }, modifier = Modifier.weight(1f)) { Text("심화 풀기") }
                    }
                }
            }
        }
    }
}
