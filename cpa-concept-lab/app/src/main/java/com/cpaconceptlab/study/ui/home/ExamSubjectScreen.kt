package com.cpaconceptlab.study.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.domain.usecase.ExamSubjectProgress
import com.cpaconceptlab.study.ui.DashboardState
import com.cpaconceptlab.study.ui.components.ProgressLine
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.VSpace
import com.cpaconceptlab.study.ui.components.pct

/** 공식 시험과목 상세: 세부 콘텐츠팩(·파트)별 개념 진도, 정답률, 문제 학습률, 콘텐츠 구축률 */
@Composable
fun ExamSubjectScreen(dash: DashboardState, examId: String, onOpenSubject: (String) -> Unit) {
    val d = remember(dash, examId) { ExamSubjectProgress.detail(examId, dash.content, dash.user) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ExamSubjectRow(d.subject, onClick = null) }
        items(d.packs, key = { it.stat.id }) { p ->
            val st = p.stat
            SectionCard(modifier = Modifier.clickable(enabled = p.active) { onOpenSubject(st.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(st.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            !st.hasContent -> "콘텐츠 없음"
                            p.active -> "학습 가능 ›"
                            else -> "준비 중"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (p.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                st.buildRatio?.let {
                    Text("콘텐츠 구축 ${pct(it)} · 출제범위 목차 ${st.curriculumCovered}/${st.curriculumTotal} · 학습 가능한 개념 ${st.totalConcepts}개",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (st.hasContent) {
                    VSpace(6)
                    ProgressLine("개념 진도", st.completedConcepts, st.totalConcepts)
                    ProgressLine("문제 학습률(1회 이상 풀이)", st.solvedQuestions, st.totalQuestions)
                    Text("정답률 ${pct(st.accuracy)} · 복습 필요 개념 ${st.reviewNeeded}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                p.parts.forEach { part ->
                    VSpace(6)
                    Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
                        ProgressLine(part.name, part.completedConcepts, part.totalConcepts,
                            detail = if (part.hasContent) "${part.completedConcepts}/${part.totalConcepts} · 정답률 ${pct(part.accuracy)}" else "콘텐츠 없음")
                    }
                }
            }
        }
    }
}
