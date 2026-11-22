package com.cpaconceptlab.study.ui.review

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.data.model.BLANK_TOKEN
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.ui.DashboardState
import com.cpaconceptlab.study.ui.components.EmptyState
import com.cpaconceptlab.study.ui.components.KeyValue
import com.cpaconceptlab.study.ui.components.Pill
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.VSpace
import com.cpaconceptlab.study.ui.quiz.Explanation
import com.cpaconceptlab.study.ui.theme.LocalStatusColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
fun fmt(ms: Long?): String = if (ms == null) "–" else Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DATE)

/** 오답노트: 과목·단원·개념·모드·최근/횟수·미해결 필터 */
@Composable
fun WrongNotesScreen(
    dash: DashboardState,
    onOpenConcept: (String) -> Unit,
    onStartQuiz: (QuizMode, QuizScope) -> Unit,
) {
    val content = dash.content
    val status = LocalStatusColors.current
    var subject by rememberSaveable { mutableStateOf<String?>(null) }
    var chapter by rememberSaveable { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf<String?>(null) }
    var unresolvedOnly by rememberSaveable { mutableStateOf(true) }
    var sortByCount by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }

    val notes = dash.user.wrongNotes
        .filter { content.questionById[it.questionId]?.isActive == true }
        .filter { !unresolvedOnly || !it.isResolved }
        .filter { mode == null || it.mode == mode }
        .filter { n -> val q = content.questionById[n.questionId]!!; subject == null || q.subjectId == subject }
        .filter { n -> val q = content.questionById[n.questionId]!!; chapter == null || q.chapterId == chapter }
        .filter { n ->
            if (query.isBlank()) true else {
                val q = content.questionById[n.questionId]!!
                q.conceptIds.any { id -> id.contains(query, true) || content.conceptById[id]?.koreanName?.contains(query) == true }
            }
        }
        .let { list -> if (sortByCount) list.sortedByDescending { it.wrongCount } else list.sortedByDescending { it.lastWrongAt } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            SectionCard(title = "오답노트 (${notes.size})") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(unresolvedOnly, { unresolvedOnly = !unresolvedOnly }, { Text("미해결만") })
                    FilterChip(!sortByCount, { sortByCount = false }, { Text("최근 오답순") })
                    FilterChip(sortByCount, { sortByCount = true }, { Text("오답 횟수순") })
                    FilterChip(mode == null, { mode = null }, { Text("기본+심화") })
                    QuizMode.entries.forEach { m -> FilterChip(mode == m.name, { mode = m.name }, { Text(m.label) }) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(subject == null, { subject = null }, { Text("전체 과목") })
                    content.subjects.filter { s -> content.concepts.any { it.subjectId == s.id } }.forEach { s ->
                        FilterChip(subject == s.id, { subject = s.id }, { Text(s.name) })
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(chapter == null, { chapter = null }, { Text("전체 단원") })
                    content.chapters.forEach { ch -> FilterChip(chapter == ch.id, { chapter = ch.id }, { Text(ch.code) }) }
                }
                OutlinedTextField(query, { query = it }, label = { Text("개념 필터(이름·코드)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                VSpace(8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.WRONG)) }, modifier = Modifier.weight(1f)) { Text("기본 오답 복습") }
                    OutlinedButton(onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.WRONG)) }, modifier = Modifier.weight(1f)) { Text("심화 오답 복습") }
                }
            }
        }
        if (notes.isEmpty()) item { EmptyState("조건에 맞는 오답이 없습니다.") }
        items(notes, key = { it.questionId + it.mode }) { n ->
            val q = content.questionById[n.questionId]!!
            val key = n.questionId + n.mode
            SectionCard {
                Column(Modifier.fillMaxWidth().clickable { expanded = if (expanded == key) null else key }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(QuizMode.of(n.mode).label, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                        if (n.isResolved) Pill("해결됨", status.correctContainer, status.correct) else Pill("미해결", status.wrongContainer, status.wrong)
                        Text("오답 ${n.wrongCount}회", style = MaterialTheme.typography.labelLarge)
                    }
                    VSpace(6)
                    Text(q.questionText.replace(BLANK_TOKEN, "( ______ )"), style = MaterialTheme.typography.bodyLarge)
                    VSpace(4)
                    KeyValue("내가 입력한 답", n.lastUserAnswer)
                    KeyValue("정확한 정답", q.blankAnswer)
                    KeyValue("마지막 오답", fmt(n.lastWrongAt))
                    KeyValue("관련 개념", q.conceptIds.joinToString(", ") { "$it ${content.conceptById[it]?.koreanName.orEmpty()}" })
                }
                if (expanded == key) {
                    VSpace(8)
                    Explanation(q, content, onOpenConcept)
                }
                VSpace(6)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onStartQuiz(QuizMode.of(n.mode), QuizScope(ScopeType.QUESTION, q.id)) }, modifier = Modifier.weight(1f)) { Text("다시 풀기") }
                    OutlinedButton(onClick = { onOpenConcept(q.primaryConceptId) }, modifier = Modifier.weight(1f)) { Text("개념 보기") }
                }
            }
        }
    }
}
