package com.cpaconceptlab.study.ui.quiz

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.data.repository.ContentSnapshot
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.domain.model.QuestionKind
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.VSpace

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizSetupScreen(
    content: ContentSnapshot?,
    initialMode: QuizMode,
    initialScope: QuizScope,
    defaultSize: Int,
    onStart: (QuizMode, QuizScope, Int) -> Unit,
) {
    if (content == null) return
    var mode by remember { mutableStateOf(initialMode) }
    var type by remember { mutableStateOf(initialScope.type.takeIf { it != ScopeType.QUESTION && it != ScopeType.EXAM } ?: ScopeType.ALL) }
    var targetId by remember { mutableStateOf(initialScope.id) }
    var size by remember { mutableFloatStateOf(defaultSize.toFloat()) }
    var query by remember { mutableStateOf("") }
    var levels by remember { mutableStateOf(initialScope.minLevel.toFloat()..initialScope.maxLevel.toFloat()) }
    var kind by remember { mutableStateOf(initialScope.kind) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionCard(title = "풀이 모드") {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                QuizMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, QuizMode.entries.size),
                    ) { Text("${m.label} · ${m.description}") }
                }
            }
            VSpace(6)
            Text("기본·심화 모드의 진도와 정답률은 따로 기록됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VSpace(12)
        SectionCard(title = "출제 범위") {
            val selectable = listOf(
                ScopeType.ALL, ScopeType.SUBJECT, ScopeType.WEIGHTED, ScopeType.PART, ScopeType.CHAPTER, ScopeType.SECTION, ScopeType.CONCEPT,
                ScopeType.UNSTUDIED, ScopeType.WRONG, ScopeType.WEAK, ScopeType.RECENT, ScopeType.DUE, ScopeType.MOCK,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                selectable.forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t; if (t != initialScope.type) targetId = null }, label = { Text(t.label) })
                }
            }
            VSpace(8)
            when (type) {
                ScopeType.SUBJECT -> {
                    Hint("선택한 과목의 모든 개념을 한 번씩 출제한 뒤에 반복하는 균등학습입니다.")
                    content.subjects.filter { s -> s.isActive && content.chapters.any { it.subjectId == s.id } }.forEach { sub ->
                        ChoiceRow(sub.name, targetId == sub.id) { targetId = sub.id }
                    }
                }
                ScopeType.WEIGHTED -> {
                    val weightedSubjects = content.examAreas.map { it.subjectId }.distinct()
                    val chosen = targetId?.takeIf { it in weightedSubjects } ?: QuizViewModel.LAW_SUBJECT
                    weightedSubjects.forEach { sid ->
                        ChoiceRow(content.subjects.firstOrNull { it.id == sid }?.name ?: sid, chosen == sid) { targetId = sid }
                    }
                    val areas = content.examAreas.filter { it.subjectId == chosen }
                        .sortedBy { QuizViewModel.AREA_ORDER.indexOf(it.id) }
                    Hint(
                        "${content.subjects.firstOrNull { it.id == chosen }?.name ?: chosen} 1차 시험(금융위원회 공고 출제비중)에 맞춘 ${areas.sumOf { it.mockQuestions }}문항 모의고사입니다. " +
                            "문항당 2.5점, 100점 만점으로 채점합니다. 아래 난이도·형식 필터와 문항 수 설정은 적용되지 않습니다.",
                    )
                    areas.forEach { a -> Text("· ${a.name}: ${a.mockQuestions}문항 (공고 ${a.minPercent}~${a.maxPercent}%)", style = MaterialTheme.typography.bodySmall) }
                }
                ScopeType.PART -> content.chapters.map { it.partTitle }.filter { it.isNotBlank() }.distinct().forEach { part ->
                    ChoiceRow(part, targetId == part) { targetId = part }
                }
                ScopeType.WEAK -> Hint("정답률 60% 미만(시도 3회 이상)이거나 미해결 오답이 2개 이상인 개념을 모아 출제합니다.")
                ScopeType.RECENT -> Hint("가장 최근 콘텐츠 업데이트(v6: 세법개론 — 국세기본법·법인세법·소득세법·부가가치세법·상증법·지방세법·조특법)에서 추가된 개념을 출제합니다.")
                ScopeType.CHAPTER -> content.chapters.forEach { ch ->
                    ChoiceRow("${ch.code}. ${ch.title}", targetId == ch.id) { targetId = ch.id }
                }
                ScopeType.SECTION -> content.chapters.forEach { ch ->
                    Text("${ch.code}. ${ch.title}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    content.sectionsOf(ch.id).forEach { sec -> ChoiceRow(sec.title, targetId == sec.id) { targetId = sec.id } }
                }
                ScopeType.CONCEPT -> {
                    OutlinedTextField(query, { query = it }, label = { Text("개념명 또는 코드 검색 (예: D04, 공정가치)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val list = content.concepts.filter { query.isNotBlank() && (it.id.contains(query, true) || it.koreanName.contains(query)) }.take(30)
                    list.forEach { c -> ChoiceRow("${c.id} ${c.koreanName}", targetId == c.id) { targetId = c.id } }
                    if (targetId != null && list.none { it.id == targetId }) {
                        Text("선택됨: $targetId ${content.conceptById[targetId]?.koreanName.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                ScopeType.EXAM -> content.examScopes.forEach { e -> ChoiceRow("${e.number}번 · ${e.title}", targetId == e.number) { targetId = e.number } }
                ScopeType.WRONG -> Hint("오답노트의 미해결 문항(선택한 모드)을 오답 횟수가 많은 순서로 출제합니다.")
                ScopeType.DUE -> Hint("복습 예정일이 지난 문항을 오래 연체된 순서로 출제합니다.")
                ScopeType.UNSTUDIED -> Hint("아직 문제를 풀지 않은 개념만 출제합니다.")
                ScopeType.MOCK -> Hint("대단원 비중을 유지한 무작위 모의테스트입니다.")
                else -> Hint("모든 개념을 한 번 이상 학습하기 전까지 같은 개념이 반복되지 않도록 균등 출제합니다.")
            }
        }
        VSpace(12)
        SectionCard(title = "난이도: Level ${levels.start.toInt()} ~ ${levels.endInclusive.toInt()}") {
            RangeSlider(value = levels, onValueChange = { levels = it }, valueRange = 1f..5f, steps = 3)
            Text(
                "1 정의·용어 / 2 개념 구별 / 3 사례 적용 / 4 계산 / 5 개념 연결 응용",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VSpace(12)
        SectionCard(title = "문항 형식") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QuestionKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
            }
        }
        VSpace(12)
        SectionCard(title = "문항 수: ${size.toInt()}개") {
            Slider(value = size, onValueChange = { size = it }, valueRange = 5f..50f, steps = 8)
        }
        VSpace(16)
        val needsTarget = type in setOf(ScopeType.PART, ScopeType.CHAPTER, ScopeType.SECTION, ScopeType.CONCEPT, ScopeType.EXAM, ScopeType.SUBJECT)
        Button(
            onClick = {
                val weighted = targetId?.takeIf { id -> content.examAreas.any { it.subjectId == id } } ?: QuizViewModel.LAW_SUBJECT
                val scope = if (type == ScopeType.WEIGHTED) QuizScope(ScopeType.WEIGHTED, weighted) else QuizScope(
                    type, if (needsTarget) targetId else null,
                    levels.start.toInt(), levels.endInclusive.toInt(), kind,
                )
                onStart(mode, scope, size.toInt())
            },
            enabled = !needsTarget || targetId != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("시작하기") }
        VSpace(24)
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
