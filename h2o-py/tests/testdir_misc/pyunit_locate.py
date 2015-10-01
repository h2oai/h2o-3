import sys
sys.path.insert(1, "../../")
import h2o, tests

def test_locate():

    iris_path = h2o.locate("smalldata/iris/iris.csv")

    try:
        tests.locate("smalldata/iris/afilethatdoesnotexist.csv")
        assert False, "Expected h2o.locate to raise a ValueError"
    except ValueError:
        assert True

if __name__ == "__main__":
    tests.run_test(sys.argv, test_locate)
