from __future__ import print_function
import sys

sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils


def parquet_parse_dates():
    parquet_data = h2o.import_file(
        path=pyunit_utils.locate("smalldata/parser/parquet/parquet-file-with-date-column.snappy.parquet"))

    parquet_data.summary()
    parquet_summary = h2o.frame(parquet_data.frame_id)["frames"][0]["columns"]
    date_converted_column_type = parquet_summary[2]['type']
    assert date_converted_column_type == "time"

    date_string_rows = parquet_data[:, "date_string"]
    date_converted_rows = parquet_data[:, "date_converted"]
    pyunit_utils.compare_frames(date_string_rows, date_converted_rows, 1)

if __name__ == "__main__":
    pyunit_utils.standalone_test(parquet_parse_dates)
else:
    parquet_parse_dates()
