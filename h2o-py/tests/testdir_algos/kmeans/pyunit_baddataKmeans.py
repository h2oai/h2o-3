import sys
sys.path.insert(1, "../../../")
import h2o, tests
import random
import string

def baddataKmeans():

  # Connect to a pre-existing cluster
    # connect to localhost:54321

  rows = 100
  cols = 10
  rawdata = [[random.random() for c in range(cols)] for r in range(rows)]

  # Row elements that are None will be replaced with mean of column
  #Log.info("Training data with 1 row of all Nones: replace with column mean")
  data = rawdata[:]
  for cidx, cval in enumerate(data[24]):
    data[24][cidx] = None
  frame = h2o.H2OFrame(data)

  km_model = h2o.kmeans(x=frame, k=5)

  centers = km_model.centers()
  assert len(centers) == 5, "expected 5 centers"
  for c in range(len(centers)):
    assert len(centers[c]) == 10, "expected center to be 10 dimensional"

  # Columns with constant value will be automatically dropped
  #Log.info("Training data with 1 col of all 5's: drop automatically")
  data = rawdata[:]
  for idx, val in enumerate(data):
    data[idx][4] = 5
  frame = h2o.H2OFrame(data)

  km_model = h2o.kmeans(x=frame, k=5)

  centers = km_model.centers()
  assert len(centers) == 5, "expected 5 centers"
  for c in range(len(centers)):
    assert len(centers[c]) == 9, "expected center to be 9 "
  # TODO: expect_warning(km_model = h2o.kmeans(x=frame, k=5))

  # Log.info("Training data with 1 col of all None's, 1 col of all zeroes: drop automatically")
  data = rawdata[:]
  for idx, val in enumerate(data):
    data[idx][4] = None
    data[idx][7] = 0
  frame = h2o.H2OFrame(data)

  km_model = h2o.kmeans(x=frame, k=5)

  centers = km_model.centers()
  assert len(centers) == 5, "expected 5 centers"
  for c in range(len(centers)):
    assert len(centers[c]) == 8, "expected center to be 9 "
  # TODO: expect_warning(km_model = h2o.kmeans(x=frame, k=5))

  # Log.info("Training data with all None's")
  data = [[None for c in range(cols)] for r in range(rows)]
  frame = h2o.H2OFrame(data)

  try:
    h2o.kmeans(x=frame, k=5)
    assert False, "expected an error"
  except EnvironmentError:
    assert True

  # Log.info("Training data with a categorical column(s)")
  data = [[random.choice(string.ascii_uppercase) for c in range(cols)] for r in range(rows)]
  frame = h2o.H2OFrame(data)

  km_model = h2o.kmeans(x=frame, k=5)
  centers = km_model.centers()
  assert len(centers) == 5, "expected 5 centers"
  for c in range(len(centers)):
    assert len(centers[c]) == 10, "expected center to be 10 "+str(len(centers[c]))

  # Log.info("Importing iris.csv data...\n")
  iris = h2o.import_file(path=tests.locate("smalldata/iris/iris.csv"))

  km_model = h2o.kmeans(x=iris, k=5)
  centers = km_model.centers()
  assert len(centers) == 5, "expected 5 centers"
  for c in range(len(centers)):
    assert len(centers[c]) == 5, "expected center to be 5 "+str(len(centers[c]))

if __name__ == "__main__":
   tests.run_test(sys.argv, baddataKmeans)
