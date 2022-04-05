from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from pandas.testing import assert_frame_equal 


def import_bundled_dataset():
  iris_smalldata = h2o.import_file(pyunit_utils.locate("smalldata/extdata/iris_wheader.csv"))
  iris_h2o = h2o.import_file("h2o://iris")
  assert_frame_equal(iris_smalldata.as_data_frame(), iris_h2o.as_data_frame())


if __name__ == "__main__":
  pyunit_utils.standalone_test(import_bundled_dataset)
else:
  import_bundled_dataset()
