package com.cpaconceptlab.study.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

// ───────────────────────── 필기(투명 화이트보드) ─────────────────────────

/** 펜·형광펜·지우개 */
enum class InkTool(val label: String) { PEN("펜"), HIGHLIGHTER("형광펜"), ERASER("지우개") }

class InkStroke(val points: List<Offset>, val color: Color, val width: Float)

/**
 * 문제 화면 위 필기 상태. 획은 스크롤되는 문제 내용 좌표에 저장되므로 화면을 스크롤해도 밑줄이 문장에 붙어 있다.
 * 문제가 바뀌면 새로 만들어진다(문제마다 빈 화이트보드).
 */
class InkState {
    var enabled by mutableStateOf(false)
    var tool by mutableStateOf(InkTool.PEN)
    var color by mutableStateOf(PEN_COLORS[0])
    val strokes = mutableStateListOf<InkStroke>()
    internal val current = mutableStateListOf<Offset>()

    fun undo() { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }
    fun clear() { strokes.clear() }

    internal fun strokeColor() = if (tool == InkTool.HIGHLIGHTER) HIGHLIGHT.copy(alpha = 0.35f) else color
    internal fun strokeWidth() = if (tool == InkTool.HIGHLIGHTER) 22f else 5f

    /** 지우개: 손가락이 지나간 곳 근처를 지나는 획을 통째로 지운다 */
    internal fun eraseAt(p: Offset, radius: Float = 28f) {
        strokes.removeAll { s -> s.points.any { (it - p).getDistance() < radius } }
    }

    companion object {
        val PEN_COLORS = listOf(Color(0xFFE53935), Color(0xFF1E66F5), Color(0xFF222222))
        val HIGHLIGHT = Color(0xFFFFD400)
    }
}

