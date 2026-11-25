package com.cpaconceptlab.study.ui.concepts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.data.local.ConceptEntity
import com.cpaconceptlab.study.data.model.DetailSectionDto
import com.cpaconceptlab.study.ui.components.VSpace

/** 간단 요약: 기존 정의(원문 보존) + 핵심·공식·시험 포인트 요지 */
@Composable
fun SummaryTab(c: ConceptEntity) {
    val lines = (c.shortSummary ?: c.definition).split("\n").filter { it.isNotBlank() }
    Column(Modifier.testTag("summary-tab")) {
        lines.forEachIndexed { i, line ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text(if (i == 0) "■" else "•", modifier = Modifier.width(18.dp), color = MaterialTheme.colorScheme.primary)
                Text(line, style = if (i == 0) MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium) else MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** 상세 학습: 섹션별 접기·펼치기, 공식 복사, 분개 표(좁은 화면에서는 가로 스크롤) */
@Composable
fun DetailTab(c: ConceptEntity, sections: List<DetailSectionDto>) {
    Column(Modifier.testTag("detail-tab")) {
        if (sections.isEmpty()) {
            Text("상세 학습 자료를 불러오지 못했습니다. 간단 요약을 확인하세요.", style = MaterialTheme.typography.bodySmall)
            return
        }
        sections.forEach { s -> DetailSectionView(c.id, s) }
    }
}

@Composable
private fun DetailSectionView(conceptId: String, s: DetailSectionDto) {
    var open by rememberSaveable(conceptId, s.sectionId) { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(s.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            if (s.source == "AUTHORED_V7") Text("예제 ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            Text(if (open) "▲" else "▼", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            if (s.type != "FORMULA") Text(s.body, style = MaterialTheme.typography.bodyMedium)
            if (s.formulas.isNotEmpty()) {
                VSpace(6)
                s.formulas.forEach { FormulaBox(it) }
            }
            if (s.journal.isNotEmpty()) {
                VSpace(6)
                JournalTable(s.journal)
            }
        }
        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 공식: 고정폭 글꼴, 가로 스크롤(줄이 길어도 깨지지 않게), 복사 버튼 */
@Composable
fun FormulaBox(formula: String) {
    val clip = LocalClipboardManager.current
    var copied by rememberSaveable(formula) { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                Text(formula, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, softWrap = false)
            }
            TextButton(onClick = { clip.setText(AnnotatedString(formula)); copied = true }) { Text(if (copied) "복사됨" else "복사") }
        }
    }
}

/** 분개: 차변 | 대변 | 금액. 계정이 길면 가로로 스크롤한다. */
@Composable
fun JournalTable(lines: List<com.cpaconceptlab.study.data.model.JournalLineDto>) {
    Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(8.dp)) {
            Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("(차) 차변", Modifier.width(210.dp), style = MaterialTheme.typography.labelMedium)
                Text("(대) 대변", Modifier.width(210.dp), style = MaterialTheme.typography.labelMedium)
                Text("금액", Modifier.width(90.dp), style = MaterialTheme.typography.labelMedium)
            }
            lines.forEach { j ->
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(j.dr.replace(" / ", "\n"), Modifier.width(210.dp), style = MaterialTheme.typography.bodySmall)
                    Text(j.cr.replace(" / ", "\n"), Modifier.width(210.dp), style = MaterialTheme.typography.bodySmall)
                    Text(j.amount, Modifier.width(90.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}
