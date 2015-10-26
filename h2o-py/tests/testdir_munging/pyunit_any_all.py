import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def test_any_all():

    foo = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))

    foo["C6"] = foo["C1"] > 0.0
    any = foo[:,"C6"].any()
    all = foo[:,"C6"].all()
    assert any and all, "expected any and all to be True but got {0} and {1}".format(any, all)

    foo["C6"] = foo["C1"] > 5.0
    any = foo[:,"C6"].any()
    all = foo[:,"C6"].all()
    assert any and not all, "expected any to be True and all to be False but got {0} and {1}".format(any, all)



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_any_all)
else:
    test_any_all()
