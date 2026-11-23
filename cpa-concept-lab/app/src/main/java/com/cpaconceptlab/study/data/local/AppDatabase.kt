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
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contentDao(): ContentDao
    abstract fun progressDao(): ProgressDao

    companion object {
        const val VERSION = 3
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

        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
