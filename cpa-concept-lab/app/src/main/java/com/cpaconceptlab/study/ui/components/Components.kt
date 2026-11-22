package com.cpaconceptlab.study.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.domain.model.ChapterStatus
import com.cpaconceptlab.study.domain.model.MasteryLevel
import com.cpaconceptlab.study.domain.model.StandardStatus
import com.cpaconceptlab.study.ui.theme.LocalStatusColors
import java.util.Locale

fun pct(v: Double?): String = if (v == null) "–" else String.format(Locale.KOREA, "%.0f%%", v * 100)
fun ratioText(a: Int, b: Int): String = "$a / $b"

@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(contentPadding)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    trailing?.invoke(this)
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, accent: Color? = null) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent ?: MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
fun TileRow(vararg tiles: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { tiles.forEach { it() } }
}

@Composable
fun ProgressLine(label: String, current: Int, total: Int, modifier: Modifier = Modifier, detail: String? = null) {
    val ratio = if (total == 0) 0f else current.toFloat() / total
    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail ?: "${ratioText(current, total)} · ${pct(ratio.toDouble())}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) { Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1) }
}

@Composable
fun MasteryPill(level: MasteryLevel) {
    val s = LocalStatusColors.current
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (level) {
        MasteryLevel.NOT_STARTED -> cs.surfaceContainerHighest to cs.onSurfaceVariant
        MasteryLevel.STARTED, MasteryLevel.BASIC_PRACTICING, MasteryLevel.ADVANCED_PRACTICING -> cs.secondaryContainer to cs.onSecondaryContainer
        MasteryLevel.REVIEW_NEEDED -> s.warnContainer to s.warn
        MasteryLevel.BASIC_MASTERED, MasteryLevel.ADVANCED_MASTERED, MasteryLevel.FULL_MASTERY -> s.correctContainer to s.correct
    }
    Pill(level.label, bg, fg)
}

@Composable
fun ChapterStatusPill(status: ChapterStatus) {
    val s = LocalStatusColors.current
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (status) {
        ChapterStatus.NOT_STARTED -> cs.surfaceContainerHighest to cs.onSurfaceVariant
        ChapterStatus.LEARNING -> cs.secondaryContainer to cs.onSecondaryContainer
        ChapterStatus.REVIEW -> s.warnContainer to s.warn
        ChapterStatus.MASTERED -> s.correctContainer to s.correct
    }
    Pill(status.label, bg, fg)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StandardBadges(status: StandardStatus, requiresReview: Boolean, standardReference: String?, note: String?) {
    val s = LocalStatusColors.current
    val cs = MaterialTheme.colorScheme
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (status) {
                StandardStatus.CURRENT -> Pill("[현재 기준]", s.correctContainer, s.correct)
                StandardStatus.UPCOMING -> Pill("[시행 예정 기준]", cs.secondaryContainer, cs.onSecondaryContainer)
                StandardStatus.HISTORICAL -> Pill("[과거 기준]", cs.surfaceContainerHighest, cs.onSurfaceVariant)
            }
            if (requiresReview) Pill("[개정사항 확인 필요]", s.warnContainer, s.warn)
        }
        if (!standardReference.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("근거: $standardReference", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (!note.isNullOrBlank()) {
            Spacer(Modifier.height(2.dp))
            Text("개정 메모: $note", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
    }
}

/** 제목 + 본문 블록(개념·해설 화면 공용) */
@Composable
fun InfoBlock(label: String, body: String?, emphasize: Boolean = false) {
    if (body.isNullOrBlank()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasize) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}

/** 간결한 막대그래프(최근 학습량) */
@Composable
fun BarChart(values: List<Int>, labels: List<String>, modifier: Modifier = Modifier, height: Int = 120) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val barColor = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            values.forEach { v ->
                Text("$v", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val w = slot * 0.5f
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - w) / 2
                drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(6f, 6f))
                val h = size.height * v / max
                drawRoundRect(barColor, Offset(x, size.height - h), Size(w, h), CornerRadius(6f, 6f))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
fun KeyValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))
