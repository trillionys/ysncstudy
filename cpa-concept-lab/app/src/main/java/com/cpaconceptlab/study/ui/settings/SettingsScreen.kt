package com.cpaconceptlab.study.ui.settings

import com.cpaconceptlab.study.data.repository.ReviewPreset
import com.cpaconceptlab.study.domain.model.Difficulty
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.clickable
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cpaconceptlab.study.data.backup.BackupKind
import com.cpaconceptlab.study.data.backup.BackupManager
import com.cpaconceptlab.study.data.backup.RestoreMode
import com.cpaconceptlab.study.ui.MainViewModel
import com.cpaconceptlab.study.data.repository.DEFAULT_INTERVALS
import com.cpaconceptlab.study.ui.components.KeyValue
import com.cpaconceptlab.study.ui.components.SectionCard
import com.cpaconceptlab.study.ui.components.VSpace
import com.cpaconceptlab.study.ui.review.fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

private fun readText(ctx: Context, uri: Uri): String =
    ctx.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        ?: throw IllegalStateException("파일을 열 수 없습니다.")

private fun displayName(ctx: Context, uri: Uri): String = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment ?: "content.json"

private const val MAX_FILE_CHARS = 20_000_000

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onOpenReport: () -> Unit, onNavigate: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val versions by vm.contentVersions.collectAsState()
    var intervals by remember(settings.reviewIntervals) { mutableStateOf(settings.reviewIntervals) }
    var pendingKind by remember { mutableStateOf(BackupKind.FULL) }
    var pendingRestore by remember { mutableStateOf<Pair<BackupManager.Inspection, RestoreMode>?>(null) }
    var restoreAfterBackup by remember { mutableStateOf(false) }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) { restoreAfterBackup = false; return@rememberLauncherForActivityResult }
        scope.launch {
            try {
                val json = vm.exportBackup(pendingKind)
                withContext(Dispatchers.IO) {
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: throw IllegalStateException("저장 위치를 열 수 없습니다.")
                }
                vm.showMessage("저장했습니다: ${displayName(ctx, uri)}")
                if (restoreAfterBackup) {
                    restoreAfterBackup = false
                    pendingRestore?.let { (ins, mode) -> vm.restore(ins, mode); pendingRestore = null }
                }
            } catch (e: Exception) {
                restoreAfterBackup = false
                vm.showMessage("저장 실패: ${e.message}")
            }
        }
    }

    var openPurpose by remember { mutableStateOf("") }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = try {
                withContext(Dispatchers.IO) { readText(ctx, uri) }
            } catch (e: Exception) {
                vm.showMessage("파일을 읽을 수 없습니다: ${e.message}"); return@launch
            }
            if (text.length > MAX_FILE_CHARS) { vm.showMessage("파일이 너무 큽니다."); return@launch }
            when (openPurpose) {
                "content" -> vm.importContent(text, displayName(ctx, uri))
                "restore", "merge" -> {
                    vm.inspectBackup(text)
                        .onSuccess { pendingRestore = it to if (openPurpose == "restore") RestoreMode.REPLACE else RestoreMode.MERGE }
                        .onFailure { vm.showMessage("백업 파일 오류: ${it.message}") }
                }
            }
        }
    }

    fun save(kind: BackupKind, prefix: String) {
        pendingKind = kind
        createDoc.launch("${prefix}_${LocalDate.now()}.json")
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionCard(title = "화면") {
            Text("글자 크기: ${(settings.fontScale * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(settings.fontScale, { v -> vm.updateSettings { it.copy(fontScale = (v * 20).toInt() / 20f) } }, valueRange = 0.85f..1.5f)
            Text("예시) 재무정보가 이용자의 의사결정에 차이를 가져올 수 있는 특성은 목적적합성이다.", style = MaterialTheme.typography.bodyLarge)
            VSpace(10)
            val modes = listOf("SYSTEM" to "시스템", "LIGHT" to "라이트", "DARK" to "다크")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { i, (k, label) ->
                    SegmentedButton(selected = settings.themeMode == k, onClick = { vm.updateSettings { it.copy(themeMode = k) } }, shape = SegmentedButtonDefaults.itemShape(i, modes.size)) { Text(label) }
                }
            }
        }
        VSpace(12)
        SectionCard(title = "학습 설정") {
            SettingRow("오늘의 문제 목표", if (settings.goalUnlimited) "무제한 학습" else "${"%,d".format(settings.dailyGoal)}문제") { onNavigate("goalSettings") }
            SettingRow("복습 주기", ReviewPreset.of(settings.reviewPreset).label + " · " + settings.intervals.joinToString("→") + "일") { onNavigate("reviewSettings") }
            VSpace(6)
            Text("기본 난이도(단원 문제 풀기)", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Difficulty.entries.forEach { d ->
                    FilterChip(selected = settings.lastDifficulty == d.name, onClick = { vm.updateSettings { it.copy(lastDifficulty = d.name) } }, label = { Text(d.label) })
                }
            }
            Text("기본 문제 유형", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.cpaconceptlab.study.ui.quiz.UNIT_KINDS.forEach { k ->
                    FilterChip(selected = settings.lastKind == k.name, onClick = { vm.updateSettings { it.copy(lastKind = k.name) } }, label = { Text(k.label) })
                }
            }
            Text("한 세트 기본 문항 수: ${settings.sessionSize}", style = MaterialTheme.typography.bodyMedium)
            Slider(settings.sessionSize.toFloat(), { v -> vm.updateSettings { it.copy(sessionSize = v.toInt()) } }, valueRange = 5f..50f, steps = 8)
            Text("한 세트를 다 풀면 같은 범위로 이어서 다음 세트를 풀 수 있습니다(문항을 한꺼번에 불러오지 않아 메모리를 아낍니다).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VSpace(12)
        SectionCard(title = "학습 데이터 · 백업·복원") {
            Text("앱을 삭제하면 기기 안의 학습기록도 삭제됩니다. 정기적으로 백업 파일을 원하는 위치(Google Drive, 다운로드 폴더 등)에 저장하세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            VSpace(8)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { save(BackupKind.FULL, "cpa_concept_lab_full_backup") }, modifier = Modifier.weight(1f)) { Text("전체 데이터 백업") }
                FilledTonalButton(onClick = { openPurpose = "restore"; openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text("백업 데이터 복원") }
            }
            VSpace(6)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { save(BackupKind.RECORDS, "cpa_concept_lab_records") }, modifier = Modifier.weight(1f)) { Text("학습기록 내보내기") }
                OutlinedButton(onClick = { openPurpose = "merge"; openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.weight(1f)) { Text("학습기록 가져오기") }
            }
            VSpace(4)
            Text("복원 = 현재 기록을 백업 내용으로 대체 / 가져오기 = 현재 기록과 병합(중복 제거, 최신 기록 우선)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        VSpace(12)
        SectionCard(title = "학습 콘텐츠") {
            Button(onClick = { openPurpose = "content"; openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("학습 콘텐츠 가져오기 (JSON)") }
            VSpace(6)
            OutlinedButton(onClick = onOpenReport, modifier = Modifier.fillMaxWidth()) { Text("개념 누락 검증 리포트") }
            VSpace(8)
            Text("콘텐츠 반영 이력", style = MaterialTheme.typography.titleSmall)
            versions.take(10).forEach { v ->
                KeyValue("v${v.version} · ${if (v.source == "bundled") "앱 내장" else "가져오기"} · ${fmt(v.importedAt)}", "+개념 ${v.insertedConcepts} +문항 ${v.insertedQuestions} / 갱신 ${v.updatedConcepts + v.updatedQuestions}")
            }
        }
        VSpace(12)
        var confirmDelete by remember { mutableStateOf(0) }
        SectionCard(title = "학습기록 관리") {
            Text("학습기록은 이 기기 안에만 저장됩니다. 삭제하면 풀이기록·오답노트·복습 일정·개념 진도가 지워지며 되돌릴 수 없습니다(콘텐츠와 설정은 유지).",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { confirmDelete = 1 }, modifier = Modifier.fillMaxWidth()) { Text("학습데이터 삭제") }
        }
        if (confirmDelete > 0) {
            AlertDialog(
                onDismissRequest = { confirmDelete = 0 },
                title = { Text(if (confirmDelete == 1) "학습데이터를 삭제할까요?" else "정말 삭제합니다") },
                text = { Text(if (confirmDelete == 1) "삭제 전에 '전체 데이터 백업'을 해 두기를 권장합니다." else "모든 풀이기록·오답노트·복습 일정이 삭제됩니다. 이 작업은 되돌릴 수 없습니다.") },
                confirmButton = {
                    TextButton(onClick = { if (confirmDelete == 1) confirmDelete = 2 else { vm.clearLearningData(); confirmDelete = 0 } }) {
                        Text(if (confirmDelete == 1) "계속" else "삭제", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = 0 }) { Text("취소") } },
            )
        }
        VSpace(12)
        var reporting by remember { mutableStateOf(false) }
        SectionCard(title = "앱 정보") {
            SettingRow("이용 안내", "학습 콘텐츠의 정확성·최신성 안내") { onNavigate("notice") }
            SettingRow("개인정보 처리방침", "기기 내 저장 · 수집·전송 없음") { onNavigate("privacy") }
            SettingRow("공식 출처 · 버전 정보", vm.appVersion) { onNavigate("appInfo") }
            SettingRow("콘텐츠 오류 신고", "메일·메신저 등 직접 고른 앱으로 보내기") { reporting = true }
        }
        if (reporting) {
            com.cpaconceptlab.study.ui.common.ErrorReportDialog(null, null, null, versions.firstOrNull()?.version ?: 0, vm.appVersion, onDismiss = { reporting = false })
        }
        VSpace(24)
    }

    pendingRestore?.let { (ins, mode) ->
        if (restoreAfterBackup) return@let
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text(if (mode == RestoreMode.REPLACE) "백업 데이터 복원" else "학습기록 가져오기(병합)") },
            text = {
                Column {
                    Text("백업 일시: ${fmt(ins.file.exportedAt)} · 형식 v${ins.file.backupFormatVersion} · 콘텐츠 v${ins.file.contentVersion}")
                    Text("풀이기록 ${ins.file.payload.attempts.size}건, 문항 진도 ${ins.file.payload.questionProgress.size}건, 오답노트 ${ins.file.payload.wrongNotes.size}건")
                    ins.warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    VSpace(8)
                    Text(
                        if (mode == RestoreMode.REPLACE) "현재 학습기록이 백업 내용으로 대체됩니다. 복원 전에 현재 데이터를 백업하는 것을 권장합니다."
                        else "현재 학습기록과 병합합니다.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                    TextButton(onClick = { restoreAfterBackup = true; save(BackupKind.FULL, "cpa_concept_lab_before_restore") }) { Text("현재 데이터 백업 후 진행") }
                    TextButton(onClick = { vm.restore(ins, mode); pendingRestore = null }) { Text("바로 진행") }
                }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("취소") } },
        )
    }
}

@Composable
fun ValidationReportScreen(vm: MainViewModel) {
    val report = remember { vm.validationReport() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        SectionCard(title = if (report?.passed == true) "검증 통과" else "검증 결과") {
            Text(report?.toText() ?: "콘텐츠를 불러오는 중입니다.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}


@Composable
fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("›", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
