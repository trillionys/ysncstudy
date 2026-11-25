package com.cpaconceptlab.study.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SubjectEntity::class, ChapterEntity::class, SectionEntity::class, ConceptEntity::class,
        QuestionEntity::class, QuestionConceptCrossRef::class, ExamScopeEntity::class,
        ContentVersionEntity::class, ImportedPackEntity::class, ProblemSourceEntity::class, ScopeItemEntity::class,
        QuestionAttemptEntity::class, QuestionProgressEntity::class, ConceptProgressEntity::class,
        WrongNoteEntity::class, StudyDayEntity::class,
        LawArticleEntity::class, NumericRuleEntity::class, CaseNoteEntity::class, ExamAreaEntity::class, LawVersionEntity::class, CurriculumItemEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun progressDao(): ProgressDao

    companion object {
        const val VERSION = 6
        const val NAME = "cpa_concept_lab.db"

        /**
         * 모든 스키마 변경은 명시적 Migration 으로 제공한다.
         * fallbackToDestructiveMigration 은 사용하지 않는다(학습기록 보존).
         */
        /** v1 → v2: 개념별 개인 메모 컬럼 추가. 기존 학습기록은 그대로 유지된다. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE concept_progress ADD COLUMN memo TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v2 → v3: 재고자산·유형자산·차입원가 업데이트.
         * 콘텐츠 테이블에 컬럼만 추가하고(기본값 지정) 새 콘텐츠 테이블 2개를 만든다.
         * 사용자 학습기록 테이블은 전혀 건드리지 않는다.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chapters ADD COLUMN partTitle TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE concepts ADD COLUMN recognitionRules TEXT")
                db.execSQL("ALTER TABLE concepts ADD COLUMN measurementRules TEXT")
                db.execSQL("ALTER TABLE concepts ADD COLUMN exceptions TEXT")
                db.execSQL("ALTER TABLE concepts ADD COLUMN journalEntries TEXT")
                db.execSQL("ALTER TABLE concepts ADD COLUMN sourceProblemIds TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE concepts ADD COLUMN verificationStatus TEXT NOT NULL DEFAULT 'AUTHOR_REVIEWED'")
                db.execSQL("ALTER TABLE questions ADD COLUMN inputType TEXT NOT NULL DEFAULT 'text'")
                db.execSQL("ALTER TABLE questions ADD COLUMN choices TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE questions ADD COLUMN slotLabels TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE questions ADD COLUMN slotAnswers TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE questions ADD COLUMN calcFormulaId TEXT")
                db.execSQL("ALTER TABLE questions ADD COLUMN calcParams TEXT")
                db.execSQL("ALTER TABLE questions ADD COLUMN sourceProblemIds TEXT NOT NULL DEFAULT '[]'")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `problem_sources` (`sourceId` TEXT NOT NULL, `sourceFile` TEXT, `sourcePage` TEXT, " +
                        "`originalNumber` TEXT, `topic` TEXT NOT NULL, `conceptIds` TEXT NOT NULL, `formula` TEXT, `solutionSteps` TEXT, " +
                        "`keyTrap` TEXT, `linkedQuestionIds` TEXT NOT NULL, `readingConfidence` TEXT NOT NULL, " +
                        "`verificationStatus` TEXT NOT NULL, `reviewRequired` INTEGER NOT NULL, `note` TEXT, PRIMARY KEY(`sourceId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `scope_items` (`id` TEXT NOT NULL, `area` TEXT NOT NULL, `item` TEXT NOT NULL, " +
                        "`conceptId` TEXT, `covered` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * v3 → v4: 2027 공인회계사 1차 기업법(CPA-LAW) 업데이트.
         * 개념·문항 테이블에 법령 메타데이터 컬럼을 추가하고(기본값 지정) 법령 참조 테이블 5개를 만든다.
         * 기존 자리표시자 과목 'BL'(기업법, 비활성·콘텐츠 없음)은 실제 과목 'CPA-LAW' 로 대체되므로 삭제한다.
         * 사용자 학습기록 테이블(question_attempts·question_progress·concept_progress·wrong_notes·study_days)은 건드리지 않는다.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("lawName", "lawVersion", "promulgationDate", "transitionRule", "examApplicability", "sourceUrl",
                    "verifiedAt", "caseLaw", "examArea").forEach { db.execSQL("ALTER TABLE concepts ADD COLUMN `$it` TEXT") }
                db.execSQL("ALTER TABLE concepts ADD COLUMN `articleRefs` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE concepts ADD COLUMN `examYear` INTEGER")
                db.execSQL("ALTER TABLE questions ADD COLUMN `answerVerification` TEXT")
                db.execSQL("ALTER TABLE questions ADD COLUMN `examArea` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `law_articles` (`id` TEXT NOT NULL, `lawName` TEXT NOT NULL, `lawShortName` TEXT NOT NULL, " +
                        "`articleId` TEXT NOT NULL, `articleNo` INTEGER NOT NULL, `branch` INTEGER NOT NULL, `title` TEXT NOT NULL, " +
                        "`text` TEXT NOT NULL, `part` TEXT NOT NULL, `chapter` TEXT NOT NULL, `deleted` INTEGER NOT NULL, " +
                        "`inScope` INTEGER NOT NULL, `status` TEXT NOT NULL, `statusLabel` TEXT NOT NULL, `note` TEXT, `examArea` TEXT, " +
                        "`conceptIds` TEXT NOT NULL, `questionCount` INTEGER NOT NULL, `lawVersion` TEXT NOT NULL, `effectiveDate` TEXT, " +
                        "`sourceUrl` TEXT, `verifiedAt` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_law_articles_lawShortName` ON `law_articles` (`lawShortName`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `numeric_rules` (`ruleId` TEXT NOT NULL, `lawName` TEXT NOT NULL, `article` TEXT NOT NULL, " +
                        "`ruleName` TEXT NOT NULL, `subject` TEXT NOT NULL, `condition` TEXT NOT NULL, `value` TEXT NOT NULL, " +
                        "`unit` TEXT NOT NULL, `exception` TEXT, `effectiveDate` TEXT, `sourceUrl` TEXT, `conceptId` TEXT, " +
                        "`questionId` TEXT, PRIMARY KEY(`ruleId`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `case_notes` (`id` TEXT NOT NULL, `conceptId` TEXT NOT NULL, `topic` TEXT NOT NULL, " +
                        "`holding` TEXT NOT NULL, `caseNumber` TEXT, `decisionDate` TEXT, `court` TEXT NOT NULL, `articles` TEXT NOT NULL, " +
                        "`verificationStatus` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exam_areas` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `minPercent` INTEGER NOT NULL, " +
                        "`maxPercent` INTEGER NOT NULL, `mockQuestions` INTEGER NOT NULL, `questionCount` INTEGER NOT NULL, " +
                        "`subjectId` TEXT NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `law_versions` (`lawShortName` TEXT NOT NULL, `lawName` TEXT NOT NULL, " +
                        "`lawVersion` TEXT NOT NULL, `promulgationDate` TEXT, `effectiveDate` TEXT, `sourceUrl` TEXT, `verifiedAt` TEXT, " +
                        "`examYear` INTEGER, PRIMARY KEY(`lawShortName`))",
                )
                db.execSQL("DELETE FROM subjects WHERE id = 'BL' AND NOT EXISTS (SELECT 1 FROM chapters WHERE subjectId = 'BL')")
            }
        }

        /**
         * v4 → v5: CPA 1차 전 과목 확장.
         * 과목에 공식 시험과목 그룹 컬럼을 추가하고 출제범위 마스터 목차 테이블을 만든다. 학습기록 테이블은 건드리지 않는다.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subjects ADD COLUMN `examSubject` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `curriculum_items` (`id` TEXT NOT NULL, `subjectId` TEXT NOT NULL, `unit` TEXT NOT NULL, " +
                        "`item` TEXT NOT NULL, `conceptIds` TEXT NOT NULL, `covered` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_curriculum_items_subjectId` ON `curriculum_items` (`subjectId`)")
            }
        }

        /**
         * v5 → v6 (앱 1.0.7): '재무회계(FA)' 독립 팩 해체 + 간단 요약·상세 학습.
         *  - 학습기록 테이블(question_attempts·question_progress·concept_progress·wrong_notes·study_days)은 읽지도 쓰지도 않는다.
         *  - 개념·문항·대단원의 ID 는 그대로 두고 소속 과목(subjectId)만 FA → IA 로 바꾼다.
         *    (개별 개념의 고급회계 이동·새 대단원·상세 학습 내용은 직후 번들 동기화가 revision 상승분으로 반영한다.)
         *  - 레거시 FA 과목 행은 삭제하지 않고 비활성·LEGACY 로 표시한다(외래키·백업 호환).
         *  - 모든 문장은 같은 데이터에 다시 실행해도 결과가 같다(멱등).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "ALTER TABLE concepts ADD COLUMN `primaryPackId` TEXT",
                    "ALTER TABLE concepts ADD COLUMN `relatedPackIds` TEXT NOT NULL DEFAULT '[]'",
                    "ALTER TABLE concepts ADD COLUMN `classificationStatus` TEXT",
                    "ALTER TABLE concepts ADD COLUMN `legacyPackId` TEXT",
                    "ALTER TABLE concepts ADD COLUMN `shortSummary` TEXT",
                    "ALTER TABLE concepts ADD COLUMN `detailSections` TEXT",
                    "ALTER TABLE concepts ADD COLUMN `detailLevel` TEXT",
                ).forEach { db.execSQL(it) }
                // 재분류: 기존 재무회계 개념·문항·대단원을 중급회계 소속으로(ID 유지)
                db.execSQL("UPDATE concepts SET legacyPackId = 'FA', primaryPackId = 'IA', subjectId = 'IA', classificationStatus = 'CLASSIFIED' WHERE subjectId = 'FA'")
                db.execSQL("UPDATE concepts SET primaryPackId = subjectId WHERE primaryPackId IS NULL")
                db.execSQL("UPDATE questions SET subjectId = 'IA' WHERE subjectId = 'FA'")
                db.execSQL("UPDATE chapters SET subjectId = 'IA' WHERE subjectId = 'FA'")
                db.execSQL("UPDATE subjects SET isActive = 0, examSubject = 'LEGACY', name = '재무회계(이전됨)' WHERE id = 'FA'")
                // 목차 메타데이터(콘텐츠 데이터)만 정리: 재무회계 팩 목차는 중급회계 목차로 대체된다
                db.execSQL("DELETE FROM curriculum_items WHERE subjectId = 'FA'")
            }
        }

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
