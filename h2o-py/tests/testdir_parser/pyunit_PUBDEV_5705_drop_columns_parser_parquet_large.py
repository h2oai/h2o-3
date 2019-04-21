from __future__ import print_function
import sys

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
import random


def test_parquet_parser_column_skip():
    # generate a big frame with all datatypes and save it to csv.  Load it back with different skipped_columns settings
    csv = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"))
    parquetNoSkip = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/airlines-simple.snappy.parquet"))
    pyunit_utils.compare_frames_local(csv, parquetNoSkip, prob=1)  # should be the same here.

    path = pyunit_utils.locate("smalldata/parser/parquet/airlines-simple.snappy.parquet")
    skip_all = list(range(csv.ncol))
    skip_even = list(range(0, csv.ncol, 2))
    skip_odd = list(range(1, csv.ncol, 2))
    skip_start_end = [0, csv.ncol - 1]
    skip_except_last = list(range(0, csv.ncol - 2))
    skip_except_first = list(range(1, csv.ncol))
    temp = list(range(0, csv.ncol))
    random.shuffle(temp)
    skip_random = []
    for index in range(0, csv.ncol//2):
        skip_random.append(temp[index])
    skip_random.sort()

    try:
        loadFileSkipAll = h2o.upload_file(path, skipped_columns=skip_all)
        sys.exit(1)  # should have failed here
    except:
        pass

    try:
        importFileSkipAll = h2o.import_file(path, skipped_columns=skip_all)
        sys.exit(1)  # should have failed here
    except:
        pass

    # skip even columns
    pyunit_utils.checkCorrectSkips(csv, path, skip_even)

    # skip odd columns
    pyunit_utils.checkCorrectSkips(csv, path, skip_odd)

    # skip the very beginning and the very end.
    pyunit_utils.checkCorrectSkips(csv, path, skip_start_end)

    # skip all except the last column
    pyunit_utils.checkCorrectSkips(csv, path, skip_except_last)

    # skip all except the very first column
    pyunit_utils.checkCorrectSkips(csv, path, skip_except_first)

    # randomly skipped half the columns
    pyunit_utils.checkCorrectSkips(csv, path, skip_random)



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_parquet_parser_column_skip)
else:
    test_parquet_parser_column_skip()