/** 내용 위에 획을 그리고, 필기 모드일 때만 터치를 가로채 그리기로 쓴다(필기 모드가 아니면 스크롤·입력 그대로). */
fun Modifier.inkLayer(ink: InkState): Modifier = this
    .then(
        if (!ink.enabled) Modifier else Modifier.pointerInput(ink.tool) {
            awaitPointerEventScope {
                while (true) {
                    // Initial 단계에서 먼저 받아 소비: 필기 중에는 아래 버튼·입력란이 눌리지 않는다
                    val down = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.pressed } ?: continue
                    down.consume()
                    if (ink.tool == InkTool.ERASER) ink.eraseAt(down.position) else { ink.current.clear(); ink.current.add(down.position) }
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        ch.consume()
                        if (!ch.pressed) break
                        if (ink.tool == InkTool.ERASER) ink.eraseAt(ch.position) else ink.current.add(ch.position)
                    }
                    if (ink.tool != InkTool.ERASER && ink.current.isNotEmpty()) {
                        val pts = if (ink.current.size == 1) listOf(ink.current[0], ink.current[0] + Offset(0.5f, 0.5f)) else ink.current.toList()
                        ink.strokes.add(InkStroke(pts, ink.strokeColor(), ink.strokeWidth()))
                        ink.current.clear()
                    }
                }
            }
        },
    )
    .drawWithContent {
        drawContent()
        fun draw(points: List<Offset>, color: Color, width: Float) {
            if (points.isEmpty()) return
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        ink.strokes.forEach { draw(it.points, it.color, it.width) }
        draw(ink.current, ink.strokeColor(), ink.strokeWidth())
    }

/** 필기 도구 막대: 펜·형광펜·지우개, 색, 되돌리기, 전체 지우기, 필기 끄기 */
@Composable
fun InkToolbar(ink: InkState, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                InkTool.entries.forEach { t ->
                    val sel = ink.tool == t
                    TextButton(onClick = { ink.tool = t }) {
                        Text(t.label, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.weight(1f))
                InkState.PEN_COLORS.forEach { c ->
                    Box(
                        Modifier.padding(3.dp).size(24.dp).background(c, CircleShape)
                            .border(if (ink.color == c && ink.tool == InkTool.PEN) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { ink.color = c; ink.tool = InkTool.PEN }
                            .semantics { contentDescription = "펜 색" },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = ink::undo, enabled = ink.strokes.isNotEmpty()) { Text("되돌리기") }
                TextButton(onClick = ink::clear, enabled = ink.strokes.isNotEmpty()) { Text("모두 지우기") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { ink.enabled = false }) { Text("필기 끄기") }
            }
            Text("필기 중에는 화면이 스크롤되지 않습니다. 스크롤·답 입력은 '필기 끄기' 후에 하세요(필기는 남아 있습니다).",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 문제 머리 오른쪽 구석의 도구 버튼(계산문제일 때 계산기, 모든 문제에 필기). 내용을 가리지 않도록 떠 있지 않게 둔다. */
@Composable
fun StudyToolButtons(ink: InkState, showCalculator: Boolean, onCalculator: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (showCalculator) {
            FilledTonalButton(onClick = onCalculator, modifier = Modifier.testTag("calculator-button"), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) { Text("계산기") }
        }
        if (!ink.enabled) {
            FilledTonalButton(onClick = { ink.enabled = true }, modifier = Modifier.testTag("ink-button"), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                Text(if (ink.strokes.isEmpty()) "필기" else "필기 (${ink.strokes.size})")
            }
        }
    }
}

// ───────────────────────── 계산기 ─────────────────────────

/** 사칙연산 계산기 엔진(괄호·%·우선순위). 금액 계산이 정확하도록 BigDecimal 로 계산한다. */
object Calculator {
    private val MC = MathContext(20, RoundingMode.HALF_UP)

    fun eval(expr: String): BigDecimal {
        val p = Parser(expr.replace(",", "").replace("×", "*").replace("÷", "/").replace("−", "-").replace(" ", ""))
        val v = p.expr()
        require(p.pos == p.s.length) { "식 오류" }
        return v
    }

    /** 표시용: 천 단위 쉼표, 소수는 최대 8자리(끝 0 제거) */
    fun format(v: BigDecimal): String {
        val r = v.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = r.toPlainString()
        val neg = plain.startsWith("-")
        val body = plain.removePrefix("-")
        val int = body.substringBefore(".")
        val frac = body.substringAfter(".", "")
        val grouped = int.reversed().chunked(3).joinToString(",").reversed()
        return (if (neg) "-" else "") + grouped + (if (frac.isNotEmpty()) ".$frac" else "")
    }

    private class Parser(val s: String) {
        var pos = 0
        private fun peek() = if (pos < s.length) s[pos] else '\u0000'
        fun expr(): BigDecimal {
            var v = term()
            while (peek() == '+' || peek() == '-') {
                val op = s[pos++]
                val r = term()
                v = if (op == '+') v.add(r, MC) else v.subtract(r, MC)
            }
            return v
        }
        private fun term(): BigDecimal {
            var v = factor()
            while (peek() == '*' || peek() == '/') {
                val op = s[pos++]
                val r = factor()
                v = if (op == '*') v.multiply(r, MC) else {
                    require(r.signum() != 0) { "0으로 나눌 수 없음" }
                    v.divide(r, MC)
                }
            }
            return v
        }
        private fun factor(): BigDecimal {
            if (peek() == '-') { pos++; return factor().negate() }
            if (peek() == '+') { pos++; return factor() }
            var v: BigDecimal
            if (peek() == '(') {
                pos++
                v = expr()
                require(peek() == ')') { "괄호 오류" }
                pos++
            } else {
                val start = pos
                while (peek().isDigit() || peek() == '.') pos++
                require(pos > start) { "식 오류" }
                v = BigDecimal(s.substring(start, pos))
            }
            while (peek() == '%') { pos++; v = v.divide(BigDecimal(100), MC) }
            return v
        }
    }
}

/**
 * 계산기 대화상자. 결과가 나오면 '답에 넣기'로 빈칸 입력란에 숫자를 넣을 수 있다(단위·쉼표 없이).
 * 식과 결과는 대화상자를 닫았다 열어도 같은 문제 안에서는 유지된다.
 */
class CalculatorState {
    var expr by mutableStateOf("")
    var result by mutableStateOf<String?>(null)
    var error by mutableStateOf<String?>(null)
    val history = mutableStateListOf<String>()
}

@Composable
fun CalculatorDialog(state: CalculatorState, canInsert: Boolean, onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    fun press(k: String) {
        state.error = null
        when (k) {
            "C" -> { state.expr = ""; state.result = null }
            "⌫" -> state.expr = state.expr.dropLast(1)
            "=" -> runCatching { Calculator.eval(state.expr) }
                .onSuccess { v ->
                    val f = Calculator.format(v)
                    state.history.add(0, "${state.expr} = $f"); if (state.history.size > 5) state.history.removeAt(5)
                    state.result = f; state.expr = f
                }
                .onFailure { state.error = it.message ?: "식 오류" }
            else -> {
                // 결과 직후 숫자를 누르면 새 식 시작, 연산자를 누르면 결과에 이어서 계산
                if (state.result != null && state.expr == state.result && (k.first().isDigit() || k == "." || k == "00" || k == "(")) state.expr = ""
                state.result = null
                state.expr += k
            }
        }
    }
    val keys = listOf(
        listOf("C", "(", ")", "÷"),
        listOf("7", "8", "9", "×"),
        listOf("4", "5", "6", "−"),
        listOf("1", "2", "3", "+"),
        listOf("0", "00", ".", "%"),
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
            Column(Modifier.padding(16.dp).width(320.dp)) {
                Text("계산기", style = MaterialTheme.typography.titleMedium)
                state.history.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                }
                Text(
                    state.expr.ifEmpty { "0" },
                    fontSize = 30.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("calculator-display"),
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                keys.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                        row.forEach { k ->
                            val op = k in setOf("÷", "×", "−", "+", "%", "(", ")", "C")
                            OutlinedButton(
                                onClick = { press(k) },
                                modifier = Modifier.weight(1f).aspectRatio(1.4f),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            ) {
                                Text(k, fontSize = 20.sp, color = if (op) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 3.dp)) {
                    OutlinedButton(onClick = { press("⌫") }, modifier = Modifier.weight(1f)) { Text("⌫") }
                    Button(onClick = { press("=") }, modifier = Modifier.weight(3f).testTag("calculator-equals")) { Text("=", fontSize = 20.sp) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (canInsert) {
                        TextButton(
                            onClick = {
                                runCatching { Calculator.eval(state.expr) }.onSuccess { v ->
                                    onInsert(v.setScale(8, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
                                    onDismiss()
                                }.onFailure { state.error = it.message ?: "식 오류" }
                            },
                            enabled = state.expr.isNotEmpty(),
                        ) { Text("답에 넣기") }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }
            }
        }
    }
}
