import sys
import tempfile

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils


def test_export_file_npe_gh_16161():
    with tempfile.TemporaryDirectory() as dir:
        df = h2o.create_frame(rows=100, cols=10, string_fraction=0.1, seed=5, seed_for_column_types=25)
        h2o.export_file(df, path=dir, format="parquet", write_checksum=False)
        df2 = h2o.import_file(dir)
        assert pyunit_utils.compare_frames(df, df2, tol_numeric=1e-10, numElements=0)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_export_file_npe_gh_16161)
else:
    test_export_file_npe_gh_16161()



