import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




import numpy as np
from sklearn.cluster import KMeans

def prostateKmeans():
  # Connect to a pre-existing cluster
    # connect to localhost:54321

  #Log.info("Importing prostate.csv data...\n")
  prostate_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  #prostate.summary()

  prostate_sci = np.loadtxt(pyunit_utils.locate("smalldata/logreg/prostate_train.csv"), delimiter=',', skiprows=1)
  prostate_sci = prostate_sci[:,1:]
  
  for i in range(5,9):
    #Log.info(paste("H2O K-Means with ", i, " clusters:\n", sep = ""))
    #Log.info(paste( "Using these columns: ", colnames(prostate.hex)[-1]) )
    prostate_km_h2o = h2o.kmeans(x=prostate_h2o[1:], k=i)
    prostate_km_h2o.show()

    prostate_km_sci = KMeans(n_clusters=i, init='k-means++', n_init=1)
    prostate_km_sci.fit(prostate_sci)
    print prostate_km_sci.cluster_centers_



if __name__ == "__main__":
    pyunit_utils.standalone_test(prostateKmeans)
else:
    prostateKmeans()
