import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




import numpy as np
from sklearn.cluster import KMeans
from sklearn.preprocessing import Imputer

def benignKmeans():
  # Connect to a pre-existing cluster
  # connect to localhost:54321


  #  Log.info("Importing benign.csv data...\n")
  benign_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/benign.csv"))
  #benign_h2o.summary()

  benign_sci = np.genfromtxt(pyunit_utils.locate("smalldata/logreg/benign.csv"), delimiter=",")
  # Impute missing values with column mean
  imp = Imputer(missing_values='NaN', strategy='mean', axis=0)
  benign_sci = imp.fit_transform(benign_sci)

  # Log.info(paste("H2O K-Means with ", i, " clusters:\n", sep = ""))

  from h2o.estimators.kmeans import H2OKMeansEstimator

  for i in range(1,7):
    benign_h2o_km = H2OKMeansEstimator(k=i)
    benign_h2o_km.train(x = range(benign_h2o.ncol), training_frame=benign_h2o)
    print "H2O centers"
    print benign_h2o_km.centers()

    benign_sci_km = KMeans(n_clusters=i, init='k-means++', n_init=1)
    benign_sci_km.fit(benign_sci)
    print "sckit centers"
    print benign_sci_km.cluster_centers_



if __name__ == "__main__":
  pyunit_utils.standalone_test(benignKmeans)
else:
  benignKmeans()
