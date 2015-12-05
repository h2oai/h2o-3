import sys
sys.path.insert(1, "../../")
import h2o

def test_any_all(ip,port):

    foo = h2o.import_frame(h2o.locate("smalldata/iris/iris.csv"))

    foo["C6"] = foo["C1"] > 0.0
    any = foo[:,"C6"].any()
    all = foo[:,"C6"].all()
    assert any and all, "expected any and all to be True but but got {0} and {1}".format(any, all)

    foo["C6"] = foo["C1"] > 5.0
    any = foo[:,"C6"].any()
    all = foo[:,"C6"].all()
    assert any and not all, "expected any to be True and all to be False but but got {0} and {1}".format(any, all)

if __name__ == "__main__":
    h2o.run_test(sys.argv, test_any_all)
