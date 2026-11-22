package com.cpaconceptlab.study.data.backup

import androidx.room.withTransaction
import com.cpaconceptlab.study.data.local.AppDatabase
import com.cpaconceptlab.study.data.local.ConceptProgressEntity
import com.cpaconceptlab.study.data.local.QuestionAttemptEntity
import com.cpaconceptlab.study.data.local.QuestionProgressEntity
import com.cpaconceptlab.study.data.local.StudyDayEntity
import com.cpaconceptlab.study.data.local.WrongNoteEntity
import com.cpaconceptlab.study.data.model.ContentJson
import com.cpaconceptlab.study.data.repository.UserSettings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/* ── 백업 파일 형식(JSON) ── */

@Serializable
data class BackupConceptProgress(
    val conceptId: String, val firstStudiedAt: Long? = null, val lastStudiedAt: Long? = null, val viewCount: Int = 0,
    val basicAttemptCount: Int = 0, val basicCorrectCount: Int = 0, val advancedAttemptCount: Int = 0,
    val advancedCorrectCount: Int = 0, val masteryLevel: String = "NOT_STARTED", val nextReviewAt: Long? = null,
    val isBookmarked: Boolean = false, val memo: String = "",
)

@Serializable
data class BackupQuestionProgress(
    val questionId: String, val mode: String, val firstAnsweredAt: Long, val totalAttempts: Int, val correctAttempts: Int,
    val wrongAttempts: Int, val streak: Int, val lastAnsweredAt: Long, val lastCorrect: Boolean, val totalResponseMs: Long,
    val stage: Int, val nextReviewAt: Long, val mastery: Double,
)

@Serializable
data class BackupAttempt(
    val questionId: String, val mode: String, val userAnswer: String, val isCorrect: Boolean,
    val attemptedAt: Long, val responseTimeMs: Long,
)

@Serializable
data class BackupWrongNote(
    val questionId: String, val mode: String, val lastUserAnswer: String, val wrongCount: Int, val firstWrongAt: Long,
    val lastWrongAt: Long, val isResolved: Boolean, val resolvedAt: Long? = null,
)

@Serializable
data class BackupStudyDay(val epochDay: Long, val solvedCount: Int, val correctCount: Int, val studyMs: Long)

@Serializable
data class BackupImportedPack(val packKey: String, val fileName: String, val importedAt: Long, val json: String)

@Serializable
data class BackupPayload(
    val conceptProgress: List<BackupConceptProgress> = emptyList(),
    val questionProgress: List<BackupQuestionProgress> = emptyList(),
    val attempts: List<BackupAttempt> = emptyList(),
    val wrongNotes: List<BackupWrongNote> = emptyList(),
    val studyDays: List<BackupStudyDay> = emptyList(),
    val settings: UserSettings? = null,
    val importedPacks: List<BackupImportedPack> = emptyList(),
)

@Serializable
data class BackupFile(
    val app: String = APP_ID,
    val backupFormatVersion: Int,
    val kind: String,
    val appVersionCode: Int,
    val contentVersion: Int,
    val exportedAt: Long,
    val payload: BackupPayload,
    /** payload JSON 의 SHA-256 (무결성 검증) */
    val checksum: String,
)

const val APP_ID = "com.cpaconceptlab.study"
const val BACKUP_FORMAT_VERSION = 1

enum class BackupKind { RECORDS, FULL }
enum class RestoreMode { REPLACE, MERGE }

/**
 * 학습기록 백업·복원.
 *  - 내보내기(RECORDS): 학습기록만
 *  - 전체 백업(FULL): 학습기록 + 설정 + 사용자가 가져온 콘텐츠 팩 원본
 *  - 복원(REPLACE): 현재 학습기록을 백업 내용으로 대체(콘텐츠 테이블은 유지)
 *  - 가져오기(MERGE): 현재 기록과 병합(중복 풀이기록 제거, 진도는 더 최근 기록 우선)
 * 학습 콘텐츠 원본(번들)은 백업에 포함하지 않고 안정적인 ID 로만 연결한다.
 */
class BackupManager(private val db: AppDatabase) {

    class BackupException(message: String) : Exception(message)

    data class Inspection(
        val file: BackupFile,
        val unknownConceptIds: Int,
        val unknownQuestionIds: Int,
        val duplicateAttempts: Int,
        val warnings: List<String>,
    )

    data class RestoreResult(val message: String, val importedPacks: List<Pair<String, String>>, val settings: UserSettings?)

