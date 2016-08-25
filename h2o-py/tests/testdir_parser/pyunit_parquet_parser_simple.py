from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def parquet_parse_simple():
    """
    Tests Parquet parser by comparing the summary of the original csv frame with the h2o parsed Parquet frame.
    Basic use case of importing files that only have string and numeric columns.
    :return: None if passed.  Otherwise, an exception will be thrown.
    """
    col_types = ["string", "string", "string", "string", "numeric", "numeric", "string", "string", "string", "numeric", "string", "numeric"]

    csv = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/AirlinesTrain.csv.zip"), col_types=col_types)
    parquet = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/airlines-simple.snappy.parquet"), col_types=col_types)

    csv.summary()
    csv_summary = h2o.frame(csv.frame_id)["frames"][0]["columns"]

    parquet.summary()
    orc_summary = h2o.frame(parquet.frame_id)["frames"][0]["columns"]

    pyunit_utils.compare_frame_summary(csv_summary, orc_summary)

if __name__ == "__main__":
    pyunit_utils.standalone_test(parquet_parse_simple)
else:
    parquet_parse_simple()