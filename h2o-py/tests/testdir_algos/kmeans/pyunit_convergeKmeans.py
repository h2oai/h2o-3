import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def convergeKmeans():

  # Connect to a pre-existing cluster
  # connect to localhost:54321

  # Log.info("Importing ozone.csv data...\n")
  ozone_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/ozone.csv"))
  #ozone_h2o.summary()

  miters = 5
  ncent = 10

  # Log.info(paste("Run k-means in a loop of", miters, "iterations with max_iter = 1"))
  start = ozone_h2o[0:10, 0:4]

  # expect error for 0 iterations
  from h2o.estimators.kmeans import H2OKMeansEstimator
  try:
    H2OKMeansEstimator(max_iterations=0).train(x = range(ozone_h2o.ncol), training_frame=ozone_h2o)
    assert False, "expected an error"
  except EnvironmentError:
    assert True

  centers = start
  for i in range(miters):
    rep_fit = H2OKMeansEstimator(k=ncent, user_points=centers, max_iterations=1)
    rep_fit.train(x = range(ozone_h2o.ncol), training_frame=ozone_h2o)
    centers = h2o.H2OFrame(rep_fit.centers())

  # Log.info(paste("Run k-means with max_iter=miters"))
  all_fit = H2OKMeansEstimator(k=ncent, user_points=start, max_iterations=miters)
  all_fit.train(x=range(ozone_h2o.ncol), training_frame=ozone_h2o)
  assert rep_fit.centers() == all_fit.centers(), "expected the centers to be the same"

  # Log.info("Check cluster centers have converged")
  all_fit2 = H2OKMeansEstimator(k=ncent, user_points=h2o.H2OFrame(all_fit.centers()),
                        max_iterations=1)
  all_fit2.train(x=range(ozone_h2o.ncol), training_frame= ozone_h2o)
  avg_change = sum([sum([pow((e1 - e2),2) for e1, e2 in zip(c1,c2)]) for c1, c2 in zip(all_fit.centers(),
                                                                                       all_fit2.centers())]) / ncent
  assert avg_change < 1e-6 or all_fit._model_json['output']['iterations'] == miters



if __name__ == "__main__":
  pyunit_utils.standalone_test(convergeKmeans)
else:
  convergeKmeans()
