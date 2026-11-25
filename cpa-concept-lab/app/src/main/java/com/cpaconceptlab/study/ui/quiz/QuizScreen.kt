package com.cpaconceptlab.study.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.data.local.QuestionEntity
import com.cpaconceptlab.study.data.model.BLANK_TOKEN
import com.cpaconceptlab.study.data.model.INPUT_CHOICE
import com.cpaconceptlab.study.data.model.INPUT_SLOTS
import com.cpaconceptlab.study.data.model.INPUT_TEXT
import com.cpaconceptlab.study.data.repository.ContentSnapshot
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.StandardStatusResolver
import com.cpaconceptlab.study.ui.components.EmptyState
import com.cpaconceptlab.study.ui.components.InfoBlock
import com.cpaconceptlab.study.ui.components.Pill
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.StandardBadges
import com.cpaconceptlab.study.ui.components.VSpace
import com.cpaconceptlab.study.ui.components.pct
import com.cpaconceptlab.study.ui.theme.LocalStatusColors
import com.cpaconceptlab.study.utils.Chosung
import com.cpaconceptlab.study.domain.model.LawApplicability
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.data.local.ConceptEntity
import java.time.LocalDate

private val TYPE_LABEL = mapOf(
    "definition" to "정의", "situation" to "상황 판단", "comparison" to "개념 비교", "exception" to "예외",
    "correction" to "잘못된 설명 수정", "classification" to "분류 판단", "treatment" to "처리방법",
    "formula" to "공식 구성", "calculation" to "계산", "choice" to "4지선다", "matching" to "개념 매칭",
    "order" to "순서 배열", "journal" to "분개 계정 선택",
    "number" to "숫자·기간", "effect" to "법률효과", "procedure" to "절차",
)

