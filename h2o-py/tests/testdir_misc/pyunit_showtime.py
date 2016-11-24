from __future__ import print_function
import re
import h2o
from tests import pyunit_utils


def test_show_time():

    h2o.cluster().timezone = "UTC"
    df = h2o.H2OFrame.from_python(
        {"A": [1, 2, 3],
         "B": ["a", "a", "b"],
         "C": ["hello", "all", "world"],
         "D": ["12MAR2015:11:00:00", "13MAR2015:12:00:00", "14MAR2015:13:00:00"]},
        column_types={"A": "numeric", "B": "enum", "C": "string", "D": "time"}
    )
    out = df.__unicode__()
    print(out)
    assert "2015-03-12 11:00:00" in out
    assert "2015-03-13 12:00:00" in out
    assert "2015-03-14 13:00:00" in out

    df2 = h2o.create_frame(cols=6, rows=10, time_fraction=1, missing_fraction=0.1)
    out2 = df2.__unicode__()
    print(out2)
    assert "e+" not in out2
    assert "E+" not in out2

    lines = out2.splitlines()[2:-2]  # skip header (first 2 lines) + footer (last 2 lines)
    regex = re.compile(r"(\d+)-(\d+)-(\d+) (\d+):(\d+):(\d+)")
    for l in lines:
        for entry in l.split("  "):
            entry = entry.strip()
            if entry == "": continue  # skip missing entries
            m = re.match(regex, entry)
            assert m is not None, "Failed to recognize time expression '%s'" % entry
            year = int(m.group(1))
            month = int(m.group(2))
            day = int(m.group(3))
            assert 1970 <= year <= 2020
            assert 1 <= month <= 12
            assert 1 <= day <= 31




if __name__ == "__main__":
    pyunit_utils.standalone_test(test_show_time)
else:
    test_show_time()
