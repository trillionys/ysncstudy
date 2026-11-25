"""v8 출제범위 목차 보강 콘텐츠 로더.

tools/content/v8/ 아래 과목·단원별 파일을 정해진 순서로 불러온다(순서가 바뀌면 sortOrder 가 바뀌므로 뒤에만 추가).
각 파일은 packdsl.pc()/dsl 문항 함수로 개념·문항을 등록하고 cur=[...] 로 목차 항목에 명시 연결한다.
"""
import importlib
import os
import sys

HERE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "v8")
sys.path.insert(0, HERE)

ORDER = [
    "ia_cash",
    "ia_receivables",
    "ia_investment_property",
    "ia_changes",
    "ia_intangibles",
    "ia_financial_instruments",
    "ia_provisions",
    "ia_equity",
    "ia_revenue",
    "ia_leases",
    "ia_employee_share",
    "ia_income_tax",
    "ia_eps_cf",
    "ia_supplement",
    "aa_advanced",
    "cm_supplement",
    "cm_more",
]

for name in ORDER:
    importlib.import_module(name)
