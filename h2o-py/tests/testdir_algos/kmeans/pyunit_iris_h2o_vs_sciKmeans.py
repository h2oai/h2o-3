import sys
sys.path.insert(1, "../../../")
import h2o, tests

import numpy as np
from sklearn.cluster import KMeans

def iris_h2o_vs_sciKmeans():
  # Connect to a pre-existing cluster
    # connect to localhost:54321

  iris_h2o = h2o.import_file(path=tests.locate("smalldata/iris/iris.csv"))
  iris_sci = np.genfromtxt(tests.locate("smalldata/iris/iris.csv"), delimiter=',')
  iris_sci = iris_sci[:,0:4]

  s =[[4.9,3.0,1.4,0.2],
  [5.6,2.5,3.9,1.1],
  [6.5,3.0,5.2,2.0]]

  start = h2o.H2OFrame(s)

  h2o_km = h2o.kmeans(x=iris_h2o[0:4], k=3, user_points=start, standardize=False)

  sci_km = KMeans(n_clusters=3, init=np.asarray(s), n_init=1)
  sci_km.fit(iris_sci)

  # Log.info("Cluster centers from H2O:")
  print "Cluster centers from H2O:"
  h2o_centers = h2o_km.centers()
  print h2o_centers

  # Log.info("Cluster centers from scikit:")
  print "Cluster centers from scikit:"
  sci_centers = sci_km.cluster_centers_.tolist()
  print sci_centers

  for hcenter, scenter in zip(h2o_centers, sci_centers):
    for hpoint, spoint in zip(hcenter,scenter):
      assert (hpoint- spoint) < 1e-10, "expected centers to be the same"

if __name__ == "__main__":
  tests.run_test(sys.argv, iris_h2o_vs_sciKmeans)
