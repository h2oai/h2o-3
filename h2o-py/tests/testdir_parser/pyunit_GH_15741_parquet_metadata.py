import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def test_parquet_column_types():
    h2oTypes = {"mixed_col":"real", "uniform_col": "int"}
    desiredTypes = {"mixed_col":"real", "uniform_col": "real"}
    parquet = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/df.parquet"))
    pTypes = parquet.types
    parquetForce = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/parquet/df.parquet"), force_col_types=True)
    fpTypes = parquetForce.types
    assert h2oTypes == pTypes, "Expected column types: {0}, actual: {1}".format(h2oTypes, pTypes)
    assert desiredTypes == fpTypes, "Expected column types: {0}, actual: {1}".format(desiredTypes, fpTypes)
    pyunit_utils.compare_frames_local(parquet, parquetForce, prob=1.0, tol=1e-12)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_parquet_column_types)
else:
    test_parquet_column_types()