    suspend fun export(kind: BackupKind, settings: UserSettings?, appVersionCode: Int, contentVersion: Int, now: Long): String {
        val dao = db.progressDao()
        val payload = BackupPayload(
            conceptProgress = dao.allConceptProgress().map {
                BackupConceptProgress(
                    it.conceptId, it.firstStudiedAt, it.lastStudiedAt, it.viewCount, it.basicAttemptCount, it.basicCorrectCount,
                    it.advancedAttemptCount, it.advancedCorrectCount, it.masteryLevel, it.nextReviewAt, it.isBookmarked, it.memo,
                )
            },
            questionProgress = dao.allQuestionProgress().map {
                BackupQuestionProgress(
                    it.questionId, it.mode, it.firstAnsweredAt, it.totalAttempts, it.correctAttempts, it.wrongAttempts,
                    it.streak, it.lastAnsweredAt, it.lastCorrect, it.totalResponseMs, it.stage, it.nextReviewAt, it.mastery,
                )
            },
            attempts = dao.allAttempts().map { BackupAttempt(it.questionId, it.mode, it.userAnswer, it.isCorrect, it.attemptedAt, it.responseTimeMs) },
            wrongNotes = dao.allWrongNotes().map {
                BackupWrongNote(it.questionId, it.mode, it.lastUserAnswer, it.wrongCount, it.firstWrongAt, it.lastWrongAt, it.isResolved, it.resolvedAt)
            },
            studyDays = dao.allStudyDays().map { BackupStudyDay(it.epochDay, it.solvedCount, it.correctCount, it.studyMs) },
            settings = if (kind == BackupKind.FULL) settings else null,
            importedPacks = if (kind == BackupKind.FULL) {
                db.contentDao().importedPacks().map { BackupImportedPack(it.packKey, it.fileName, it.importedAt, it.json) }
            } else emptyList(),
        )
        val file = BackupFile(
            backupFormatVersion = BACKUP_FORMAT_VERSION, kind = kind.name, appVersionCode = appVersionCode,
            contentVersion = contentVersion, exportedAt = now, payload = payload, checksum = checksum(payload),
        )
        return ContentJson.encodeToString(BackupFile.serializer(), file)
    }

    /** 파일 형식·버전·무결성·중복·미존재 ID·호환성 검증. 실패 시 BackupException(앱은 종료되지 않음). */
    fun inspect(json: String, knownConceptIds: Set<String>, knownQuestionIds: Set<String>, currentContentVersion: Int): Inspection {
        val file = try {
            ContentJson.decodeFromString(BackupFile.serializer(), json)
        } catch (e: SerializationException) {
            throw BackupException("백업 파일 형식이 올바르지 않거나 손상되었습니다.")
        } catch (e: IllegalArgumentException) {
            throw BackupException("백업 파일 값이 올바르지 않습니다.")
        }
        if (file.app != APP_ID) throw BackupException("CPA Concept Lab 백업 파일이 아닙니다.")
        if (file.backupFormatVersion > BACKUP_FORMAT_VERSION) {
            throw BackupException("더 최신 버전 앱에서 만든 백업입니다(형식 v${file.backupFormatVersion}). 앱을 업데이트한 뒤 복원하세요.")
        }
        if (file.backupFormatVersion < 1) throw BackupException("알 수 없는 백업 형식 버전입니다.")
        if (checksum(file.payload) != file.checksum) throw BackupException("백업 파일 무결성 검증에 실패했습니다(파일이 수정되었거나 손상됨).")

        val p = file.payload
        val dupAttempts = p.attempts.size - p.attempts.distinctBy { Triple(it.questionId, it.mode, it.attemptedAt) }.size
        val dupProgress = p.questionProgress.size - p.questionProgress.distinctBy { it.questionId to it.mode }.size
        if (dupProgress > 0) throw BackupException("백업 데이터에 중복된 문항 진도 기록이 있어 복원할 수 없습니다.")
        val unknownC = p.conceptProgress.count { it.conceptId !in knownConceptIds }
        val unknownQ = (p.questionProgress.map { it.questionId } + p.attempts.map { it.questionId } + p.wrongNotes.map { it.questionId })
            .toSet().count { it !in knownQuestionIds }
        val warnings = buildList {
            if (dupAttempts > 0) add("중복 풀이기록 ${dupAttempts}건은 한 번만 복원합니다.")
            if (unknownC + unknownQ > 0) add("현재 콘텐츠에 없는 개념 ${unknownC}개·문항 ${unknownQ}개의 기록은 보존되며, 해당 콘텐츠가 추가되면 자동으로 연결됩니다(통계에서는 제외).")
            if (file.contentVersion > currentContentVersion) add("백업의 콘텐츠 버전(v${file.contentVersion})이 현재 앱(v$currentContentVersion)보다 높습니다. 앱 업데이트를 권장합니다.")
        }
        return Inspection(file, unknownC, unknownQ, dupAttempts, warnings)
    }

