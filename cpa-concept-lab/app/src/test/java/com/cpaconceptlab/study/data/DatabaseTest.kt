package com.cpaconceptlab.study.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.cpaconceptlab.study.TestContent
import com.cpaconceptlab.study.data.backup.BackupKind
import com.cpaconceptlab.study.data.backup.BackupManager
import com.cpaconceptlab.study.data.backup.RestoreMode
import com.cpaconceptlab.study.data.importer.ContentImporter
import com.cpaconceptlab.study.data.importer.detailSectionList
import com.cpaconceptlab.study.data.local.AppDatabase
import com.cpaconceptlab.study.data.model.BLANK_TOKEN
import com.cpaconceptlab.study.data.model.ContentJson
import com.cpaconceptlab.study.data.model.ContentPack
import com.cpaconceptlab.study.data.repository.ContentRepository
import com.cpaconceptlab.study.data.repository.StudyRepository
import com.cpaconceptlab.study.data.repository.UserSettings
import com.cpaconceptlab.study.data.importer.BundledAssetContentSource
import com.cpaconceptlab.study.domain.model.QuizMode
import com.cpaconceptlab.study.domain.model.ScopeType
import com.cpaconceptlab.study.domain.usecase.QuestionSelector
import com.cpaconceptlab.study.domain.usecase.ReviewScheduler
import com.cpaconceptlab.study.domain.usecase.StatsCalculator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DatabaseTest {
    private lateinit var ctx: Context
    private lateinit var db: AppDatabase
    private lateinit var content: ContentRepository
    private lateinit var study: StudyRepository
    private val scheduler = ReviewScheduler()

    @Before fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        content = ContentRepository(db, BundledAssetContentSource(ctx.assets))
        study = StudyRepository(db)
    }

    @After fun tearDown() { db.close() }

    private fun conceptMap() = content.snapshot.value!!.questionsByPrimary.mapValues { e -> e.value.map { it.id } }

    private fun answer(qid: String, mode: QuizMode, correct: Boolean, at: Long) = runBlocking {
        val q = content.snapshot.value!!.questionById.getValue(qid)
        study.recordAnswer(q, mode, if (correct) q.blankAnswer else "오답", correct, 4000, at, scheduler, conceptMap())
    }

    // 번들 콘텐츠 최초 로딩: 285개 개념, 600개 이상 문항, 개념별 초기 학습상태 생성
    @Test fun bundledContentLoadsWithInitialProgressRows() = runBlocking<Unit> {
        val r = content.syncBundled()
        assertNotNull(r); assertTrue(r!!.success)
        val snap = content.snapshot.value!!
        assertEquals(TestContent.manifest.conceptCount, snap.concepts.size)
        assertEquals(TestContent.manifest.questionCount, snap.questions.size)
        assertEquals(46, snap.examScopes.size)
        assertEquals(TestContent.manifest.conceptCount, db.progressDao().allConceptProgress().size)
        assertTrue(content.validationReport(snap).passed)
    }

    // 같은 콘텐츠를 두 번 가져와도 중복이 생기지 않는다(idempotent)
    @Test fun importingSameContentTwiceDoesNotDuplicate() = runBlocking<Unit> {
        content.syncBundled()
        val importer = ContentImporter(db)
        val again = importer.import(TestContent.packs, ContentImporter.SOURCE_BUNDLED)
        assertTrue(again.success)
        assertEquals(0, again.insertedConcepts + again.insertedQuestions + again.updatedConcepts + again.updatedQuestions)
        assertEquals(TestContent.manifest.conceptCount, db.contentDao().concepts().size)
        assertEquals(TestContent.manifest.questionCount, db.contentDao().questions().size)
        val crossRefs = db.contentDao().crossRefs()
        assertEquals(crossRefs.size, crossRefs.toSet().size)
        // 같은 버전으로 다시 동기화하면 가져오기를 건너뛴다
        assertNull(content.syncBundled())
    }

    // 기존 콘텐츠에 새 개념·문항 추가 + 설명 변경(리비전 증가): 학습기록은 유지
    @Test fun updateAddsNewContentAndKeepsLearningRecords() = runBlocking<Unit> {
        content.syncBundled()
        val q1 = content.snapshot.value!!.questions.first()
        answer(q1.id, QuizMode.BASIC, false, 1_000)
        answer(q1.id, QuizMode.BASIC, true, 2_000)
        val before = study.currentSnapshot()

        val base = TestContent.packs.first { it.concepts.isNotEmpty() }.concepts.first { it.id == "D03" }
        val pack = ContentPack(
            contentVersion = 2, packId = "v2-test",
            concepts = listOf(
                base.copy(id = "D37", koreanName = "테스트 신규 개념", sortOrder = 9999),
                base.copy(definition = base.definition + " (보완)", revision = base.revision + 1),
            ),
            questions = listOf(
                com.cpaconceptlab.study.data.model.QuestionDto(
                    id = "D37-Q01", primaryConceptId = "D37", conceptIds = listOf("D37"), subjectId = "FA", chapterId = "FA-D",
                    questionText = "새로 추가된 개념의 시험용 정답은 $BLANK_TOKEN 이다.", blankAnswer = "신규", explanation = "추가 문항",
                ),
            ),
        )
        val r = ContentImporter(db).import(listOf(pack), ContentImporter.SOURCE_BUNDLED)
        assertTrue(r.message, r.success)
        assertEquals(1, r.insertedConcepts); assertEquals(1, r.updatedConcepts); assertEquals(1, r.insertedQuestions)
        content.reload()
        val snap = content.snapshot.value!!
        assertEquals(TestContent.manifest.conceptCount + 1, snap.concepts.size)
        assertTrue(snap.conceptById.getValue("D03").definition.endsWith("(보완)"))
        // 학습기록(정답률·오답노트·학습일·복습일) 그대로 유지
        val after = study.currentSnapshot()
        assertEquals(before.questionProgress, after.questionProgress)
        assertEquals(before.wrongNotes, after.wrongNotes)
        assertEquals(before.studyDays, after.studyDays)
        assertEquals(before.conceptProgress.size + 1, after.conceptProgress.size)  // 새 개념만 초기 상태 생성
        // 통계 분모는 새 개념을 포함해 재계산
        val ov = StatsCalculator.overview(snap, after, 3_000)
        assertEquals(TestContent.manifest.conceptCount + 1, ov.totalConcepts)
        assertEquals(1, ov.solvedQuestions)
    }

    // 오류가 있는 팩은 트랜잭션 전에 거부되어 기존 데이터가 변하지 않는다
    @Test fun invalidPackLeavesDatabaseUnchanged() = runBlocking<Unit> {
        content.syncBundled()
        val conceptsBefore = db.contentDao().concepts()
        val bad = """{"formatVersion":1,"contentVersion":2,"packId":"bad","questions":[{"id":"X-Q01","primaryConceptId":"NOPE","conceptIds":["NOPE"],"subjectId":"FA","chapterId":"FA-A","questionText":"빈칸 없음","blankAnswer":"","explanation":""}]}"""
        val r = content.importUserPack(bad, "bad.json")
        assertFalse(r.success)
        assertEquals(conceptsBefore, db.contentDao().concepts())
        assertNull(db.contentDao().questions().firstOrNull { it.id == "X-Q01" })
        val broken = content.importUserPack("{ not json", "broken.json")
        assertFalse(broken.success)
    }

    // 오답 기록 → 오답 복습 출제 → 다시 맞혀도 과거 오답 이력 유지
    @Test fun wrongNoteHistoryIsKeptAfterResolution() = runBlocking<Unit> {
        content.syncBundled()
        val snap = content.snapshot.value!!
        val qs = snap.questions.take(3)
        answer(qs[0].id, QuizMode.BASIC, false, 1_000)
        answer(qs[0].id, QuizMode.BASIC, false, 2_000)
        answer(qs[1].id, QuizMode.BASIC, false, 3_000)
        val user = study.currentSnapshot()
        val picked = QuestionSelector().select(snap.questions, QuizMode.BASIC, ScopeType.WRONG, 10, user.progressMap, user.wrongNotes, 10_000)
        assertEquals(listOf(qs[0].id, qs[1].id), picked.map { it.id })

        answer(qs[0].id, QuizMode.BASIC, true, 20_000)
        val note = db.progressDao().wrongNote(qs[0].id, "BASIC")!!
        assertTrue(note.isResolved)
        assertEquals(2, note.wrongCount)
        assertEquals(1_000, note.firstWrongAt)
        assertEquals(4, db.progressDao().attemptCount())
        val p = db.progressDao().questionProgress(qs[0].id, "BASIC")!!
        assertEquals(3, p.totalAttempts); assertEquals(1, p.correctAttempts); assertEquals(2, p.wrongAttempts)
        // 기본·심화 진도는 분리
        assertNull(db.progressDao().questionProgress(qs[0].id, "ADVANCED"))
    }

    // 학습기록 저장 및 복원(백업 → 삭제 → 복원) + 병합 시 중복 없음
    @Test fun backupAndRestoreRoundTrip() = runBlocking<Unit> {
        content.syncBundled()
        val snap = content.snapshot.value!!
        snap.questions.take(5).forEachIndexed { i, q -> answer(q.id, if (i % 2 == 0) QuizMode.BASIC else QuizMode.ADVANCED, i != 2, 1_000L * (i + 1)) }
        study.saveMemo("D03", "메모 테스트")
        val before = study.currentSnapshot()
        val attemptsBefore = db.progressDao().allAttempts().map { Triple(it.questionId, it.mode, it.attemptedAt) }
        val mgr = BackupManager(db)
        val json = mgr.export(BackupKind.FULL, UserSettings(fontScale = 1.2f), 1, snap.bundledVersion, 99_000)

        // 앱 삭제 후 재설치와 같은 상황: 새 DB
        db.close()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        content = ContentRepository(db, BundledAssetContentSource(ctx.assets)); study = StudyRepository(db)
        content.syncBundled()
        val ins = mgr.let { BackupManager(db) }.inspect(json, snap.allConcepts.map { it.id }.toSet(), snap.allQuestions.map { it.id }.toSet(), snap.bundledVersion)
        val res = BackupManager(db).restore(ins, RestoreMode.REPLACE)
        assertEquals(1.2f, res.settings!!.fontScale)
        val after = study.currentSnapshot()
        assertEquals(before.questionProgress.toSet(), after.questionProgress.toSet())
        assertEquals(before.wrongNotes.toSet(), after.wrongNotes.toSet())
        assertEquals(before.studyDays.toSet(), after.studyDays.toSet())
        assertEquals("메모 테스트", after.conceptProgressMap.getValue("D03").memo)
        assertEquals(attemptsBefore.toSet(), db.progressDao().allAttempts().map { Triple(it.questionId, it.mode, it.attemptedAt) }.toSet())

        // 같은 백업을 병합으로 다시 가져와도 풀이기록이 중복되지 않음
        BackupManager(db).restore(ins, RestoreMode.MERGE)
        assertEquals(attemptsBefore.size, db.progressDao().attemptCount())
    }

    // 손상된 백업 파일은 앱 종료 없이 오류 메시지로 처리
    @Test fun corruptedBackupIsRejectedGracefully() = runBlocking<Unit> {
        content.syncBundled()
        val snap = content.snapshot.value!!
        answer(snap.questions.first().id, QuizMode.BASIC, true, 1_000)
        val mgr = BackupManager(db)
        val json = mgr.export(BackupKind.RECORDS, null, 1, 1, 5_000)
        val ids = snap.allConcepts.map { it.id }.toSet() to snap.allQuestions.map { it.id }.toSet()
        fun expectFail(text: String) {
            try { mgr.inspect(text, ids.first, ids.second, 1); fail("거부되어야 함") } catch (e: BackupManager.BackupException) { assertTrue(e.message!!.isNotBlank()) }
        }
        expectFail("")
        expectFail("{garbage")
        expectFail(json.substring(0, json.length / 2))                       // 잘린 파일
        expectFail(json.replace("\"totalAttempts\":1", "\"totalAttempts\":9"))  // 내용 변조 → 체크섬 불일치
        expectFail(json.replace("\"backupFormatVersion\":1", "\"backupFormatVersion\":99"))  // 미래 형식
        expectFail(json.replace("com.cpaconceptlab.study", "other.app"))
        // 존재하지 않는 ID 는 경고로 보고하고 기록은 보존
        val inspection = mgr.inspect(json, emptySet(), emptySet(), 1)
        assertTrue(inspection.unknownQuestionIds > 0)
        assertTrue(inspection.warnings.isNotEmpty())
    }

    // DB 마이그레이션 1 → 2: 내보낸 v1 스키마로 실제 DB 를 만들고 데이터를 넣은 뒤 Room v2 로 열어 보존 확인
    @Test fun migration1To2KeepsData() {
        val schemaDir = File(System.getProperty("schemaDir") ?: "schemas", AppDatabase::class.java.name)
        val v1 = Json.parseToJsonElement(File(schemaDir, "1.json").readText()).jsonObject["database"]!!.jsonObject
        val name = "migration-test.db"
        ctx.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    v1["entities"]!!.jsonArray.forEach { e ->
                        val o = e.jsonObject
                        val table = o["tableName"]!!.jsonPrimitive.content
                        db.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                        (o["indices"] as? kotlinx.serialization.json.JsonArray)?.forEach { idx ->
                            db.execSQL(idx.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                        }
                    }
                    v1["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO concept_progress (conceptId, firstStudiedAt, lastStudiedAt, viewCount, basicAttemptCount, basicCorrectCount, advancedAttemptCount, advancedCorrectCount, masteryLevel, nextReviewAt, isBookmarked) VALUES ('D03', 1, 2, 3, 4, 3, 2, 1, 'BASIC_MASTERED', 99, 1)")
            execSQL("INSERT INTO question_progress VALUES ('D03-Q01','BASIC',1,4,3,1,2,5,1,4000,2,999,0.7)")
            execSQL("INSERT INTO wrong_notes VALUES ('D03-Q01','BASIC','오답',1,1,2,1,5)")
            execSQL("INSERT INTO study_days VALUES (20000, 4, 3, 16000)")
            close()
        }
        val migrated = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        runBlocking {
            val cp = migrated.progressDao().conceptProgress("D03")!!
            assertEquals(3, cp.viewCount); assertEquals("BASIC_MASTERED", cp.masteryLevel); assertTrue(cp.isBookmarked)
            assertEquals("", cp.memo)   // v2 신규 컬럼 기본값
            assertEquals(4, migrated.progressDao().questionProgress("D03-Q01", "BASIC")!!.totalAttempts)
            assertEquals(1, migrated.progressDao().allWrongNotes().size)
            assertEquals(4, migrated.progressDao().allStudyDays().single().solvedCount)
        }
        migrated.close()
        ctx.deleteDatabase(name)
    }

    /** 내보낸 스키마 JSON(version)으로 실제 SQLite 파일을 만든다. */
    private fun createDbFromSchema(name: String, version: Int): Pair<SupportSQLiteOpenHelper, JsonObject> {
        val schemaDir = File(System.getProperty("schemaDir") ?: "schemas", AppDatabase::class.java.name)
        val schema = Json.parseToJsonElement(File(schemaDir, "$version.json").readText()).jsonObject["database"]!!.jsonObject
        ctx.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    schema["entities"]!!.jsonArray.forEach { e ->
                        val o = e.jsonObject
                        val table = o["tableName"]!!.jsonPrimitive.content
                        db.execSQL(o["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                        (o["indices"] as? kotlinx.serialization.json.JsonArray)?.forEach { idx ->
                            db.execSQL(idx.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table))
                        }
                    }
                    schema["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        return helper to schema
    }

    /** 스키마의 모든 NOT NULL 컬럼을 채운 INSERT 문(지정하지 않은 값은 형식별 기본값) */
    private fun insertSql(schema: JsonObject, table: String, values: Map<String, Any?>): String {
        val entity = schema["entities"]!!.jsonArray.map { it.jsonObject }.first { it["tableName"]!!.jsonPrimitive.content == table }
        val cols = entity["fields"]!!.jsonArray.map { it.jsonObject }
        val names = cols.map { it["columnName"]!!.jsonPrimitive.content }
        val vals = cols.map { f ->
            val n = f["columnName"]!!.jsonPrimitive.content
            val v = if (n in values) values[n] else when (f["affinity"]!!.jsonPrimitive.content) {
                "INTEGER", "REAL" -> 0
                else -> if (f["notNull"]!!.jsonPrimitive.content == "true") (if (n.endsWith("Ids") || n == "acceptedAnswers" || n.endsWith("Numbers") || n == "conceptIds") "[]" else "") else null
            }
            when (v) { null -> "NULL"; is Number -> v.toString(); is Boolean -> if (v) "1" else "0"; else -> "'" + v.toString().replace("'", "''") + "'" }
        }
        return "INSERT INTO $table (${names.joinToString(",")}) VALUES (${vals.joinToString(",")})"
    }

    // DB 마이그레이션 2 → 3 + v3 번들 콘텐츠 동기화: 1.0.1(v2) 사용자의 학습기록·오답·북마크·메모·복습일정이 모두 보존되는지
    @Test fun migration2To3KeepsLearningRecordsAndAddsNewContent() = runBlocking<Unit> {
        val name = "migration23-test.db"
        val (helper, v2) = createDbFromSchema(name, 2)
        helper.writableDatabase.apply {
            // 1.0.1 에 있던 콘텐츠 일부(기존 개념·문항)와 번들 버전 2 기록
            execSQL(insertSql(v2, "chapters", mapOf("id" to "FA-D", "code" to "D", "subjectId" to "FA", "title" to "질적 특성", "englishTitle" to "", "sortOrder" to 4)))
            execSQL(insertSql(v2, "concepts", mapOf("id" to "D03", "subjectId" to "FA", "chapterId" to "FA-D", "sectionId" to "FA-D-S01",
                "koreanName" to "목적적합성", "definition" to "구 정의", "reviewStatus" to "REVIEWED", "isRequired" to 1, "isActive" to 1, "revision" to 2, "contentVersion" to 2)))
            execSQL(insertSql(v2, "questions", mapOf("id" to "D03-Q01", "primaryConceptId" to "D03", "conceptIds" to "[\"D03\"]", "subjectId" to "FA",
                "chapterId" to "FA-D", "questionType" to "definition", "questionText" to "구 문항 (______)", "blankAnswer" to "목적적합성",
                "acceptedAnswers" to "[\"목적적합성\"]", "explanation" to "해설", "difficulty" to 1, "reviewStatus" to "REVIEWED", "isActive" to 1, "revision" to 2, "contentVersion" to 2)))
            execSQL(insertSql(v2, "content_versions", mapOf("version" to 2, "source" to "bundled", "packId" to "fa", "importedAt" to 1,
                "conceptCount" to 285, "questionCount" to 600)))
            // 사용자 학습기록
            execSQL(insertSql(v2, "concept_progress", mapOf("conceptId" to "D03", "firstStudiedAt" to 1, "lastStudiedAt" to 2, "viewCount" to 3,
                "basicAttemptCount" to 4, "basicCorrectCount" to 3, "advancedAttemptCount" to 2, "advancedCorrectCount" to 1,
                "masteryLevel" to "BASIC_MASTERED", "nextReviewAt" to 99, "isBookmarked" to 1, "memo" to "내 메모")))
            execSQL("INSERT INTO question_progress VALUES ('D03-Q01','BASIC',1,4,3,1,2,5,1,4000,2,999,0.7)")
            execSQL("INSERT INTO question_attempts (questionId, mode, userAnswer, isCorrect, attemptedAt, responseTimeMs) VALUES ('D03-Q01','BASIC','목적적합성',1,5,4000)")
            execSQL("INSERT INTO wrong_notes VALUES ('D03-Q01','ADVANCED','충실한 표현',2,1,3,0,NULL)")
            execSQL("INSERT INTO study_days VALUES (20000, 4, 3, 16000)")
            close()
        }
        val migrated = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        // 마이그레이션 직후: 새 컬럼 기본값
        val q = migrated.contentDao().questions().single()
        assertEquals("text", q.inputType)
        assertTrue(q.choices.isEmpty() && q.slotLabels.isEmpty() && q.calcFormulaId == null)
        assertEquals("AUTHOR_REVIEWED", migrated.contentDao().concepts().single().verificationStatus)
        assertEquals("", migrated.contentDao().chapters().single().partTitle)

        // 새 APK(콘텐츠 v3) 첫 실행 동기화
        val repo = ContentRepository(migrated, BundledAssetContentSource(ctx.assets))
        val r = repo.syncBundled()
        assertNotNull(r); assertTrue(r!!.message, r.success)
        val snap = repo.snapshot.value!!
        assertEquals(TestContent.manifest.conceptCount, snap.concepts.size)
        assertEquals(TestContent.manifest.questionCount, snap.questions.size)
        assertEquals(42, snap.problemSources.size)
        assertTrue(snap.scopeItems.size >= 700)
        assertEquals("재고자산", snap.chapterById["FA-L"]!!.partTitle)
        // v7: 기존 재무회계 대단원은 중급회계 '제1부 재무회계의 기초'로 재분류(대단원 ID 유지)
        assertEquals("제1부 재무회계의 기초", snap.chapterById["FA-D"]!!.partTitle)
        assertEquals("IA", snap.chapterById["FA-D"]!!.subjectId)
        assertTrue(snap.questions.any { it.inputType == "choice" } && snap.questions.any { it.inputType == "slots" })
        assertTrue(snap.questions.any { it.calcFormulaId != null })

        // 학습기록 보존
        val pd = migrated.progressDao()
        val cp = pd.conceptProgress("D03")!!
        assertEquals(3, cp.viewCount); assertEquals("BASIC_MASTERED", cp.masteryLevel); assertTrue(cp.isBookmarked); assertEquals("내 메모", cp.memo)
        assertEquals(99L, cp.nextReviewAt)
        val qp = pd.questionProgress("D03-Q01", "BASIC")!!
        assertEquals(4, qp.totalAttempts); assertEquals(999L, qp.nextReviewAt); assertEquals(2, qp.stage)
        val wn = pd.allWrongNotes().single()
        assertEquals(2, wn.wrongCount); assertFalse(wn.isResolved)
        assertEquals(4, pd.allStudyDays().single().solvedCount)
        // 새 개념은 미학습 초기 상태로 추가된다
        assertEquals("NOT_STARTED", pd.conceptProgress("P044")!!.masteryLevel)
        assertEquals(TestContent.manifest.conceptCount, pd.allConceptProgress().size)

        // 같은 콘텐츠로 다시 동기화해도 중복이 생기지 않는다
        migrated.openHelper.writableDatabase.execSQL("UPDATE content_versions SET version = 2")
        repo.syncBundled()
        assertEquals(TestContent.manifest.questionCount, migrated.contentDao().questions().size)
        assertEquals(42, migrated.contentDao().problemSources().size)
        migrated.close()
        ctx.deleteDatabase(name)
    }

    // DB 마이그레이션 3 → 4 + v4(기업법) 번들 동기화: 1.0.2(v3) 사용자의 학습기록·오답·북마크·메모·복습일정이 모두 보존되는지
    @Test fun migration3To4KeepsLearningRecordsAndAddsCorporateLaw() = runBlocking<Unit> {
        val name = "migration34-test.db"
        val (helper, v3) = createDbFromSchema(name, 3)
        helper.writableDatabase.apply {
            execSQL(insertSql(v3, "subjects", mapOf("id" to "FA", "name" to "재무회계", "description" to "", "sortOrder" to 1, "isActive" to 1)))
            execSQL(insertSql(v3, "subjects", mapOf("id" to "BL", "name" to "기업법", "description" to "콘텐츠 팩 추가 시 활성화", "sortOrder" to 7, "isActive" to 0)))
            execSQL(insertSql(v3, "chapters", mapOf("id" to "FA-L", "code" to "L", "subjectId" to "FA", "title" to "재고자산", "englishTitle" to "", "sortOrder" to 12, "partTitle" to "재고자산")))
            execSQL(insertSql(v3, "concepts", mapOf("id" to "L001", "subjectId" to "FA", "chapterId" to "FA-L", "sectionId" to "FA-L-S01",
                "koreanName" to "재고자산의 정의", "definition" to "정의", "reviewStatus" to "REVIEWED", "isRequired" to 1, "isActive" to 1, "revision" to 99, "contentVersion" to 3)))
            execSQL(insertSql(v3, "questions", mapOf("id" to "L001-Q01", "primaryConceptId" to "L001", "conceptIds" to "[\"L001\"]", "subjectId" to "FA",
                "chapterId" to "FA-L", "questionType" to "definition", "questionText" to "v3 문항 (______)", "blankAnswer" to "재고자산",
                "acceptedAnswers" to "[\"재고자산\"]", "explanation" to "해설", "difficulty" to 1, "reviewStatus" to "REVIEWED", "isActive" to 1, "revision" to 1, "contentVersion" to 3)))
            execSQL(insertSql(v3, "content_versions", mapOf("version" to 3, "source" to "bundled", "packId" to "fa", "importedAt" to 1,
                "conceptCount" to 462, "questionCount" to 1127)))
            execSQL(insertSql(v3, "concept_progress", mapOf("conceptId" to "L001", "firstStudiedAt" to 1, "lastStudiedAt" to 2, "viewCount" to 7,
                "basicAttemptCount" to 4, "basicCorrectCount" to 3, "advancedAttemptCount" to 2, "advancedCorrectCount" to 1,
                "masteryLevel" to "ADVANCED_PRACTICING", "nextReviewAt" to 77, "isBookmarked" to 1, "memo" to "저가법 주의")))
            execSQL("INSERT INTO question_progress VALUES ('L001-Q01','ADVANCED',1,6,4,2,3,5,0,4000,3,888,0.6)")
            execSQL("INSERT INTO question_attempts (questionId, mode, userAnswer, isCorrect, attemptedAt, responseTimeMs) VALUES ('L001-Q01','ADVANCED','재고',0,5,4000)")
            execSQL("INSERT INTO wrong_notes VALUES ('L001-Q01','ADVANCED','재고',3,1,3,0,NULL)")
            execSQL("INSERT INTO study_days VALUES (20400, 9, 6, 26000)")
            close()
        }
        val migrated = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        // 마이그레이션 직후: 새 컬럼 기본값, 자리표시자 과목 BL 제거, 기존 콘텐츠 유지
        val c0 = migrated.contentDao().concepts().single()
        assertTrue(c0.articleRefs.isEmpty() && c0.lawName == null && c0.examArea == null)
        assertNull(migrated.contentDao().questions().single().answerVerification)
        assertEquals(listOf("FA"), migrated.contentDao().subjects().map { it.id })
        assertTrue(migrated.contentDao().lawArticles().isEmpty())

        // 새 APK(콘텐츠 v4) 첫 실행 동기화
        val repo = ContentRepository(migrated, BundledAssetContentSource(ctx.assets))
        val r = repo.syncBundled()
        assertNotNull(r); assertTrue(r!!.message, r.success)
        val snap = repo.snapshot.value!!
        assertEquals(TestContent.manifest.conceptCount, snap.concepts.size)
        assertEquals(TestContent.manifest.questionCount, snap.questions.size)
        assertTrue(snap.subjects.any { it.id == "CPA-LAW" && it.name == "기업법" && it.isActive })
        assertTrue(snap.subjects.none { it.id == "BL" })
        assertEquals(TestContent.packs.sumOf { it.lawArticles.size }, snap.lawArticles.size)
        assertEquals(4, snap.examAreas.count { it.subjectId == "CPA-LAW" })
        assertEquals(5, snap.examAreas.count { it.subjectId == "TX" })   // v6: 세법개론 실전 비중 영역
        assertTrue(snap.numericRules.size >= 150)
        assertTrue(snap.concepts.count { it.subjectId == "CPA-LAW" } >= 250)
        // 기기에 있는 개념의 revision 이 번들보다 같거나 높으면 덮어쓰지 않는다
        assertEquals("정의", snap.conceptById["L001"]!!.definition)

        // 학습기록 보존
        val pd = migrated.progressDao()
        val cp = pd.conceptProgress("L001")!!
        assertEquals(7, cp.viewCount); assertEquals("ADVANCED_PRACTICING", cp.masteryLevel); assertTrue(cp.isBookmarked); assertEquals("저가법 주의", cp.memo)
        assertEquals(77L, cp.nextReviewAt)
        val qp = pd.questionProgress("L001-Q01", "ADVANCED")!!
        assertEquals(6, qp.totalAttempts); assertEquals(888L, qp.nextReviewAt); assertEquals(3, qp.stage)
        val wn = pd.allWrongNotes().single()
        assertEquals(3, wn.wrongCount); assertFalse(wn.isResolved)
        assertEquals(9, pd.allStudyDays().single().solvedCount)
        // 새 기업법 개념은 미학습 초기 상태로 추가된다
        assertEquals("NOT_STARTED", pd.conceptProgress("LD045")!!.masteryLevel)
        assertEquals(TestContent.manifest.conceptCount, pd.allConceptProgress().size)

        // 다시 동기화해도 중복이 생기지 않는다
        migrated.openHelper.writableDatabase.execSQL("UPDATE content_versions SET version = 3")
        repo.syncBundled()
        assertEquals(TestContent.manifest.questionCount, migrated.contentDao().questions().size)
        assertEquals(TestContent.packs.sumOf { it.lawArticles.size }, migrated.contentDao().lawArticles().size)
        migrated.close()
        ctx.deleteDatabase(name)
    }

    // DB 마이그레이션 4 → 5 + v5 번들(전 과목 팩) 동기화: 1.0.3 사용자의 학습기록이 모두 보존되는지
    @Test fun migration4To5KeepsLearningRecordsAndAddsPacks() = runBlocking<Unit> {
        val name = "migration45-test.db"
        val (helper, v4) = createDbFromSchema(name, 4)
        helper.writableDatabase.apply {
            execSQL(insertSql(v4, "subjects", mapOf("id" to "FA", "name" to "재무회계", "description" to "", "sortOrder" to 1, "isActive" to 1)))
            execSQL(insertSql(v4, "subjects", mapOf("id" to "CPA-LAW", "name" to "기업법", "description" to "", "sortOrder" to 7, "isActive" to 1)))
            execSQL(insertSql(v4, "content_versions", mapOf("version" to 4, "source" to "bundled", "packId" to "fa", "importedAt" to 1,
                "conceptCount" to 758, "questionCount" to 2282)))
            execSQL(insertSql(v4, "concept_progress", mapOf("conceptId" to "LD045", "firstStudiedAt" to 1, "lastStudiedAt" to 2, "viewCount" to 2,
                "basicAttemptCount" to 3, "basicCorrectCount" to 2, "advancedAttemptCount" to 1, "advancedCorrectCount" to 1,
                "masteryLevel" to "ADVANCED_MASTERED", "nextReviewAt" to 55, "isBookmarked" to 1, "memo" to "특별결의 2/3·1/3")))
            execSQL("INSERT INTO question_progress VALUES ('LD045-Q01','BASIC',1,3,2,1,1,5,1,4000,2,777,0.5)")
            execSQL("INSERT INTO wrong_notes VALUES ('LD045-Q01','BASIC','과반수',1,1,3,0,NULL)")
            execSQL("INSERT INTO study_days VALUES (20500, 7, 5, 21000)")
            close()
        }
        val migrated = Room.databaseBuilder(ctx, AppDatabase::class.java, name)
            .addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        assertEquals(setOf("FA", "CPA-LAW"), migrated.contentDao().subjects().map { it.id }.toSet())
        // 4→5→6: FA 행은 삭제되지 않고 레거시(비활성)로 남는다
        assertEquals("LEGACY", migrated.contentDao().subjects().single { it.id == "FA" }.examSubject)
        assertEquals("", migrated.contentDao().subjects().single { it.id == "CPA-LAW" }.examSubject)
        assertTrue(migrated.contentDao().curriculumItems().isEmpty())

        val repo = ContentRepository(migrated, BundledAssetContentSource(ctx.assets))
        val r = repo.syncBundled()
        assertNotNull(r); assertTrue(r!!.message, r.success)
        val snap = repo.snapshot.value!!
        assertEquals(TestContent.manifest.conceptCount, snap.concepts.size)
        // v7: 재무회계(FA)는 레거시(비활성·LEGACY)로 맨 뒤, 사용자 화면에는 표시하지 않는다
        assertEquals(listOf("IA", "AA", "CM", "GA", "FM", "HR", "EC", "CPA-LAW", "TX", "FA"), snap.subjects.sortedBy { it.sortOrder }.map { it.id })
        val fa = snap.subjects.single { it.id == "FA" }
        assertFalse(fa.isActive); assertEquals("LEGACY", fa.examSubject)
        assertTrue(snap.concepts.none { it.subjectId == "FA" })
        assertEquals("ACC", snap.subjects.single { it.id == "CM" }.examSubject)
        assertTrue(snap.subjects.single { it.id == "FM" }.isActive)
        assertTrue(snap.subjects.single { it.id == "TX" }.isActive)   // v6: 세법개론 활성
        assertTrue(snap.curriculumItems.size > 1000)

        val pd = migrated.progressDao()
        val cp = pd.conceptProgress("LD045")!!
        assertEquals("ADVANCED_MASTERED", cp.masteryLevel); assertEquals("특별결의 2/3·1/3", cp.memo); assertTrue(cp.isBookmarked); assertEquals(55L, cp.nextReviewAt)
        assertEquals(777L, pd.questionProgress("LD045-Q01", "BASIC")!!.nextReviewAt)
        assertEquals(1, pd.allWrongNotes().size)
        assertEquals(7, pd.allStudyDays().single().solvedCount)
        assertEquals("NOT_STARTED", pd.conceptProgress("FM020")!!.masteryLevel)
        // 다시 동기화해도 중복 없음
        migrated.openHelper.writableDatabase.execSQL("UPDATE content_versions SET version = 4")
        repo.syncBundled()
        assertEquals(TestContent.manifest.questionCount, migrated.contentDao().questions().size)
        assertEquals(snap.curriculumItems.size, migrated.contentDao().curriculumItems().size)
        migrated.close()
        ctx.deleteDatabase(name)
    }

    /**
     * v1.0.7 핵심 검증: DB 5 → 6 + 콘텐츠 v7 동기화에서 '재무회계(FA)' 팩 해체.
     * 픽스처 = 1.0.6(git HEAD) 실제 재무회계 콘텐츠(대단원 17·중단원 96·개념 462·문항 1,127, revision 2).
     * 모든 FA 문항에 풀이기록을 만들고 352개 개념을 학습 완료 상태로 둔 뒤, 업데이트 후 기록이 1건도 바뀌지 않았는지 확인한다.
     */
    @Test fun migration5To6ReclassifiesFinancialAccountingWithoutLosingRecords() = runBlocking<Unit> {
        val fixture = Json.parseToJsonElement(File("src/test/resources/pre_v7_fa_fixture.json").readText()).jsonObject
        fun arr(k: String) = fixture[k]!!.jsonArray.map { it.jsonObject }
        fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
        val chapters = arr("chapters"); val sections = arr("sections"); val concepts = arr("concepts"); val questions = arr("questions")
        assertEquals(listOf(17, 96, 462, 1127), listOf(chapters.size, sections.size, concepts.size, questions.size))

        val name = "migration56-test.db"
        val (helper, v5) = createDbFromSchema(name, 5)
        helper.writableDatabase.apply {
            beginTransaction()
            execSQL(insertSql(v5, "subjects", mapOf("id" to "FA", "name" to "재무회계", "description" to "", "sortOrder" to 1, "isActive" to 1, "examSubject" to "ACC")))
            execSQL(insertSql(v5, "content_versions", mapOf("version" to 6, "source" to "bundled", "packId" to "fa", "importedAt" to 1,
                "conceptCount" to 1005, "questionCount" to 2956)))
            chapters.forEach { c -> execSQL(insertSql(v5, "chapters", mapOf("id" to c.str("id"), "code" to c.str("code"), "subjectId" to "FA",
                "title" to c.str("title"), "englishTitle" to c.str("englishTitle"), "sortOrder" to c.str("sortOrder").toInt(), "partTitle" to c.str("partTitle")))) }
            sections.forEach { x -> execSQL(insertSql(v5, "sections", mapOf("id" to x.str("id"), "chapterId" to x.str("chapterId"), "title" to x.str("title"), "sortOrder" to x.str("sortOrder").toInt()))) }
            concepts.forEachIndexed { i, x ->
                execSQL(insertSql(v5, "concepts", mapOf("id" to x.str("id"), "subjectId" to "FA", "chapterId" to x.str("chapterId"), "sectionId" to x.str("sectionId"),
                    "koreanName" to x.str("koreanName"), "definition" to x.str("definition"), "reviewStatus" to "REVIEWED", "isRequired" to 1, "isActive" to 1,
                    "revision" to x.str("revision").toInt(), "sortOrder" to x.str("sortOrder").toInt(), "contentVersion" to 2, "verificationStatus" to "AUTHOR_REVIEWED")))
                // 352개 개념 학습 완료(열람 + 기본 정답 + 심화 정답), 나머지는 진행 중, 일부는 북마크·메모
                val done = i < 352
                execSQL(insertSql(v5, "concept_progress", mapOf("conceptId" to x.str("id"), "firstStudiedAt" to 1000 + i, "lastStudiedAt" to 2000 + i,
                    "viewCount" to if (done) 2 else 1, "basicAttemptCount" to 3, "basicCorrectCount" to 2, "advancedAttemptCount" to if (done) 2 else 1,
                    "advancedCorrectCount" to if (done) 1 else 0, "masteryLevel" to if (done) "ADVANCED_MASTERED" else "BASIC_PRACTICING",
                    "nextReviewAt" to 9_000_000L + i, "isBookmarked" to (i % 10 == 0), "memo" to if (i % 25 == 0) "메모 $i" else "")))
            }
            questions.forEachIndexed { i, x ->
                val qid = x.str("id")
                execSQL(insertSql(v5, "questions", mapOf("id" to qid, "primaryConceptId" to x.str("primaryConceptId"), "conceptIds" to "[\"${x.str("primaryConceptId")}\"]",
                    "subjectId" to "FA", "chapterId" to x.str("chapterId"), "questionType" to x.str("questionType"), "questionText" to "(______)",
                    "blankAnswer" to x.str("blankAnswer"), "acceptedAnswers" to "[]", "difficulty" to x.str("difficulty").toInt(), "reviewStatus" to "REVIEWED",
                    "isActive" to 1, "revision" to x.str("revision").toInt(), "sortOrder" to x.str("sortOrder").toInt(), "contentVersion" to 2,
                    "inputType" to x.str("inputType"), "choices" to "[]", "slotLabels" to "[]", "slotAnswers" to "[]", "sourceProblemIds" to "[]")))
                // 모든 FA 문항에 풀이기록: 시도 3회(정답 2·오답 1) + 심화 1회
                val ok = i % 3 != 0
                execSQL("INSERT INTO question_attempts (questionId, mode, userAnswer, isCorrect, attemptedAt, responseTimeMs) VALUES ('$qid','BASIC','a',1,${10_000 + i},3000)")
                execSQL("INSERT INTO question_attempts (questionId, mode, userAnswer, isCorrect, attemptedAt, responseTimeMs) VALUES ('$qid','BASIC','b',0,${20_000 + i},4000)")
                execSQL("INSERT INTO question_attempts (questionId, mode, userAnswer, isCorrect, attemptedAt, responseTimeMs) VALUES ('$qid','BASIC','c',1,${30_000 + i},5000)")
                execSQL("INSERT INTO question_progress VALUES ('$qid','BASIC',10000,3,2,1,1,${30_000 + i},1,12000,2,${50_000_000L + i},0.6)")
                execSQL("INSERT INTO question_attempts (questionId, mode, userAnswer, isCorrect, attemptedAt, responseTimeMs) VALUES ('$qid','ADVANCED','d',${if (ok) 1 else 0},${40_000 + i},6000)")
                execSQL("INSERT INTO question_progress VALUES ('$qid','ADVANCED',40000,1,${if (ok) 1 else 0},${if (ok) 0 else 1},${if (ok) 1 else 0},${40_000 + i},${if (ok) 1 else 0},6000,${if (ok) 1 else 0},${60_000_000L + i},0.5)")
                if (!ok) execSQL("INSERT INTO wrong_notes VALUES ('$qid','ADVANCED','d',1,${40_000 + i},${40_000 + i},0,NULL)")
            }
            execSQL("INSERT INTO study_days VALUES (20500, 4508, 3005, 9999)")
            setTransactionSuccessful(); endTransaction()
            close()
        }

        val migrated = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        val pd = migrated.progressDao()
        val beforeAttempts = pd.allAttempts(); val beforeQp = pd.allQuestionProgress().toSet(); val beforeCp = pd.allConceptProgress().toSet()
        val beforeWrong = pd.allWrongNotes().toSet(); val beforeDays = pd.allStudyDays()
        assertEquals(1127 * 4, beforeAttempts.size)
        // 마이그레이션 직후(동기화 전): ID 유지, 소속만 IA, FA 행은 레거시로 남음
        assertTrue(migrated.contentDao().concepts().none { it.subjectId == "FA" })
        assertEquals(462, migrated.contentDao().concepts().count { it.legacyPackId == "FA" })
        assertEquals("LEGACY", migrated.contentDao().subjects().single { it.id == "FA" }.examSubject)

        // 새 APK 첫 실행 동기화(v7)
        val repo = ContentRepository(migrated, BundledAssetContentSource(ctx.assets))
        val r = repo.syncBundled()
        assertNotNull(r); assertTrue(r!!.message, r.success)
        val snap = repo.snapshot.value!!
        val faIds = concepts.map { it.str("id") }.toSet()
        val faQ = questions.map { it.str("id") }.toSet()
        // 모든 기존 개념·문항이 그대로 존재(ID 유지) — 유실 0
        assertEquals(faIds, snap.allConcepts.filter { it.id in faIds }.map { it.id }.toSet())
        assertEquals(faQ, snap.allQuestions.filter { it.id in faQ && it.isActive }.map { it.id }.toSet())
        // 새 소속: 중급회계 459 · 고급회계 3(B13~B15, AA-A) · 정부회계 0 · 재무회계 0
        val bySubject = snap.allConcepts.filter { it.id in faIds }.groupingBy { it.subjectId }.eachCount()
        assertEquals(mapOf("IA" to 459, "AA" to 3), bySubject)
        listOf("B13", "B14", "B15").forEach { assertEquals("AA-A", snap.conceptById[it]!!.chapterId) }
        assertTrue(snap.allQuestions.filter { it.id in faQ }.none { it.subjectId == "FA" })
        assertEquals("제1부 재무회계의 기초", snap.chapterById["FA-A"]!!.partTitle)
        // 간단 요약(기존 정의 보존) + 상세 학습
        val p037 = snap.conceptById["P037"]!!
        assertTrue(p037.shortSummary!!.contains(concepts.first { it.str("id") == "P037" }.str("definition")))
        assertTrue(p037.detailSectionList().any { it.source == "AUTHORED_V7" && it.journal.isNotEmpty() })

        // 학습기록: 1건도 바뀌지 않음(풀이·정답·오답 횟수, 복습일, 오답노트, 북마크·메모, 학습일)
        assertEquals(beforeAttempts, pd.allAttempts())
        assertEquals(beforeQp, pd.allQuestionProgress().filter { it.questionId in faQ }.toSet())
        assertEquals(beforeCp, pd.allConceptProgress().filter { it.conceptId in faIds }.toSet())
        assertEquals(beforeWrong, pd.allWrongNotes().toSet())
        assertEquals(beforeDays, pd.allStudyDays())
        assertEquals(1127 * 4, pd.attemptCount())
        assertEquals(beforeAttempts.count { it.isCorrect }, pd.allAttempts().count { it.isCorrect })
        // 진도: 기존 학습 완료 352개가 새 구조(중급·고급회계)에서도 그대로 집계되고 중복 없음
        val user = StudyRepository(migrated).currentSnapshot()
        val acc = com.cpaconceptlab.study.domain.usecase.ExamSubjectProgress.summary(snap, user).single { it.id == "ACC" }
        assertEquals(352, acc.completedConcepts)
        val packs = com.cpaconceptlab.study.domain.usecase.ExamSubjectProgress.detail("ACC", snap, user).packs
        assertEquals(352, packs.sumOf { it.stat.completedConcepts })
        assertTrue(packs.none { it.stat.id == "FA" })

        // 반복 동기화해도 중복·변경 없음(멱등)
        migrated.openHelper.writableDatabase.execSQL("UPDATE content_versions SET version = 6")
        repo.syncBundled()
        assertEquals(TestContent.manifest.questionCount, migrated.contentDao().questions().count { it.isActive })
        assertEquals(beforeAttempts, pd.allAttempts())
        assertEquals(beforeCp, pd.allConceptProgress().filter { it.conceptId in faIds }.toSet())
        migrated.close()
        ctx.deleteDatabase(name)
    }

    // 앱 업데이트(콘텐츠 버전 상승) 시뮬레이션: 같은 DB 파일을 다시 열어 동기화해도 학습진도 유지
    @Test fun progressSurvivesAppRestartAndResync() = runBlocking<Unit> {
        val name = "update-test.db"
        ctx.deleteDatabase(name)
        var fileDb = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        var repo = ContentRepository(fileDb, BundledAssetContentSource(ctx.assets))
        repo.syncBundled()
        val q = repo.snapshot.value!!.questions[10]
        StudyRepository(fileDb).recordAnswer(q, QuizMode.ADVANCED, q.blankAnswer, true, 3000, 5_000, scheduler, mapOf(q.primaryConceptId to listOf(q.id)))
        val before = StudyRepository(fileDb).currentSnapshot()
        // 저장된 번들 버전을 낮춰 '새 버전 APK 설치' 상황을 만든다
        fileDb.openHelper.writableDatabase.execSQL("UPDATE content_versions SET version = 0")
        fileDb.close()

        fileDb = Room.databaseBuilder(ctx, AppDatabase::class.java, name).addMigrations(*AppDatabase.MIGRATIONS).allowMainThreadQueries().build()
        repo = ContentRepository(fileDb, BundledAssetContentSource(ctx.assets))
        val r = repo.syncBundled()
        assertNotNull("새 버전으로 인식해 동기화해야 함", r)
        val after = StudyRepository(fileDb).currentSnapshot()
        assertEquals(before.questionProgress, after.questionProgress)
        assertEquals(before.conceptProgress.toSet(), after.conceptProgress.toSet())
        assertEquals(before.studyDays, after.studyDays)
        assertEquals(TestContent.manifest.conceptCount, repo.snapshot.value!!.concepts.size)
        fileDb.close()
        ctx.deleteDatabase(name)
    }
}
