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
    h2oTypes = {"mixed_col":"real", "uniform_col": "int"}
    desiredTypes = {"mixed_col":"real", "uniform_col": "real"}
    parquet = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/df.parquet"))
    pTypes = parquet.types
    parquetForce = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/df.parquet"), force_col_types=True)
    fpTypes = parquetForce.types
    assert h2oTypes == pTypes, "Expected column types: {0}, actual: {1}".format(h2oTypes, pTypes)
    assert desiredTypes == fpTypes, "Expected column types: {0}, actual: {1}".format(desiredTypes, fpTypes)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(parquet_parse_simple)
else:
    parquet_parse_simple()
