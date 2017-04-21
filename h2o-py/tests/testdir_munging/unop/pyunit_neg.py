import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils


def test___neg__():
    fr = h2o.create_frame(rows=100, cols=1, categorical_fraction=1, factors=3)
    f2 = ~fr["C1"].isin(["c0.l0", "c0.l2"])
    assert f2.any()
    assert not f2.all()


if __name__ == "__main__":
    pyunit_utils.standalone_test(test___neg__)
else:
    test___neg__()
