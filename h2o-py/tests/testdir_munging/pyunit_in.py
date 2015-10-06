import sys
sys.path.insert(1, "../../")
import h2o, tests

def test_in():
    iris = h2o.import_file(tests.locate("smalldata/iris/iris.csv"))

    assert 5.1 in iris[0], "expected 5.1 to be in the first column, but it wasn't"
    assert 1.7 in iris, "expected 1.7 to be in the dataset, but it wasn't"
    assert not 99 in iris, "didn't expect 99 to be in the dataset, but it was"
    assert "Iris-setosa" in iris[4], "expected Iris-setosa to be in the dataset, but it wasn't"

    h2o.remove(iris)

if __name__ == "__main__":
    tests.run_test(sys.argv, test_in)
