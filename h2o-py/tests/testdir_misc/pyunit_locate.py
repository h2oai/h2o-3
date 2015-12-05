import sys
sys.path.insert(1, "../../")
import h2o

def test_locate(ip,port):

    iris_path = h2o.locate("smalldata/iris/iris.csv")

    try:
        h2o.locate("smalldata/iris/afilethatdoesnotexist.csv")
        assert False, "Expected h2o.locate to raise a ValueError"
    except ValueError:
        assert True

if __name__ == "__main__":
    h2o.run_test(sys.argv, test_locate)
