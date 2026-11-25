# v7 마이그레이션 보고서 — 재무회계 팩 해체·상세 학습 (앱 1.0.7, DB 스키마 6, 콘텐츠 v7)

작성일 2026-11-25. 수치는 실제 생성 파일(`app/src/main/assets/content/*_v7.json`)과 실행한 테스트 기준이다.

## 1. 이전 상태(보존한 스냅샷)
- 앱 1.0.6(versionCode 7), DB 스키마 5, 콘텐츠 v6 — `docs/migration/pre_v7_content_snapshot.json`
- 재무회계(FA): 대단원 17 · 중단원 96 · 개념 462 · 문항 1,127 (ID 목록·SHA-256 해시 보존)
- 테스트 픽스처: `app/src/test/resources/pre_v7_fa_fixture.json` (git HEAD 1.0.6 실제 FA 콘텐츠)

## 2. 재분류 결과
| 구분 | 개념 | 문항(기존) |
|---|---|---|
| 기존 재무회계 | 462 | 1,127 |
| → 중급회계 | 459 | 1,121 |
| → 고급회계 | 3 (B13 연결재무제표, B14 지배력, B15 연결범위 — K-IFRS 1110호 주제) | 6 |
| → 정부회계 | 0 (기존 FA 에 국가·지방회계 개념 없음. 1020호 정부보조금은 기업회계 → 중급회계) | 0 |
| 분류 검토 필요 | 0 | — |
| 유실 | 0 | 0 |
| 관련 과목 링크만 추가(소속 유지) | 5 (C22·I45·K04·L011·Q019 → 고급회계 참조) | — |

- 분류 근거: 대단원 주제·적용 기준서(`tools/content/reclassify.py` CHAPTER_RULES) + 개념별 기준서 재확인(CONCEPT_RULES·RELATED_RULES). 단어 검색만으로 분류하지 않음.
- 대단원 FA-A~K → 중급회계 '제1부 재무회계의 기초', FA-L·M·N·R → '재고자산', FA-P → '유형자산', FA-Q → '차입원가'.
- conceptId·questionId·대단원 ID 는 바꾸지 않음. 소속 과목(subjectId)·파트·primaryPackId 만 변경.
- 레거시 FA 과목 행: 삭제하지 않고 `isActive=0, examSubject='LEGACY'` — 사용자 화면(과목 목록·전체 학습현황·오답노트 필터)에 나오지 않음.

## 3. DB 마이그레이션 5 → 6 (`AppDatabase.MIGRATION_5_6`)
1. concepts 컬럼 추가: primaryPackId, relatedPackIds, classificationStatus, legacyPackId, shortSummary, detailSections, detailLevel
2. FA 개념·문항·대단원 subjectId → IA, 레거시 표시, FA 과목 비활성·LEGACY
3. curriculum_items 의 FA 목차(콘텐츠 메타데이터)만 삭제
4. 학습기록 테이블(question_attempts·question_progress·concept_progress·wrong_notes·study_days)은 읽거나 쓰지 않음
5. 직후 번들 동기화: 개념 revision +1(간단 요약·상세 학습 반영), 기존 FA 문항 revision +1(소속 변경 반영) — 가져오기는 학습기록을 건드리지 않음
- destructive fallback 없음. 모든 문장 멱등.

## 4. 검증(자동화 테스트 — 실행·통과)
`DatabaseTest.migration5To6ReclassifiesFinancialAccountingWithoutLosingRecords`
- 스키마 5 DB 에 실제 FA 콘텐츠 1,127문항 + 풀이기록 4,508건 + 문항 진도 2,254행 + 개념 진도 462행(학습 완료 352) + 오답노트 376건 + 북마크·메모 + 학습일 입력
- 마이그레이션·동기화 후: 풀이기록·문항 진도(복습일 포함)·개념 진도·오답노트·학습일이 **전부 동일**, 정답 수 동일
- 기존 개념 462·문항 1,127 모두 존재(ID 유지), IA 459 · AA 3, FA 소속 0
- 회계학 학습 완료 개념 352 → 352 (중급·고급회계 합계도 352, 중복 집계 없음)
- 재동기화(멱등): 문항 수·기록 변화 없음
- 기존 마이그레이션 테스트 1→2, 2→3, 3→4, 4→5 도 스키마 6 까지 통과

## 5. 콘텐츠 변화
| 항목 | 수치 |
|---|---|
| 전체 개념 | 1,005 → 1,031 (+26: 고급회계 16, 정부회계 10) |
| 전체 문항 | 2,956 → 3,301 (+345: 고급 51, 정부 30, 중급회계 제1부 '개념 구별' 매칭형 264 + 고급회계 이동분 3) |
| 삭제·비활성화된 기존 문항 | 0 |
| 간단 요약 보존 | 1,031/1,031 (기존 정의 전문 포함, 빌드가 검사) |
| 상세 학습 섹션 보유 | 1,031/1,031 |
| 상세 학습 충실도 | RICH 20 · FULL 451 · BASIC 560 |

- '개념 구별' 매칭형 267개는 작성자가 검토한 개념 정의만으로 정답이 정해지는 구조화 자동 생성 문항이다(개념명은 ○○로 가림, 새 회계 내용 없음).
- BASIC 560개는 상세 섹션이 있으나 적용요건·분개·공식 등 적용 섹션이 없거나 분량이 적은 개념으로, 추가 작성 대상이다(목록: `docs/CONTENT_VALIDATION_REPORT.md`).
- 고급회계·정부회계 신규 개념은 기준서·규칙 원문 문단 대조를 하지 않았다(검증 상태 '작성자 검토').