@Composable
fun QuizScreen(
    vm: QuizViewModel,
    content: ContentSnapshot?,
    onOpenConcept: (String) -> Unit,
    onFinish: () -> Unit,
    onRestart: () -> Unit,
    onAdvance: () -> Unit = {},
    /** 단원 + 난이도 조건에 문제가 없을 때 '전체 난이도로 풀기' */
    onRetryAllLevels: (() -> Unit)? = null,
    appVersion: String = "",
) {
    val s by vm.state.collectAsState()
    if (s.loading || content == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
        return
    }
    if (s.questions.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            EmptyState(s.emptyReason ?: "출제할 문항이 없습니다.")
            if (s.emptyReason == QuizViewModel.UNIT_EMPTY && onRetryAllLevels != null) {
                Button(onClick = onRetryAllLevels, modifier = Modifier.fillMaxWidth()) { Text("전체 난이도로 풀기") }
                VSpace(6)
                OutlinedButton(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("다른 난이도 선택(돌아가기)") }
            } else {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("돌아가기") }
            }
        }
        return
    }
    if (s.finished) {
        QuizSummary(s, content, onOpenConcept, onFinish, onRestart, onAdvance)
        return
    }
    val q = s.current ?: return
    val status = LocalStatusColors.current
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(q.id) { scroll.scrollTo(0); runCatching { focus.requestFocus() } }
    // 문제마다 새 화이트보드·계산기
    val ink = remember(q.id) { InkState() }
    val calc = remember(q.id) { CalculatorState() }
    var showCalc by remember(q.id) { mutableStateOf(false) }
    val isCalcQuestion = q.calcFormulaId != null || q.questionType == "calculation" || q.questionType == "number" || q.unit == "원"

    Box(Modifier.fillMaxSize().imePadding()) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll, enabled = !ink.enabled).inkLayer(ink).padding(16.dp)) {
        // 진행 표시
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${s.index + 1} / ${s.questions.size}", style = MaterialTheme.typography.titleSmall)
            Text(
                "  ·  ${s.mode.label} 모드 · ${s.scope.type.label}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text("정답 ${s.correctCount}/${s.answeredCount}", style = MaterialTheme.typography.bodySmall)
        }
        VSpace(6)
        LinearProgressIndicator(
            progress = { (s.index + if (s.result != null) 1 else 0).toFloat() / s.questions.size },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            drawStopIndicator = {},
        )
        VSpace(14)

        val concept = content.conceptById[q.primaryConceptId]
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(TYPE_LABEL[q.questionType] ?: q.questionType, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
            Pill(
                com.cpaconceptlab.study.domain.model.Difficulty.entries.drop(1).firstOrNull { q.difficulty in it.min..it.max }?.label ?: "Level ${q.difficulty}",
                MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.isRetry) Pill("다시 풀기", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
            if (s.scope.minLevel >= 4 && s.result == null) ElapsedTimer(q.id)
            if (q.examReferenceNumbers.isNotEmpty()) {
                Pill("출제범위 ${q.examReferenceNumbers.take(3).joinToString(",")}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            StudyToolButtons(ink, isCalcQuestion, { showCalc = true })
        }
        VSpace(10)
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    questionAnnotated(q.questionText, s.result?.let { if (q.inputType == INPUT_TEXT) q.blankAnswer else null }),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                )
                if (s.mode == QuizMode.BASIC && q.inputType == INPUT_TEXT) {
                    VSpace(12)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("초성 힌트  ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Chosung.hint(q.blankAnswer), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.secondary)
                        Text("  (${Chosung.letterCount(q.blankAnswer)}자)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (q.unit != null) {
                    VSpace(6)
                    Text("단위: ${q.unit} (숫자만 입력해도 됩니다)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        VSpace(14)

        val result = s.result
        if (result == null && q.inputType == INPUT_CHOICE) {
            ChoiceInput(q.choices, s.selectedChoice, null, vm::onChoice)
            VSpace(10)
            SubmitRow(s.canSubmit, vm)
        } else if (result == null && q.inputType == INPUT_SLOTS) {
            SlotInput(q.slotLabels, q.choices, s.slotPicks, null, vm::onSlot)
            VSpace(10)
            SubmitRow(s.canSubmit, vm)
        } else if (result == null) {
            OutlinedTextField(
                value = s.input,
                onValueChange = vm::onInput,
                label = { Text("빈칸에 들어갈 답") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.submit() }),
            )
            VSpace(10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.submit(skip = true) }, modifier = Modifier.weight(1f)) { Text("모르겠음") }
                Button(onClick = { vm.submit() }, enabled = s.input.isNotBlank(), modifier = Modifier.weight(2f)) { Text("제출") }
            }
        } else {
            val ok = result.isCorrect
            if (q.inputType == INPUT_CHOICE) {
                ChoiceInput(q.choices, s.selectedChoice, q.blankAnswer) {}
                VSpace(10)
            } else if (q.inputType == INPUT_SLOTS) {
                SlotInput(q.slotLabels, q.choices, s.slotPicks, q.slotAnswers) { _, _ -> }
                VSpace(10)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (ok) status.correctContainer else status.wrongContainer,
                border = BorderStroke(1.dp, if (ok) status.correct else status.wrong),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(if (ok) "정답입니다" else if (result.skipped) "정답을 확인하세요" else "오답입니다", style = MaterialTheme.typography.titleMedium, color = if (ok) status.correct else status.wrong)
                    if (!ok && q.inputType == INPUT_TEXT) Text("내 답: ${result.userAnswer}", style = MaterialTheme.typography.bodyMedium)
                }
            }
            VSpace(12)
            Explanation(q, content, onOpenConcept)
            VSpace(6)
            var reporting by remember(q.id) { mutableStateOf(false) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onOpenConcept(q.primaryConceptId) }, modifier = Modifier.weight(1f)) { Text("관련 개념 자세히 보기") }
                TextButton(onClick = { reporting = true }) { Text("오류 신고") }
            }
            if (reporting) {
                com.cpaconceptlab.study.ui.common.ErrorReportDialog(
                    q.primaryConceptId, content.conceptById[q.primaryConceptId]?.koreanName, q.id, content.bundledVersion, appVersion,
                    onDismiss = { reporting = false },
                )
            }
            VSpace(12)
            Button(onClick = vm::next, modifier = Modifier.fillMaxWidth()) {
                Text(if (s.index + 1 >= s.questions.size) "결과 보기" else "다음 문제")
            }
        }
        VSpace(if (ink.enabled) 180 else 24)   // 필기 막대에 가리지 않도록 여백
    }
    if (ink.enabled) {
        InkToolbar(ink, Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp))
    }
    }
    if (showCalc) {
        CalculatorDialog(
            calc,
            canInsert = s.result == null && q.inputType == INPUT_TEXT,
            onInsert = vm::onInput,
            onDismiss = { showCalc = false },
        )
    }
}

/** 빈칸 강조 표시. 정답 확인 후에는 빈칸 자리에 정답을 채워 보여준다. */
@Composable
private fun questionAnnotated(text: String, filled: String?) = buildAnnotatedString {
    val i = text.indexOf(BLANK_TOKEN)
    if (i < 0) { append(text); return@buildAnnotatedString }
    append(text.substring(0, i))
    withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, background = MaterialTheme.colorScheme.secondaryContainer)) {
        append(if (filled != null) " $filled " else " ______ ")
    }
    append(text.substring(i + BLANK_TOKEN.length))
}

