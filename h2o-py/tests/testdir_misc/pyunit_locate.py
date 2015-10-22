import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def test_locate():

    iris_path = pyunit_utils.locate("smalldata/iris/iris.csv")

    try:
        pyunit_utils.locate("smalldata/iris/afilethatdoesnotexist.csv")
        assert False, "Expected pyunit_utils.locate to raise a ValueError"
    except ValueError:
        assert True



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_locate)
else:
    test_locate()
