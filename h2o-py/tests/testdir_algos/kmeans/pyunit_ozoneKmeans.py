import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def ozoneKM():
  # Connect to a pre-existing cluster
  # connect to localhost:54321

  train = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/ozone.csv"))

  # See that the data is ready
  print train.describe()

  # Run KMeans

  from h2o.estimators.kmeans import H2OKMeansEstimator
  my_km = H2OKMeansEstimator(
                     k=10,
                     init = "PlusPlus",
                     max_iterations = 100)
  my_km.train(x = range(train.ncol), training_frame=train)
  my_km.show()
  my_km.summary()

  my_pred = my_km.predict(train)
  my_pred.describe()



if __name__ == "__main__":
  pyunit_utils.standalone_test(ozoneKM)
else:
  ozoneKM()