/** 기업법 해설: 조문 근거, 정답-조문 대조 여부, 적용 기준 라벨 */
@Composable
private fun LawBasisBlock(q: QuestionEntity, concept: ConceptEntity, content: ContentSnapshot) {
    val verify = when (q.answerVerification) {
        "STATUTE_TEXT_MATCHED" -> "정답이 근거 조문 원문에 그대로 있음을 자동 대조함"
        "INTERPRETIVE" -> "해석·선택형 문항(조문 문언 직접 대조 대상 아님)"
        else -> null
    }
    val applic = LawApplicability.of(concept.examApplicability)?.label
    InfoBlock("[조문 근거]", listOfNotNull(
        q.standardReference.takeIf { it.isNotBlank() },
        applic,
        verify,
        concept.lawVersion?.let { "법령: ${concept.lawName.orEmpty()} · $it" },
    ).joinToString("\n"))
    val refs = q.standardReference.split(",").mapNotNull { ContentSnapshot.articleKey(it.trim()) }.distinct()
    refs.mapNotNull { content.lawArticleById[it] }.take(2).forEach { a ->
        InfoBlock("[조문 원문] ${a.articleId}(${a.title})", a.text.take(600) + if (a.text.length > 600) " …" else "")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Explanation(q: QuestionEntity, content: ContentSnapshot, onOpenConcept: (String) -> Unit) {
    val concept = content.conceptById[q.primaryConceptId]
    SectionCard {
        InfoBlock("[정답]", buildString {
            append(q.blankAnswer)
            if (q.unit != null) append(" (${q.unit})")
            val alts = q.acceptedAnswers.filter { it != q.blankAnswer }
            if (alts.isNotEmpty()) append("\n인정 표기: ${alts.joinToString(", ")}")
        }, emphasize = true)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        InfoBlock("[핵심 개념] ${concept?.koreanName.orEmpty()}", concept?.definition)
        InfoBlock("[왜 이 답이 정답인지]", q.explanation)
        InfoBlock("[헷갈리기 쉬운 개념]", q.confusion ?: concept?.comparison)
        InfoBlock("[자주 틀리는 함정]", concept?.commonMistakes)
        InfoBlock("[CPA 출제 포인트]", concept?.examTips)
        if (q.calculation != null || (q.questionType == "calculation" && concept?.formula != null)) {
            InfoBlock("[계산문제] 공식·대입·계산 순서·결과", listOfNotNull(concept?.formula?.let { "공식: $it" }, q.calculation).joinToString("\n"))
        }
        if (concept != null && concept.articleRefs.isNotEmpty()) {
            LawBasisBlock(q, concept, content)
        }
        val status = StandardStatusResolver.resolve(q.effectiveFrom, q.effectiveTo, q.isHistorical, LocalDate.now())
        VSpace(4)
        StandardBadges(status, q.requiresReview || (concept?.requiresReview == true), q.standardReference, q.revisionNote ?: concept?.revisionNote)
        VSpace(10)
        Text("[관련 개념 복습]", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        VSpace(4)
        val ids = (q.conceptIds + (concept?.relatedConceptIds ?: emptyList())).distinct().take(8)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ids.forEach { id ->
                content.conceptById[id]?.let { c -> AssistChip(onClick = { onOpenConcept(id) }, label = { Text("${c.id} ${c.koreanName}") }) }
            }
        }
    }
}

@Composable
private fun QuizSummary(
    s: QuizUiState,
    content: ContentSnapshot,
    onOpenConcept: (String) -> Unit,
    onFinish: () -> Unit,
    onRestart: () -> Unit,
    onAdvance: () -> Unit,
) {
    val status = LocalStatusColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        if (s.suggestAdvanced) {
            SectionCard(title = "기본 단계 통과") {
                Text("이번 세트 정답률이 80% 이상입니다. 같은 범위를 힌트 없는 심화 모드로 이어서 풀어 보세요.", style = MaterialTheme.typography.bodyMedium)
                VSpace(8)
                Button(onClick = onAdvance, modifier = Modifier.fillMaxWidth()) { Text("심화 모드로 이동") }
            }
            VSpace(12)
        }
        if (s.scope.type == ScopeType.WEIGHTED) {
            SectionCard(title = "CPA 실전 비중 모의 점수") {
                val score = s.correctCount * 100.0 / s.questions.size.coerceAtLeast(1)
                Text("${"%.1f".format(score)}점 / 100점", style = MaterialTheme.typography.headlineSmall)
                VSpace(4)
                s.history.groupBy { it.first.examArea }.forEach { (area, rows) ->
                    val name = content.examAreas.firstOrNull { it.id == area }?.name ?: "기타"
                    Text("· $name: ${rows.count { it.second.isCorrect }} / ${rows.size}", style = MaterialTheme.typography.bodySmall)
                }
            }
            VSpace(12)
        }
        SectionCard(title = "학습 결과") {
            Text("${s.correctCount} / ${s.answeredCount} 정답 (${pct(if (s.answeredCount == 0) null else s.correctCount.toDouble() / s.answeredCount)})", style = MaterialTheme.typography.headlineSmall)
            VSpace(4)
            Text("틀린 문제는 오답노트에 자동 저장되었고, 복습 일정이 갱신되었습니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VSpace(12)
        SectionCard(title = "문항별 결과") {
            s.history.forEach { (q, r) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(if (r.isCorrect) "○" else "✕", color = if (r.isCorrect) status.correct else status.wrong, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(end = 10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (q.inputType == INPUT_TEXT) q.questionText.replace(BLANK_TOKEN, "( ${q.blankAnswer} )") else "${q.questionText}\n정답: ${q.blankAnswer}",
                            style = MaterialTheme.typography.bodyMedium, maxLines = 4,
                        )
                        if (!r.isCorrect) Text("내 답: ${r.userAnswer}", style = MaterialTheme.typography.bodySmall, color = status.wrong)
                    }
                    TextButton(onClick = { onOpenConcept(q.primaryConceptId) }) { Text(q.primaryConceptId) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        VSpace(12)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) { Text("홈으로") }
            Button(onClick = onRestart, modifier = Modifier.weight(1f)) { Text("새 세트 풀기") }
        }
        VSpace(24)
    }
}

@Composable
private fun SubmitRow(enabled: Boolean, vm: QuizViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.submit(skip = true) }, modifier = Modifier.weight(1f)) { Text("모르겠음") }
        Button(onClick = { vm.submit() }, enabled = enabled, modifier = Modifier.weight(2f)) { Text("제출") }
    }
}

/** 4지선다 보기. reveal 이 주어지면 정답 보기와 선택한 보기를 색으로 표시한다. */
@Composable
private fun ChoiceInput(options: List<String>, selected: String?, reveal: String?, onPick: (String) -> Unit) {
    val status = LocalStatusColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            val isAnswer = reveal != null && o == reveal
            val isWrongPick = reveal != null && o == selected && o != reveal
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    isAnswer -> status.correctContainer
                    isWrongPick -> status.wrongContainer
                    o == selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                },
                border = BorderStroke(1.dp, if (o == selected || isAnswer) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth().clickable(enabled = reveal == null) { onPick(o) },
            ) {
                Text("${'①' + i}  $o", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(14.dp))
            }
        }
    }
}

