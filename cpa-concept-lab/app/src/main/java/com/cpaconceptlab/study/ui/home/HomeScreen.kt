package com.cpaconceptlab.study.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.data.repository.UserSettings
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.QuizScope
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.ui.DashboardState
import com.cpaconceptlab.study.ui.components.ProgressLine
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.StatTile
import com.cpaconceptlab.study.ui.components.VSpace
import com.cpaconceptlab.study.ui.components.pct
import com.cpaconceptlab.study.ui.theme.LocalStatusColors

@Composable
fun HomeScreen(
    dash: DashboardState,
    settings: UserSettings,
    onStartQuiz: (QuizMode, QuizScope) -> Unit,
    onOpenSetup: () -> Unit,
    onOpenSubject: (String) -> Unit,
    onOpenExamScopes: () -> Unit,
) {
    val ov = dash.overview
    val status = LocalStatusColors.current
    val recommended = maxOf(settings.dailyGoal, ov.dueQuestions.coerceAtMost(settings.dailyGoal * 2))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // 오늘의 학습
        SectionCard(title = "오늘의 학습") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("오늘 복습할 문제", "${ov.dueQuestions}", Modifier.weight(1f), accent = if (ov.dueQuestions > 0) status.warn else null)
                StatTile("미학습 개념", "${ov.unstudiedConcepts}", Modifier.weight(1f))
            }
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("오늘 푼 문제", "${ov.todaySolved}", Modifier.weight(1f), sub = "권장 ${recommended}문항")
                StatTile("연속 학습일", "${ov.streak}일", Modifier.weight(1f))
            }
            VSpace(10)
            ProgressLine("오늘 목표 달성", ov.todaySolved.coerceAtMost(recommended), recommended)
        }
        VSpace(12)

        // 빠른 시작
        SectionCard(title = "빠른 시작") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.ALL)) }, modifier = Modifier.weight(1f)) { Text("기본 문제") }
                Button(onClick = { onStartQuiz(QuizMode.ADVANCED, QuizScope(ScopeType.ALL)) }, modifier = Modifier.weight(1f)) { Text("심화 문제") }
            }
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { onStartQuiz(QuizMode.BASIC, QuizScope(ScopeType.WRONG)) }, modifier = Modifier.weight(1f)) { Text("오답 복습 ${ov.unresolvedWrong}") }
                FilledTonalButton(onClick = { onStartQuiz(QuizMode.of(settings.lastMode), QuizScope(ScopeType.DUE)) }, modifier = Modifier.weight(1f)) { Text("오늘의 복습") }
            }
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onStartQuiz(QuizMode.of(settings.lastMode), QuizScope.decode(settings.lastScope)) }, modifier = Modifier.weight(1f)) { Text("이어서 공부하기") }
                OutlinedButton(onClick = onOpenSetup, modifier = Modifier.weight(1f)) { Text("범위 선택") }
            }
        }
        VSpace(12)

        // 전체 학습현황
        SectionCard(title = "전체 학습현황") {
            ProgressLine("학습한 개념", ov.studiedConcepts, ov.totalConcepts)
            ProgressLine("풀이한 문제(기본·심화 중 1회 이상)", ov.solvedQuestions, ov.totalQuestions)
            ProgressLine("기본 모드 진도", ov.solvedBasic, ov.totalQuestions)
            ProgressLine("심화 모드 진도", ov.solvedAdvanced, ov.totalQuestions)
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("기본 정답률", pct(ov.basicAccuracy), Modifier.weight(1f))
                StatTile("심화 정답률", pct(ov.advancedAccuracy), Modifier.weight(1f))
                StatTile("복습 필요 개념", "${ov.reviewNeededConcepts}", Modifier.weight(1f))
            }
            VSpace(6)
            Text(
                "총 개념 ${ov.totalConcepts}개 · 전체 문제 ${ov.totalQuestions}개(각 문제 기본·심화 2가지 방식) · 콘텐츠 v${dash.content.bundledVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        VSpace(12)

        // 과목
        SectionCard(title = "과목") {
            dash.content.subjects.sortedBy { it.sortOrder }.forEach { sub ->
                val concepts = dash.content.concepts.count { it.subjectId == sub.id }
                val active = sub.isActive && concepts > 0
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(enabled = active) { onOpenSubject(sub.id) },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, style = MaterialTheme.typography.titleSmall)
                            Text(if (active) sub.description else "콘텐츠 팩 추가 시 학습 가능", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (active) {
                            val studied = dash.content.concepts.count { it.subjectId == sub.id && ov.masteryByConcept[it.id]?.name != "NOT_STARTED" }
                            Text("$studied/$concepts", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            OutlinedButton(onClick = onOpenExamScopes, modifier = Modifier.fillMaxWidth()) { Text("기출범위별 학습현황 (01~46)") }
        }
        VSpace(24)
    }
}
