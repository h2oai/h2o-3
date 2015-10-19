import sys
sys.path.insert(1, "../../")
import h2o, tests
import numpy as np

def col_names_check():

  iris_wheader = h2o.import_file(tests.locate("smalldata/iris/iris_wheader.csv"))
  assert iris_wheader.col_names == ["sepal_len","sepal_wid","petal_len","petal_wid","class"], \
      "Expected {0} for column names but got {1}".format(["sepal_len","sepal_wid","petal_len","petal_wid","class"],
                                                         iris_wheader.col_names)

  iris = h2o.import_file(tests.locate("smalldata/iris/iris.csv"))
  assert iris.col_names == ["C1","C2","C3","C4","C5"], "Expected {0} for column names but got " \
                                                         "{1}".format(["C1","C2","C3","C4","C5"], iris.col_names)

  df = h2o.H2OFrame(np.random.randn(100,4).tolist(), column_names=list("ABCD"), column_types=["Enum"]*4)
  df.head()
  assert df.col_names == list("ABCD"), "Expected {} for column names but got {}".format(list("ABCD"), df.col_names)
  assert df.types == {"A": "Enum", "C": "Enum", "B": "Enum", "D": "Enum"}, "Expected {} for column types " \
                              "but got {}".format({"A": "Enum", "C": "Enum", "B": "Enum", "D": "Enum"},
                                                  df.types)

  df = h2o.H2OFrame(np.random.randn(100,4).tolist())
  df.head()
  assert df.col_names == ["C1","C2","C3","C4"], "Expected {} for column names but got {}".format(["C1","C2","C3","C4"]
                                                                                                 , df.col_names)
  assert df.types == {"C3": "Numeric", "C2": "Numeric", "C1": "Numeric", "C4": "Numeric"}, "Expected {}" \
                      " for column types but got {}".format({"C3": "Numeric", "C2": "Numeric", "C1": "Numeric",
                                                             "C4": "Numeric"}, df.types)

if __name__ == "__main__":
  tests.run_test(sys.argv, col_names_check)
