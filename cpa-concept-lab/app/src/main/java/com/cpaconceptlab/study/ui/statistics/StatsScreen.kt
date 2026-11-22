package com.cpaconceptlab.study.ui.statistics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.domain.model.MasteryLevel
import com.cpaconceptlab.study.ui.DashboardState
import com.cpaconceptlab.study.ui.components.BarChart
import com.cpaconceptlab.study.ui.components.ChapterStatusPill
import com.cpaconceptlab.study.ui.components.EmptyState
import com.cpaconceptlab.study.ui.components.KeyValue
import com.cpaconceptlab.study.ui.components.ProgressLine
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.StatTile
import com.cpaconceptlab.study.ui.components.VSpace
import com.cpaconceptlab.study.ui.components.pct
import com.cpaconceptlab.study.ui.theme.LocalStatusColors

@Composable
fun StatsScreen(dash: DashboardState, onOpenConcept: (String) -> Unit) {
    val ov = dash.overview
    val status = LocalStatusColors.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionCard(title = "학습 요약") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("학습 일수", "${ov.studyDays}일", Modifier.weight(1f))
                StatTile("연속 학습일", "${ov.streak}일", Modifier.weight(1f))
                StatTile("누적 풀이", "${ov.totalAttempts}", Modifier.weight(1f))
            }
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("전체 정답률", pct(ov.overallAccuracy), Modifier.weight(1f))
                StatTile("기본 정답률", pct(ov.basicAccuracy), Modifier.weight(1f))
                StatTile("심화 정답률", pct(ov.advancedAccuracy), Modifier.weight(1f))
            }
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("미학습 개념", "${ov.unstudiedConcepts}", Modifier.weight(1f))
                StatTile("남은 복습문제", "${ov.dueQuestions}", Modifier.weight(1f), accent = if (ov.dueQuestions > 0) status.warn else null)
                StatTile("미해결 오답", "${ov.unresolvedWrong}", Modifier.weight(1f))
            }
        }
        VSpace(12)
        SectionCard(title = "최근 7일 학습량") {
            BarChart(ov.last7.map { it.solved }, ov.last7.map { "${it.day.monthValue}/${it.day.dayOfMonth}" })
            VSpace(8)
            KeyValue("최근 7일 풀이", "${ov.last7Total}문항")
            KeyValue("최근 30일 풀이", "${ov.last30Total}문항")
        }
        VSpace(12)
        SectionCard(title = "단원별 숙달도") {
            ov.chapters.forEach { ch ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("${ch.code}. ${ch.title}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    ChapterStatusPill(ch.status)
                }
                ProgressLine(
                    "숙달도 ${pct(ch.masteryRatio)} · 정답률 ${pct(ch.accuracy)}",
                    ch.studiedConcepts, ch.conceptCount,
                    detail = "개념 ${ch.studiedConcepts}/${ch.conceptCount} · 문항 ${ch.solvedQuestions}/${ch.questionCount}",
                )
            }
        }
        VSpace(12)
        SectionCard(title = "개념 숙달 단계 분포") {
            MasteryLevel.entries.forEach { lvl ->
                ProgressLine(lvl.label, ov.masteryCounts[lvl] ?: 0, ov.totalConcepts)
            }
        }
        VSpace(12)
        SectionCard(title = "오답이 가장 많은 개념") {
            if (ov.mostWrong.isEmpty()) EmptyState("아직 오답 기록이 없습니다.")
            ov.mostWrong.forEach { w ->
                Row(Modifier.fillMaxWidth().clickable { onOpenConcept(w.conceptId) }.padding(vertical = 6.dp)) {
                    Text(w.conceptId, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp))
                    Text(w.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("오답 ${w.wrong} / 풀이 ${w.attempts}", style = MaterialTheme.typography.bodySmall, color = status.wrong)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        VSpace(12)
        SectionCard(title = "개념별 취약도(오답률)") {
            if (ov.weakest.isEmpty()) EmptyState("2회 이상 풀이한 개념 중 오답이 있으면 표시됩니다.")
            ov.weakest.forEach { w ->
                Row(Modifier.fillMaxWidth().clickable { onOpenConcept(w.conceptId) }.padding(vertical = 6.dp)) {
                    Text(w.conceptId, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(44.dp))
                    Text(w.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("오답률 ${pct(w.wrongRate)}", style = MaterialTheme.typography.bodySmall, color = status.wrong)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        VSpace(24)
    }
}
