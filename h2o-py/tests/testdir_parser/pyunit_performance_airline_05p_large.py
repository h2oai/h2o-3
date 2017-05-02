from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
import time
from tests import pyunit_utils


def test_performance():
    """
    This test will measure the time it takes to import a large file multiple times.
    
    :return: None if passed.  Otherwise, an exception will be thrown.
    """
    startcsv = time.time()
    multi_file_csv = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/airlines_all.05p.csv"),
                                     na_strings=['\\N'])
    endcsv = time.time()
    print("************** CSV parse time is {0}".format(endcsv-startcsv))


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_performance)
else:
    test_performance()
