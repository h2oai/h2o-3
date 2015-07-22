import sys
sys.path.insert(1, "../../")
import h2o

def test_all(ip,port):
    foo = h2o.import_frame(h2o.locate("smalldata/iris/iris.csv"))
    foo["C6"] = foo["C1"] > 0.0
    foo[:,"C6"].all()

if __name__ == "__main__":
    h2o.run_test(sys.argv, test_all)