    suspend fun restore(inspection: Inspection, mode: RestoreMode): RestoreResult {
        val p = inspection.file.payload
        val dao = db.progressDao()
        db.withTransaction {
            if (mode == RestoreMode.REPLACE) {
                dao.clearAttempts(); dao.clearQuestionProgress(); dao.clearConceptProgress(); dao.clearWrongNotes(); dao.clearStudyDays()
            }
            // 풀이기록: (문항, 모드, 시각) 고유 인덱스로 중복 자동 제거
            dao.insertAttempts(p.attempts.distinctBy { Triple(it.questionId, it.mode, it.attemptedAt) }.map {
                QuestionAttemptEntity(0, it.questionId, it.mode, it.userAnswer, it.isCorrect, it.attemptedAt, it.responseTimeMs)
            })
            val existingQp = if (mode == RestoreMode.MERGE) dao.allQuestionProgress().associateBy { it.questionId to it.mode } else emptyMap()
            dao.upsertQuestionProgress(p.questionProgress.mapNotNull { b ->
                val e = existingQp[b.questionId to b.mode]
                if (e != null && e.lastAnsweredAt >= b.lastAnsweredAt) null else QuestionProgressEntity(
                    b.questionId, b.mode, b.firstAnsweredAt, b.totalAttempts, b.correctAttempts, b.wrongAttempts, b.streak,
                    b.lastAnsweredAt, b.lastCorrect, b.totalResponseMs, b.stage, b.nextReviewAt, b.mastery,
                )
            })
            val existingCp = if (mode == RestoreMode.MERGE) dao.allConceptProgress().associateBy { it.conceptId } else emptyMap()
            dao.upsertConceptProgress(p.conceptProgress.mapNotNull { b ->
                val e = existingCp[b.conceptId]
                if (e != null && (e.lastStudiedAt ?: 0) >= (b.lastStudiedAt ?: 0)) null else ConceptProgressEntity(
                    b.conceptId, b.firstStudiedAt, b.lastStudiedAt, b.viewCount, b.basicAttemptCount, b.basicCorrectCount,
                    b.advancedAttemptCount, b.advancedCorrectCount, b.masteryLevel, b.nextReviewAt, b.isBookmarked, b.memo,
                )
            })
            val existingWn = if (mode == RestoreMode.MERGE) dao.allWrongNotes().associateBy { it.questionId to it.mode } else emptyMap()
            dao.upsertWrongNotes(p.wrongNotes.map { b ->
                val e = existingWn[b.questionId to b.mode]
                if (e == null) WrongNoteEntity(b.questionId, b.mode, b.lastUserAnswer, b.wrongCount, b.firstWrongAt, b.lastWrongAt, b.isResolved, b.resolvedAt)
                else WrongNoteEntity(
                    b.questionId, b.mode,
                    lastUserAnswer = if (b.lastWrongAt > e.lastWrongAt) b.lastUserAnswer else e.lastUserAnswer,
                    wrongCount = maxOf(e.wrongCount, b.wrongCount),
                    firstWrongAt = minOf(e.firstWrongAt, b.firstWrongAt),
                    lastWrongAt = maxOf(e.lastWrongAt, b.lastWrongAt),
                    isResolved = if (b.lastWrongAt > e.lastWrongAt) b.isResolved else e.isResolved,
                    resolvedAt = if (b.lastWrongAt > e.lastWrongAt) b.resolvedAt else e.resolvedAt,
                )
            })
            val existingSd = if (mode == RestoreMode.MERGE) dao.allStudyDays().associateBy { it.epochDay } else emptyMap()
            dao.upsertStudyDays(p.studyDays.map { b ->
                val e = existingSd[b.epochDay]
                if (e == null) StudyDayEntity(b.epochDay, b.solvedCount, b.correctCount, b.studyMs)
                else StudyDayEntity(b.epochDay, maxOf(e.solvedCount, b.solvedCount), maxOf(e.correctCount, b.correctCount), maxOf(e.studyMs, b.studyMs))
            })
        }
        val msg = "복원 완료: 풀이기록 ${p.attempts.size}건, 문항 진도 ${p.questionProgress.size}건, 오답노트 ${p.wrongNotes.size}건, 학습일 ${p.studyDays.size}일"
        return RestoreResult(msg, p.importedPacks.map { it.fileName.ifBlank { it.packKey } to it.json }, p.settings)
    }

    companion object {
        fun checksum(payload: BackupPayload): String {
            val bytes = ContentJson.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
            return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        }
    }
}
