import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import numpy as np

def col_names_check():

  iris_wheader = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
  assert iris_wheader.col_names == ["sepal_len","sepal_wid","petal_len","petal_wid","class"], \
      "Expected {0} for column names but got {1}".format(["sepal_len","sepal_wid","petal_len","petal_wid","class"],
                                                         iris_wheader.col_names)

  iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
  assert iris.col_names == ["C1","C2","C3","C4","C5"], "Expected {0} for column names but got " \
                                                         "{1}".format(["C1","C2","C3","C4","C5"], iris.col_names)

  df = h2o.H2OFrame.from_python(zip(*np.random.randn(100,4).tolist()), column_names=list("ABCD"), column_types=["enum"]*4)
  df.head()
  assert df.col_names == list("ABCD"), "Expected {} for column names but got {}".format(list("ABCD"), df.col_names)
  assert df.types.values() == ["enum"]*4, "Expected {} for column types but got {}".format(["enum"]*4, df.types)

  df = h2o.H2OFrame(zip(*np.random.randn(100,4).tolist()))
  df.head()
  assert df.col_names == ["C1","C2","C3","C4"], "Expected {} for column names but got {}".format(["C1","C2","C3","C4"]
                                                                                                 , df.col_names)
  assert df.types.values() == ["real"]*4, "Expected {} for column types but got {}".format(["real"]*4, df.types)

  df = h2o.H2OFrame({'B': ['a', 'a', 'b', 'NA', 'NA']})
  df.head()
  assert df.col_names == ["B"], "Expected {} for column names but got {}".format(["B"], df.col_names)

  df = h2o.H2OFrame.from_python({'B': ['a', 'a', 'b', 'NA', 'NA']}, column_names=["X"])
  df.head()
  assert df.col_names == ["X"], "Expected {} for column names but got {}".format(["X"], df.col_names)


if __name__ == "__main__":
    pyunit_utils.standalone_test(col_names_check)
else:
    col_names_check()
