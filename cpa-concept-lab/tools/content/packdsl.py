"""신규 과목 팩(중급·고급·원가·정부·재무관리·인사조직·경제·세법) 작성용 공통 헬퍼."""
import dsl
import curriculum

_state = {"pack": None}


def pchapter(pack, code, title, english, part, std):
    """pack(과목 ID)의 대단원. 대단원 ID = '{pack}-{code}'."""
    _state["pack"] = pack
    return dsl.chapter(code, title, english, part=part, subject=pack, chapter_id=f"{pack}-{code}", std=std, eff=None)


def pc(cid, ko, en, d, why, core, cmp, ex, tip, trap, cur=(), f=None, rel="", pre="", rec=None, meas=None, exc=None, je=None, **kw):
    """개념 등록 + 출제범위 목차 항목(cur) 연결."""
    c = dsl.concept(cid, ko, en, d=d, why=why, core=core, cmp=cmp, ex=ex, tip=tip, trap=trap, rel=rel, f=f, pre=pre,
                    rec=rec, meas=meas, exc=exc, je=je, **kw)
    for item in cur:
        curriculum.link(_state["pack"], item, cid)
    return c
