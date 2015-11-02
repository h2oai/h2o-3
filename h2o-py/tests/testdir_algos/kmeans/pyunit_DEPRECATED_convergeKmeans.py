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
  try:
    h2o.kmeans(x=ozone_h2o, max_iterations=0)
    assert False, "expected an error"
  except EnvironmentError:
    assert True

  centers = start
  for i in range(miters):
    rep_fit = h2o.kmeans(x=ozone_h2o, k=ncent, user_points=centers, max_iterations=1)
    centers = h2o.H2OFrame(rep_fit.centers())

  # Log.info(paste("Run k-means with max_iter=miters"))
  all_fit = h2o.kmeans(x=ozone_h2o, k=ncent, user_points=start, max_iterations=miters)
  assert rep_fit.centers() == all_fit.centers(), "expected the centers to be the same"

  # Log.info("Check cluster centers have converged")
  all_fit2 = h2o.kmeans(x=ozone_h2o, k=ncent, user_points=h2o.H2OFrame(all_fit.centers()),
                        max_iterations=1)
  avg_change = sum([sum([pow((e1 - e2),2) for e1, e2 in zip(c1,c2)]) for c1, c2 in zip(all_fit.centers(),
                                                                                       all_fit2.centers())]) / ncent
  assert avg_change < 1e-6 or all_fit._model_json['output']['iterations'] == miters



if __name__ == "__main__":
    pyunit_utils.standalone_test(convergeKmeans)
else:
    convergeKmeans()
