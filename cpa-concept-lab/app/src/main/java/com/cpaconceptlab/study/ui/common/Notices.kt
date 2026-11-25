package com.cpaconceptlab.study.ui.common

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** 학습 콘텐츠 이용 안내(정확성·최신성). 최초 실행·설정·개념 상세에서 같은 문안을 쓴다. */
object ContentNotice {
    const val TITLE = "학습 콘텐츠 이용 안내"
    const val SHORT = "학습 보조용 자료입니다. 기준서·법령의 개정 및 시험 적용연도를 확인해 주세요."
    const val FIRST_RUN = "CPA Concept Lab은 공인회계사 시험 준비를 위한 학습 보조 앱입니다. 개념 설명·문제·해설은 학습 목적으로 제공되며 " +
        "정확성·완전성·최신성을 보장하지 않습니다. 기준서·법령·세율은 개정될 수 있으니 공식 자료로 확인해 주세요. " +
        "이 앱은 국가기관이나 시험 주관기관의 공식 앱이 아닙니다."
    const val FULL = "CPA Concept Lab은 공인회계사 시험 준비를 위한 학습 보조 애플리케이션입니다.\n\n" +
        "본 앱의 개념 설명, 문제, 해설 및 계산 예시는 학습 목적으로 제공되며, 모든 내용의 정확성·완전성·최신성을 보장하지 않습니다.\n\n" +
        "회계기준, 법령, 세율, 판례, 시험 출제기준 등은 개정 또는 변경될 수 있습니다. 정확한 내용과 실제 적용 여부는 한국회계기준원, " +
        "국가법령정보센터, 금융위원회, 금융감독원 등 관계기관의 공식 자료와 해당 기준서·법령·해석서 등을 통해 확인하시기 바랍니다.\n\n" +
        "본 앱은 특정 국가기관이나 공인회계사시험 주관기관이 제작·승인한 공식 애플리케이션이 아닙니다. 또한 실제 회계처리, 세무신고, " +
        "법률적 판단 또는 투자 의사결정에 대한 전문적인 자문을 대체하지 않습니다."

    /** 공식 출처(앱은 이 링크를 브라우저로 열기만 하며 데이터를 보내지 않는다) */
    val OFFICIAL_SOURCES = listOf(
        "한국회계기준원(K-IFRS)" to "https://www.kasb.or.kr",
        "IFRS Foundation" to "https://www.ifrs.org",
        "국가법령정보센터" to "https://www.law.go.kr",
        "금융위원회" to "https://www.fsc.go.kr",
        "금융감독원 공인회계사시험" to "https://cpa.fss.or.kr",
        "국세청" to "https://www.nts.go.kr",
    )

    fun open(ctx: Context, url: String) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

/** 최초 실행 안내: 핵심만 보여 주고 전문은 펼쳐 볼 수 있게 한다(매 문제 팝업 없음). */
@Composable
fun FirstRunNoticeDialog(onAcknowledge: () -> Unit) {
    var full by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(ContentNotice.TITLE) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(if (full) ContentNotice.FULL else ContentNotice.FIRST_RUN, style = MaterialTheme.typography.bodyMedium)
                if (!full) TextButton(onClick = { full = true }) { Text("전문 보기") }
            }
        },
        confirmButton = { Button(onClick = onAcknowledge) { Text("확인했습니다") } },
    )
}

// ───────────────────────── 콘텐츠 오류 신고 ─────────────────────────

/** 오류 신고 유형 */
enum class ErrorReportType(val label: String) {
    ANSWER("정답 오류"), CALCULATION("계산 오류"), EXPLANATION("해설 오류"), CONCEPT("개념 설명 오류"),
    OUTDATED("오래된 법령·기준서"), TYPO("오탈자"), OTHER("기타"),
}

/** 신고에 담는 정보: 콘텐츠 식별자·유형·사용자가 쓴 내용만(학습기록·개인정보는 넣지 않는다) */
data class ErrorReport(
    val type: ErrorReportType,
    val conceptId: String?,
    val conceptName: String?,
    val questionId: String?,
    val contentVersion: Int,
    val appVersion: String,
    val message: String,
) {
    fun body(): String = buildString {
        appendLine("[CPA Concept Lab 콘텐츠 오류 신고]")
        appendLine("오류 유형: ${type.label}")
        conceptId?.let { appendLine("개념: $it ${conceptName.orEmpty()}") }
        questionId?.let { appendLine("문항: $it") }
        appendLine("콘텐츠 버전: v$contentVersion · 앱 버전: $appVersion")
        appendLine()
        appendLine("내용:")
        append(message.ifBlank { "(내용 없음)" })
    }
}

/**
 * 신고 전달 방식. 현재 구현은 사용자가 고른 앱(메일·메신저·메모 등)으로 공유하는 방식뿐이다.
 * 서버 API 는 없으며, 나중에 서버 신고를 추가하면 이 인터페이스의 구현을 바꾼다.
 * 사용자가 공유 화면에서 직접 보내기 전에는 어떤 데이터도 전송되지 않는다.
 */
interface ErrorReporter {
    fun submit(context: Context, report: ErrorReport)
}

object ShareErrorReporter : ErrorReporter {
    override fun submit(context: Context, report: ErrorReport) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "[CPA Concept Lab] ${report.type.label} — ${report.questionId ?: report.conceptId ?: ""}")
            putExtra(Intent.EXTRA_TEXT, report.body())
        }
        runCatching { context.startActivity(Intent.createChooser(send, "오류 신고 보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ErrorReportDialog(
    conceptId: String?, conceptName: String?, questionId: String?, contentVersion: Int, appVersion: String,
    onDismiss: () -> Unit, reporter: ErrorReporter = ShareErrorReporter,
) {
    val ctx = LocalContext.current
    var type by remember { mutableStateOf(if (questionId != null) ErrorReportType.ANSWER else ErrorReportType.CONCEPT) }
    var msg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오류 신고") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(listOfNotNull(conceptId?.let { "개념 $it ${conceptName.orEmpty()}" }, questionId?.let { "문항 $it" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall)
                androidx.compose.foundation.layout.FlowRow {
                    ErrorReportType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
                OutlinedTextField(msg, { msg = it }, label = { Text("어떤 점이 잘못되었나요?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("'보내기'를 누르면 메일·메신저 등 보낼 앱을 직접 고릅니다. 학습기록은 포함되지 않으며, 보내기 전에는 어떤 정보도 전송되지 않습니다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = {
                reporter.submit(ctx, ErrorReport(type, conceptId, conceptName, questionId, contentVersion, appVersion, msg))
                onDismiss()
            }) { Text("보내기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
