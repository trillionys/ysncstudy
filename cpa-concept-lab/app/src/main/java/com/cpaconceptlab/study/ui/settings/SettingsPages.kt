package com.cpaconceptlab.study.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.data.repository.DailyGoal
import com.cpaconceptlab.study.data.repository.ReviewPreset
import com.cpaconceptlab.study.domain.usecase.ReviewScheduler
import com.cpaconceptlab.study.ui.MainViewModel
import com.cpaconceptlab.study.ui.common.ContentNotice
import com.cpaconceptlab.study.ui.components.KeyValue
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.VSpace
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ───────────────────────── 오늘의 문제 목표 ─────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoalSettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionCard(title = "오늘의 문제 목표") {
            Text(
                if (settings.goalUnlimited) "현재: 무제한 학습(목표 없이 자유롭게)" else "현재: 하루 ${"%,d".format(settings.dailyGoal)}문제",
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("goal-current"),
            )
            Text("목표는 하루에 실제로 제출한 '서로 다른 문제' 수로 집계합니다. 목표를 넘겨도 계속 풀 수 있습니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(8)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DailyGoal.QUICK.forEach { n ->
                    FilterChip(
                        selected = !settings.goalUnlimited && settings.dailyGoal == n,
                        onClick = { error = null; vm.updateSettings { it.copy(dailyGoal = n, goalUnlimited = false) } },
                        label = { Text("${"%,d".format(n)}문제") },
                    )
                }
                FilterChip(selected = settings.goalUnlimited, onClick = { vm.updateSettings { it.copy(goalUnlimited = true) } }, label = { Text("무제한 학습") })
            }
            VSpace(10)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    text, { text = it.filter { ch -> ch.isDigit() || ch == ',' }.take(9); error = null },
                    label = { Text("직접 입력(문제 수)") }, singleLine = true, isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f).testTag("goal-input"),
                )
                TextButton(onClick = {
                    DailyGoal.parse(text)
                        .onSuccess { n -> vm.updateSettings { it.copy(dailyGoal = n, goalUnlimited = false) }; text = ""; vm.showMessage("오늘의 목표를 ${"%,d".format(n)}문제로 저장했습니다.") }
                        .onFailure { error = it.message }
                }, modifier = Modifier.testTag("goal-save")) { Text("저장") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ───────────────────────── 복습 주기 ─────────────────────────

private val DATE = DateTimeFormatter.ofPattern("M월 d일")

@Composable
fun ReviewSettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    var preset by remember(settings.reviewPreset) { mutableStateOf(ReviewPreset.of(settings.reviewPreset)) }
    val custom = remember(settings.reviewIntervals) { mutableStateListOf<String>().apply { addAll(settings.intervals.map { it.toString() }) } }
    var error by remember { mutableStateOf<String?>(null) }
    var showAdvanced by remember { mutableStateOf(false) }
    val intervals: List<Int?> = if (preset == ReviewPreset.CUSTOM) custom.map { it.trim().toIntOrNull() } else preset.intervals
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            "복습 간격은 기억을 오래 유지하기 위해 같은 개념을 다시 학습하는 주기입니다. 자주 복습할수록 반복 횟수가 늘어나며, 긴 간격을 선택하면 복습 부담이 줄어듭니다.",
            style = MaterialTheme.typography.bodyMedium,
        )
        VSpace(10)
        SectionCard(title = "복습 주기 선택") {
            ReviewPreset.entries.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("preset-${p.name}")) {
                    RadioButton(selected = preset == p, onClick = { preset = p; error = null })
                    Column(Modifier.weight(1f)) {
                        Text(p.label + if (p == ReviewPreset.STANDARD) " (기본)" else "", style = MaterialTheme.typography.bodyLarge)
                        Text(p.description + if (p.intervals.isNotEmpty()) " · " + p.intervals.joinToString(" → ") + "일" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (preset == ReviewPreset.CUSTOM) {
            VSpace(10)
            SectionCard(title = "직접 설정") {
                custom.forEachIndexed { i, v ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}회차 복습", Modifier.width(88.dp))
                        OutlinedButton(onClick = { custom[i] = ((v.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString(); error = null }) { Text("−") }
                        OutlinedTextField(
                            v, { custom[i] = it.filter { ch -> ch.isDigit() }.take(4); error = null }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(80.dp).padding(horizontal = 4.dp).testTag("custom-$i"),
                        )
                        OutlinedButton(onClick = { custom[i] = ((v.toIntOrNull() ?: 0) + 1).coerceAtMost(ReviewScheduler.MAX_INTERVAL_DAYS).toString(); error = null }) { Text("+") }
                        Text(" 일 후")
                    }
                }
                Row {
                    TextButton(onClick = {
                        if (custom.size < ReviewScheduler.MAX_STEPS) custom.add(((custom.lastOrNull()?.toIntOrNull() ?: 1) * 2).coerceAtMost(ReviewScheduler.MAX_INTERVAL_DAYS).toString())
                    }) { Text("단계 추가") }
                    TextButton(onClick = { if (custom.size > 1) custom.removeAt(custom.lastIndex) }) { Text("마지막 단계 삭제") }
                }
                Text("각 단계는 이전 단계보다 길어야 합니다(1~${ReviewScheduler.MAX_INTERVAL_DAYS}일).", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        VSpace(10)
        SectionCard(title = "복습 일정 미리보기") {
            val valid = ReviewScheduler.validate(intervals)
            if (valid != null) {
                Text(valid, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else {
                Text("오늘 새 개념을 학습하고 매번 정답을 맞힌다고 가정한 예시 일정입니다(오답이면 첫 간격부터 다시, 정답률이 낮으면 간격을 줄입니다).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val today = LocalDate.now()
                ReviewScheduler.previewOffsets(intervals.map { it!! }).forEachIndexed { i, off ->
                    KeyValue("${i + 1}차 복습", "${today.plusDays(off.toLong()).format(DATE)} (${if (off == 1) "내일" else "${off}일 후"}, 간격 ${intervals[i]}일)")
                }
            }
        }
        error?.let { VSpace(6); Text(it, color = MaterialTheme.colorScheme.error) }
        VSpace(10)
        Button(onClick = {
            val reason = ReviewScheduler.validate(intervals)
            if (reason != null) { error = reason; return@Button }
            val list = intervals.map { it!! }
            vm.updateSettings { it.copy(reviewPreset = preset.name, reviewIntervals = list.joinToString(",")) }
            vm.showMessage("복습 주기를 '${preset.label}'(${list.joinToString("→")}일)로 저장했습니다. 기존 풀이기록·숙달도는 그대로이며, 다음 복습일부터 새 주기가 적용됩니다.")
        }, modifier = Modifier.fillMaxWidth().testTag("review-save")) { Text("저장") }
        VSpace(6)
        TextButton(onClick = { showAdvanced = !showAdvanced }) { Text(if (showAdvanced) "고급 설정 접기" else "고급 설정") }
        if (showAdvanced) {
            SectionCard(title = "고급 설정") {
                Text("이미 예정된 복습 날짜도 새 주기로 다시 계산합니다. 풀이기록·숙달도·오답노트는 바뀌지 않으며, 이미 복습일이 지난 문제는 그대로 '오늘 복습'에 남습니다.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.rescheduleReviews(settings.intervals) }, modifier = Modifier.fillMaxWidth()) { Text("기존 예정 일정 다시 계산(저장된 주기 기준)") }
                Text("정답이면 다음 단계 간격으로, 오답이면 첫 간격(다음날)으로 돌아갑니다. 응답이 매우 느리면 단계를 올리지 않고, 정답률이 60% 미만인 문항은 간격을 30% 줄입니다.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ───────────────────────── 이용 안내 · 개인정보 처리방침 · 앱 정보 ─────────────────────────

@Composable
fun NoticeScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionCard(title = ContentNotice.TITLE) { Text(ContentNotice.FULL, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("notice-full")) }
        VSpace(12)
        OfficialSourcesCard()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OfficialSourcesCard() {
    val ctx = LocalContext.current
    SectionCard(title = "공식 출처") {
        Text("기준서·법령 원문과 앱의 학습용 해설은 다릅니다. 정확한 내용은 아래 공식 자료에서 확인하세요.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ContentNotice.OFFICIAL_SOURCES.forEach { (label, url) -> AssistChip(onClick = { ContentNotice.open(ctx, url) }, label = { Text("$label ↗") }) }
        }
    }
}

/** 실제 구현에 맞춘 개인정보 처리방침(운영자 정보는 출시 전에 채워야 한다 — docs/PRIVACY_POLICY.md 와 같은 내용). */
object PrivacyPolicy {
    const val UPDATED = "2026-11-25"
    val SECTIONS = listOf(
        "1. 앱과 운영자" to "앱 이름: CPA Concept Lab(무료 앱 — 유료 결제·인앱 구매·광고 없음)\n운영자(개발자)와 개인정보 문의처: Google Play 스토어의 이 앱 등록정보에 표시된 개발자 이름과 이메일",
        "2. 수집하는 정보" to "이 앱은 회원가입·로그인이 없고, 이름·연락처·기기 식별자·위치 등 개인정보를 수집하지 않습니다. 인터넷 권한을 요청하지 않으며 분석도구·광고 SDK를 포함하지 않습니다.",
        "3. 기기에 저장되는 정보" to "학습기록(문제 풀이 결과·오답노트·복습 일정·개념 진도·메모·북마크)과 앱 설정은 사용자의 기기 안에만 저장됩니다. 개발자는 이 정보에 접근할 수 없습니다.",
        "4. 백업" to "• 앱 내 백업: 사용자가 직접 고른 위치(예: 다운로드 폴더, 클라우드 드라이브 앱)에 백업 파일을 저장합니다.\n• Android 자동 백업: 기기 설정에서 백업이 켜져 있으면 Android가 학습기록 DB와 설정을 사용자 본인의 Google 계정 백업에 저장할 수 있습니다. 이 기능은 Google의 Android 백업 서비스가 처리하며 개발자에게 전송되지 않습니다.",
        "5. 오류 신고" to "오류 신고는 사용자가 직접 고른 앱(메일·메신저 등)으로만 보내지며, 보내기 전에는 아무것도 전송되지 않습니다. 신고에는 개념·문항 ID, 콘텐츠·앱 버전, 사용자가 작성한 내용만 들어가고 학습기록은 포함되지 않습니다.",
        "6. 제3자 제공·공유" to "개인정보를 수집하지 않으므로 제3자에게 제공하거나 공유하지 않습니다.",
        "7. 보유기간과 삭제" to "학습기록은 사용자가 삭제할 때까지 기기에 보관됩니다. 설정 → 학습기록 관리 → 학습데이터 삭제, 또는 앱 삭제로 지울 수 있습니다.",
        "8. 보안" to "데이터는 Android 앱 전용 저장공간에 저장되어 다른 앱이 접근할 수 없습니다. 백업 파일은 사용자가 저장 위치를 직접 관리해야 합니다.",
        "9. 변경" to "이 방침이 바뀌면 앱 업데이트와 함께 이 화면에 게시합니다. 시행일: $UPDATED",
    )
}

@Composable
fun PrivacyScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("privacy")) {
        PrivacyPolicy.SECTIONS.forEach { (t, b) ->
            SectionCard(title = t) { Text(b, style = MaterialTheme.typography.bodyMedium) }
            VSpace(8)
        }
    }
}

@Composable
fun AppInfoScreen(vm: MainViewModel) {
    val versions by vm.contentVersions.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionCard(title = "버전 정보") {
            KeyValue("앱 버전", vm.appVersion)
            KeyValue("콘텐츠 버전", versions.firstOrNull()?.let { "v${it.version}" } ?: "—")
            Text("CPA Concept Lab은 금융위원회·금융감독원·한국공인회계사회 또는 특정 학원의 공식 앱이 아니며, 합격이나 내용의 정확성을 보장하지 않습니다.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VSpace(12)
        OfficialSourcesCard()
        VSpace(12)
        SectionCard(title = "저작권") {
            Text("앱의 개념 설명·문제·해설은 자체 작성한 학습 자료입니다. 법령 원문은 국가법령정보센터의 공개 법령을 인용했습니다. 기준서·개념체계의 정의는 학습 목적으로 요약·인용한 부분이 있으며 기준서명을 함께 표시합니다. 상용 교재의 문제·해설·이미지는 수록하지 않습니다.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
