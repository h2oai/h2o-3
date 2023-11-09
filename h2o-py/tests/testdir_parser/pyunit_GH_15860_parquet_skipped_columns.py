import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def test_parquet_column_types_skipped_columns():
    parquet = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/df.parquet"))
    parquetForce = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/df.parquet"), 
                                   force_col_types=True, skipped_columns=[0])
    assert parquetForce.types["uniform_col"]=="real", "Expected type: {0}, actual: " \
                                                      "{1}".format("real", parquetForce.types["uniform_col"])
    assert parquet.ncol-1==parquetForce.ncol, "Expected column number: {0}, actual: " \
                                              "{1}".format(parquet.ncol-1, parquetForce.ncol)
    pyunit_utils.compare_frames_local(parquet[1], parquetForce[0], prob=0.1, tol=1e-12)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_parquet_column_types_skipped_columns)
else:
    test_parquet_column_types_skipped_columns()
