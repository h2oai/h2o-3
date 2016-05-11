from builtins import str
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.kmeans import H2OKMeansEstimator


import random
import string

def baddataKmeans():

  # Connect to a pre-existing cluster
  # connect to localhost:54321

  random.seed(1234)
  rows = 100
  cols = 10
  rawdata = [[random.random() for c in range(cols)] for r in range(rows)]

  # Row elements that are None will be replaced with mean of column
  #Log.info("Training data with 1 row of all Nones: replace with column mean")
  data = rawdata[:]
  data[24] = [None]*cols
  frame = h2o.H2OFrame(data)



  km_model = H2OKMeansEstimator(k=5)
  km_model.train(x=list(range(cols)), training_frame=frame)

  centers = km_model.centers()
  assert len(centers) == 5, "expected 5 centers"
  assert len(centers[0]) == 10, "expected center to be 10 dimensional"

  # Columns with constant value will be automatically dropped
  #Log.info("Training data with 1 col of all 5's: drop automatically")
  data = rawdata[:]
  for row in data: row[4] = 5
  frame = h2o.H2OFrame(data)

  km_model = H2OKMeansEstimator(k=5)
  km_model.train(x = list(range(cols)), training_frame=frame)

  centers = km_model.centers()
  assert len(centers) == 5, "expected 5 centers"
  assert len(centers[0]) == 9, "expected center to be 9-dimensional"
  # TODO: expect_warning(km_model = h2o.kmeans(x=frame, k=5))

  # Log.info("Training data with 1 col of all None's, 1 col of all zeroes: drop automatically")
  data = rawdata[:]
  for row in data:
    row[4] = None
    row[7] = 0
  frame = h2o.H2OFrame(data)

  km_model = H2OKMeansEstimator(k=5)
  km_model.train(x=list(range(cols)), training_frame=frame)

  centers = km_model.centers()
  assert len(centers) == 5, "expected 5 centers"
  assert len(centers[0]) == 8, "expected center to be 8-dim "
  # TODO: expect_warning(km_model = h2o.kmeans(x=frame, k=5))

  # Log.info("Training data with all None's")
  data = [[None for r in range(cols)] for c in range(rows)]
  frame = h2o.H2OFrame(data)

  try:
    H2OKMeansEstimator(k=5).train(x=list(range(cols)), training_frame=frame)
    assert False, "expected an error"
  except EnvironmentError:
    assert True

  # Log.info("Training data with a categorical column(s)")
  data = [[random.choice(string.ascii_uppercase) for r in range(cols)] for c in range(rows)]
  frame = h2o.H2OFrame(data)

  km_model = H2OKMeansEstimator(k=5)
  km_model.train(x=list(range(cols)), training_frame=frame)
  centers = km_model.centers()
  assert len(centers) == 5, "expected 5 centers"
  assert len(centers[0]) == 10, "expected center to be 10 "+str(len(centers))

  # Log.info("Importing iris.csv data...\n")
  iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

  km_model = H2OKMeansEstimator(k=5)
  km_model.train(x=list(range(iris.ncol)), training_frame=iris)
  centers = km_model.centers()
  assert len(centers) == 5, "expected 5 centers"
  assert len(centers[0]) == 5, "expected center to be 5 "+str(len(centers))



if __name__ == "__main__":
  pyunit_utils.standalone_test(baddataKmeans)
else:
  baddataKmeans()
