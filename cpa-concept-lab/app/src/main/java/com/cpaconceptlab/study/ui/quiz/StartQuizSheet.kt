package com.cpaconceptlab.study.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.data.repository.ContentSnapshot
import com.cpaconceptlab.study.data.repository.UserSnapshot
import com.cpaconceptlab.study.domain.model.Difficulty
import com.cpaconceptlab.study.domain.model.QuestionKind
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.ui.components.VSpace

/** 단원별 문제풀이에서 고를 수 있는 유형(OX 없음) */
val UNIT_KINDS = listOf(
    QuestionKind.ALL, QuestionKind.CHOSUNG, QuestionKind.PLAIN, QuestionKind.SHORT, QuestionKind.CALC,
    QuestionKind.CHOICE, QuestionKind.MATCHING, QuestionKind.CLASSIFY, QuestionKind.ORDER,
)

/** 유형이 모드를 정하는 경우: 초성 빈칸 → 기본(초성 힌트), 일반 빈칸 → 심화(힌트 없음) */
fun modeFor(kind: QuestionKind, chosen: QuizMode): QuizMode = when (kind) {
    QuestionKind.CHOSUNG -> QuizMode.BASIC
    QuestionKind.PLAIN -> QuizMode.ADVANCED
    else -> chosen
}

/**
 * 단원(파트·대단원·중단원·개념) 문제 풀기 시트.
 * 선택한 범위 안에서만 출제하고, 난이도·유형이 함께 적용된다. 해당 조건의 문항 수를 미리 보여 준다.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StartQuizSheet(
    title: String,
    baseScope: QuizScope,
    content: ContentSnapshot,
    initialMode: QuizMode,
    initialDifficulty: Difficulty,
    initialKind: QuestionKind,
    onDismiss: () -> Unit,
    onStart: (QuizMode, QuizScope, Difficulty, QuestionKind) -> Unit,
) {
    var mode by remember { mutableStateOf(initialMode) }
    var diff by remember { mutableStateOf(initialDifficulty) }
    var kind by remember { mutableStateOf(if (initialKind in UNIT_KINDS) initialKind else QuestionKind.ALL) }
    val scope = baseScope.copy(minLevel = diff.min, maxLevel = diff.max, kind = kind)
    val count = remember(scope) { QuizViewModel.candidatesFor(scope, content, UserSnapshot.EMPTY, System.currentTimeMillis()).size }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("start-quiz-sheet")) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text("선택한 범위의 문제만 출제합니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(10)
            Text("난이도", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Difficulty.entries.forEach { d -> FilterChip(selected = diff == d, onClick = { diff = d }, label = { Text(d.label) }) }
            }
            Text(diff.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(10)
            Text("문제 유형", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                UNIT_KINDS.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
            }
            VSpace(10)
            Text("풀이 모드", style = MaterialTheme.typography.titleSmall)
            val forced = kind == QuestionKind.CHOSUNG || kind == QuestionKind.PLAIN
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                QuizMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = modeFor(kind, mode) == m, enabled = !forced, onClick = { mode = m },
                        shape = SegmentedButtonDefaults.itemShape(i, QuizMode.entries.size),
                    ) { Text(if (m == QuizMode.BASIC) "기본(초성 힌트)" else "심화(힌트 없음)") }
                }
            }
            if (forced) Text("'${kind.label}' 유형은 풀이 모드가 자동으로 정해집니다.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(12)
            Text(if (count == 0) QuizViewModel.UNIT_EMPTY else "출제 가능한 문제 ${count}개",
                style = MaterialTheme.typography.bodyMedium,
                color = if (count == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("start-quiz-count"))
            if (count == 0 && diff != Difficulty.ALL) {
                androidx.compose.material3.TextButton(onClick = { diff = Difficulty.ALL }) { Text("전체 난이도로 바꾸기") }
            }
            VSpace(6)
            Button(
                onClick = { onStart(modeFor(kind, mode), scope, diff, kind) },
                enabled = count > 0,
                modifier = Modifier.fillMaxWidth().testTag("start-quiz-button"),
            ) { Text("문제풀이 시작") }
        }
    }
}
