from __future__ import print_function
import sys

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
import random

def import_gzip_skipped_columns():
    # checking out zip file
    airlineCSV = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv"))
    filePath = pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip")

    skip_all = list(range(airlineCSV.ncol))
    skip_even = list(range(0, airlineCSV.ncol, 2))
    skip_odd = list(range(1, airlineCSV.ncol, 2))
    skip_start_end = [0, airlineCSV.ncol - 1]
    skip_except_last = list(range(0, airlineCSV.ncol - 2))
    skip_except_first = list(range(1, airlineCSV.ncol))
    temp = list(range(0, airlineCSV.ncol))
    random.shuffle(temp)
    skip_random = []
    for index in range(0, airlineCSV.ncol//2):
        skip_random.append(temp[index])
    skip_random.sort()

    try:
        bad = h2o.import_file(filePath, skipped_columns=skip_all)  # skipped all
        sys.exit(1)
    except Exception as ex:
        print(ex)
        pass

    try:
        bad = h2o.upload_file(filePath, skipped_columns=skip_all)   # skipped all
        sys.exit(1)
    except Exception as ex:
        print(ex)
        pass

    # skip even columns
    pyunit_utils.checkCorrectSkips(airlineCSV, filePath, skip_even)

    # skip odd columns
    pyunit_utils.checkCorrectSkips(airlineCSV, filePath, skip_odd)

    # skip the very beginning and the very end.
    pyunit_utils.checkCorrectSkips(airlineCSV, filePath, skip_start_end)

    # skip all except the last column
    pyunit_utils.checkCorrectSkips(airlineCSV, filePath, skip_except_last)

    # skip all except the very first column
    pyunit_utils.checkCorrectSkips(airlineCSV, filePath, skip_except_first)

    # randomly skipped half the columns
    pyunit_utils.checkCorrectSkips(airlineCSV, filePath, skip_random)


if __name__ == "__main__":
    pyunit_utils.standalone_test(import_gzip_skipped_columns)
else:
    import_gzip_skipped_columns()
