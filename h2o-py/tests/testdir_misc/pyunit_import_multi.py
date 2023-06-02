import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# test that h2o.import_file works on multiple sources
def import_multi():
    airlines = h2o.import_file(path=[
        pyunit_utils.locate("smalldata/testng/airlines_train.csv"),
        pyunit_utils.locate("smalldata/testng/airlines_test.csv")
    ])

    assert airlines.nrows == 24421 + 2691

if __name__ == "__main__":
    pyunit_utils.standalone_test(import_multi)
else:
    import_multi()
