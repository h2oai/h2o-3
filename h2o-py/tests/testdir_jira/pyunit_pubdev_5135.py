import h2o

from h2o.expr import ExprNode
from tests import pyunit_utils


def pubdev_5135():
    parquet = h2o.import_file(path=pyunit_utils.locate("smalldata/jira/pubdev-5135.parquet"))

    assert parquet is not None, "Parquet file should be parsed."
    assert parquet.ncols is 2, "Parsed H2OFrame should contain two columns."
    assert parquet.nrows is 0, "Parsed H20Frame should not contain any rows."
    assert parquet.col_names == ['col1', 'col2'], "Parsed H20Frame's columns should be named 'col1' and 'col2'."

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_5135)
else:
    pubdev_5135()
