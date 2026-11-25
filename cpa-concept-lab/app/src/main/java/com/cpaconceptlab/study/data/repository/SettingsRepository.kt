package com.cpaconceptlab.study.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cpaconceptlab.study.domain.usecase.ReviewScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val fontScale: Float = 1.0f,
    val themeMode: String = "SYSTEM",
    val dailyGoal: Int = 30,
    val sessionSize: Int = 10,
    val reviewIntervals: String = DEFAULT_INTERVALS,
    val lastScope: String = "ALL",
    val lastMode: String = "BASIC",
    // v1.0.7
    /** 오늘의 문제 목표를 두지 않는 자유 학습 */
    val goalUnlimited: Boolean = false,
    /** FOCUS · STANDARD · RELAXED · CUSTOM */
    val reviewPreset: String = ReviewPreset.STANDARD.name,
    /** 단원별 문제풀이에서 마지막으로 고른 난이도·유형 */
    val lastDifficulty: String = "ALL",
    val lastKind: String = "ALL",
    /** 개념 화면에서 마지막으로 본 탭: SUMMARY · DETAIL */
    val lastConceptTab: String = "SUMMARY",
    /** 학습 콘텐츠 이용 안내 확인 여부(최초 실행 안내) */
    val noticeAcknowledged: Boolean = false,
) {
    val intervals: List<Int> get() = ReviewScheduler.parse(reviewIntervals)
}

/** 복습 주기 추천 설정. 간격은 '직전 복습 후 며칠 뒤'(일). */
enum class ReviewPreset(val label: String, val description: String, val intervals: List<Int>) {
    FOCUS("집중 복습", "빠르게 반복하여 암기하고 싶을 때", listOf(1, 2, 4, 7, 14, 30)),
    STANDARD("표준 복습", "일반적인 장기기억 학습", listOf(1, 3, 7, 14, 30, 60)),
    RELAXED("여유 복습", "복습 횟수를 줄이고 긴 간격으로", listOf(2, 5, 10, 21, 45, 90)),
    CUSTOM("직접 설정", "복습 간격을 직접 정합니다", emptyList());

    companion object {
        fun of(name: String?): ReviewPreset = entries.firstOrNull { it.name == name } ?: STANDARD
        /** 저장된 간격이 프리셋과 같으면 그 프리셋, 아니면 직접 설정(기존 사용자 설정 보존) */
        fun detect(intervals: List<Int>): ReviewPreset = entries.firstOrNull { it != CUSTOM && it.intervals == intervals } ?: CUSTOM
    }
}

/** 오늘의 문제 목표 입력 검증: 양의 정수, 정수 오버플로·비정상 저장 방지를 위한 기술적 상한만 둔다(학습량 상한 아님). */
object DailyGoal {
    const val MAX = 100_000
    val QUICK = listOf(20, 50, 100, 150, 200, 300, 500, 1000)
    fun parse(text: String): Result<Int> {
        val t = text.trim().replace(",", "")
        val v = t.toLongOrNull() ?: return Result.failure(IllegalArgumentException("숫자만 입력해 주세요."))
        if (v < 1) return Result.failure(IllegalArgumentException("1문제 이상으로 입력해 주세요."))
        if (v > MAX) return Result.failure(IllegalArgumentException("${"%,d".format(MAX)}문제 이하로 입력해 주세요. 더 많이 풀려면 '무제한 학습'을 선택하세요."))
        return Result.success(v.toInt())
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val FONT = floatPreferencesKey("font_scale")
        val THEME = stringPreferencesKey("theme_mode")
        val GOAL = intPreferencesKey("daily_goal")
        val SESSION = intPreferencesKey("session_size")
        val INTERVALS = stringPreferencesKey("review_intervals")
        val LAST_SCOPE = stringPreferencesKey("last_scope")
        val LAST_MODE = stringPreferencesKey("last_mode")
        val GOAL_UNLIMITED = androidx.datastore.preferences.core.booleanPreferencesKey("goal_unlimited")
        val PRESET = stringPreferencesKey("review_preset")
        val LAST_DIFF = stringPreferencesKey("last_difficulty")
        val LAST_KIND = stringPreferencesKey("last_kind")
        val LAST_TAB = stringPreferencesKey("last_concept_tab")
        val NOTICE = androidx.datastore.preferences.core.booleanPreferencesKey("notice_ack")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { p ->
        UserSettings(
            fontScale = p[Keys.FONT] ?: 1.0f,
            themeMode = p[Keys.THEME] ?: "SYSTEM",
            dailyGoal = p[Keys.GOAL] ?: 30,
            sessionSize = p[Keys.SESSION] ?: 10,
            // v1.0.2: 기본 간격에 60일 단계를 추가. 이전 기본값을 그대로 쓰던 사용자만 새 기본값으로 올리고, 직접 바꾼 간격은 유지한다.
            reviewIntervals = p[Keys.INTERVALS]?.takeIf { it != LEGACY_DEFAULT_INTERVALS } ?: DEFAULT_INTERVALS,
            lastScope = p[Keys.LAST_SCOPE] ?: "ALL",
            lastMode = p[Keys.LAST_MODE] ?: "BASIC",
            goalUnlimited = p[Keys.GOAL_UNLIMITED] ?: false,
            // 기존 사용자: 저장된 간격으로 프리셋을 추정해 설정을 그대로 유지한다
            reviewPreset = p[Keys.PRESET] ?: ReviewPreset.detect(
                ReviewScheduler.parse(p[Keys.INTERVALS]?.takeIf { it != LEGACY_DEFAULT_INTERVALS } ?: DEFAULT_INTERVALS),
            ).name,
            lastDifficulty = p[Keys.LAST_DIFF] ?: "ALL",
            lastKind = p[Keys.LAST_KIND] ?: "ALL",
            lastConceptTab = p[Keys.LAST_TAB] ?: "SUMMARY",
            noticeAcknowledged = p[Keys.NOTICE] ?: false,
        )
    }

    suspend fun current(): UserSettings = settings.first()

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        val next = transform(current())
        write(next)
    }

    suspend fun write(s: UserSettings) {
        context.dataStore.edit { p ->
            p[Keys.FONT] = s.fontScale.coerceIn(0.8f, 1.6f)
            p[Keys.THEME] = s.themeMode
            p[Keys.GOAL] = s.dailyGoal.coerceIn(1, DailyGoal.MAX)   // v1.0.7: 150·300 상한 제거(기술적 상한만)
            p[Keys.SESSION] = s.sessionSize.coerceIn(5, 100)
            p[Keys.INTERVALS] = ReviewScheduler.parse(s.reviewIntervals).joinToString(",")
            p[Keys.LAST_SCOPE] = s.lastScope
            p[Keys.LAST_MODE] = s.lastMode
            p[Keys.GOAL_UNLIMITED] = s.goalUnlimited
            p[Keys.PRESET] = ReviewPreset.of(s.reviewPreset).name
            p[Keys.LAST_DIFF] = s.lastDifficulty
            p[Keys.LAST_KIND] = s.lastKind
            p[Keys.LAST_TAB] = s.lastConceptTab
            p[Keys.NOTICE] = s.noticeAcknowledged
        }
    }
}

const val DEFAULT_INTERVALS = "1,3,7,14,30,60"
const val LEGACY_DEFAULT_INTERVALS = "1,3,7,14,30"
