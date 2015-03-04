import sys
sys.path.insert(1, "../../../")
import h2o

def convergeKmeans(ip,port):

  # Connect to a pre-existing cluster
  h2o.init(ip,port)  # connect to localhost:54321

  # Log.info("Importing ozone.csv data...\n")
  ozone_h2o = h2o.import_frame(path=h2o.locate("smalldata/glm_test/ozone.csv"))
  #ozone_h2o.summary()

  miters = 5
  ncent = 10

  # Log.info(paste("Run k-means in a loop of", miters, "iterations with max_iter = 1"))
  # TODO: implement row slicing
  start = h2o.H2OFrame([[41,190,67,7.4],
  [36,118,72,8],
  [12,149,74,12.6],
  [18,313,62,11.5],
  [23,299,65,8.6],
  [19,99,59,13.8],
  [8,19,61,20.1],
  [16,256,69,9.7],
  [11,290,66,9.2],
  [14,274,68,10.9]])
  start_key = start.send_frame()

  # expect error for 0 iterations
  try:
    h2o.kmeans(x=ozone_h2o, max_iterations=0)
    assert False, "expected an error"
  except EnvironmentError:
    assert True

  centers_key = start_key
  for i in range(miters):
    rep_fit = h2o.kmeans(x=ozone_h2o, k=ncent, user_points=centers_key, max_iterations=1)
    centers = h2o.H2OFrame(rep_fit.centers())
    centers_key = centers.send_frame()

  # Log.info(paste("Run k-means with max_iter=miters"))
  all_fit = h2o.kmeans(x=ozone_h2o, k=ncent, user_points=start_key, max_iterations=miters)
  assert rep_fit.centers() == all_fit.centers(), "expected the centers to be the same"

  # Log.info("Check cluster centers have converged")
  all_fit2 = h2o.kmeans(x=ozone_h2o, k=ncent, user_points=h2o.H2OFrame(all_fit.centers()).send_frame(), max_iterations=1)
  avg_change = sum([sum([pow((e1 - e2),2) for e1, e2 in zip(c1,c2)]) for c1, c2 in zip(all_fit.centers(),all_fit2.centers())])/ncent
  assert avg_change < 1e-6 or all_fit._model_json['output']['iterations'] < miters

if __name__ == "__main__":
    h2o.run_test(sys.argv, convergeKmeans)