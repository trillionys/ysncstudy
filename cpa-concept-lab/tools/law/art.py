#!/usr/bin/env python3
"""조문 확인: python3 tools/law/art.py 상법 41 42 382의3   /  python3 tools/law/art.py 외부감사법 10"""
import json, os, sys
D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data")
F = {"상법": "sangbeop.json", "공인회계사법": "cpa_act.json", "외부감사법": "audit_act.json", "국세기본법": "tax_basic.json",
     "법인세법": "corp_tax.json", "소득세법": "income_tax.json", "부가가치세법": "vat.json", "상증법": "inherit_gift.json",
     "지방세법": "local_tax.json", "조특법": "tax_special.json"}
law = sys.argv[1]
arts = {a["articleId"]: a for a in json.load(open(os.path.join(D, F[law]), encoding="utf-8"))["articles"]}
width = int(os.environ.get("W", "900"))
for k in sys.argv[2:]:
    aid = "제" + k.replace("의", "조의") if "의" in k else f"제{k}조"
    a = arts.get(aid)
    print(f"■ {aid}({a['title'] if a else '없음'}) {a['text'][:width] if a else ''}\n")
