package com.cpaconceptlab.study

import android.content.Context
import com.cpaconceptlab.study.data.backup.BackupManager
import com.cpaconceptlab.study.data.importer.BundledAssetContentSource
import com.cpaconceptlab.study.data.local.AppDatabase
import com.cpaconceptlab.study.data.repository.ContentRepository
import com.cpaconceptlab.study.data.repository.SettingsRepository
import com.cpaconceptlab.study.data.repository.StudyRepository
import com.cpaconceptlab.study.domain.TaxRuleEngine

/** 간단한 수동 의존성 컨테이너(앱 단일 인스턴스). */
class AppContainer(context: Context) {
    val db: AppDatabase = AppDatabase.build(context)
    val contentRepository = ContentRepository(db, BundledAssetContentSource(context.assets))
    val studyRepository = StudyRepository(db)
    val settingsRepository = SettingsRepository(context)
    val backupManager = BackupManager(db)

    init {
        // 세율표 데이터(콘텐츠 빌드가 법령 원문과 대조한 표). 읽지 못하면 세율표 카드·세율 계산이 비활성 상태로 남는다.
        runCatching { context.assets.open("content/tax_rules.json").bufferedReader().use { it.readText() } }
            .onSuccess { runCatching { TaxRuleEngine.install(it) } }
    }
}
