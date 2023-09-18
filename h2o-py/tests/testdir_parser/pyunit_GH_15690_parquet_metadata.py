import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def parquet_parse_simple():
    """
    Tests Parquet parser by comparing the summary of the original csv frame with the h2o parsed Parquet frame.
    Basic use case of importing files with auto-detection of column types.
    :return: None if passed.  Otherwise, an exception will be thrown.
    """
#    csv = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/df_parquet.csv"))
    parquet = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/df.parquet"), force_col_types=True)
#    parquet2 = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/df.parquet"), force_col_types=True, col_types=["real", "real"])
#    parquet3 = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/df.parquet"))

#    csv.summary()
#    csv_summary = h2o.frame(csv.frame_id)["frames"][0]["columns"]

    print(parquet.summary())
    print(parquet2.summary())
    print(parquet3.summary())
    parquet_summary = h2o.frame(parquet.frame_id)["frames"][0]["columns"]

#    pyunit_utils.compare_frame_summary(csv_summary, parquet_summary)

if __name__ == "__main__":
    pyunit_utils.standalone_test(parquet_parse_simple)
else:
    parquet_parse_simple()