/** 슬롯형(매칭·분류·순서·분개 계정): 각 칸마다 보기 칩 중 하나를 고른다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SlotInput(labels: List<String>, options: List<String>, picks: List<String?>, reveal: List<String>?, onPick: (Int, String) -> Unit) {
    val status = LocalStatusColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        labels.forEachIndexed { i, label ->
            val pick = picks.getOrNull(i)
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                    VSpace(6)
                    if (reveal == null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            options.forEach { o -> FilterChip(selected = pick == o, onClick = { onPick(i, o) }, label = { Text(o) }) }
                        }
                    } else {
                        val answer = reveal.getOrNull(i).orEmpty()
                        val ok = pick == answer
                        Text("정답: $answer", style = MaterialTheme.typography.bodyMedium, color = status.correct)
                        if (!ok) Text("내 선택: ${pick ?: "(선택 안 함)"}", style = MaterialTheme.typography.bodySmall, color = status.wrong)
                    }
                }
            }
        }
    }
}


/** 실전 난이도: 문제별 경과 시간 표시(측정만 하고 제한하지 않는다) */
@Composable
private fun ElapsedTimer(key: String) {
    var sec by remember(key) { mutableStateOf(0) }
    LaunchedEffect(key) { while (true) { kotlinx.coroutines.delay(1000); sec++ } }
    Pill("⏱ ${sec / 60}:${"%02d".format(sec % 60)}", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
}
