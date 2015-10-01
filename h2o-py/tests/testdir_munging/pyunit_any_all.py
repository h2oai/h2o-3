import sys
sys.path.insert(1, "../../")
import h2o, tests

def test_any_all():

    foo = h2o.import_file(tests.locate("smalldata/iris/iris.csv"))

    foo["C6"] = foo["C1"] > 0.0
    any = foo[:,"C6"].any()
    all = foo[:,"C6"].all()
    assert any and all, "expected any and all to be True but but got {0} and {1}".format(any, all)

    foo["C6"] = foo["C1"] > 5.0
    any = foo[:,"C6"].any()
    all = foo[:,"C6"].all()
    assert any and not all, "expected any to be True and all to be False but but got {0} and {1}".format(any, all)

if __name__ == "__main__":
    tests.run_test(sys.argv, test_any_all)
