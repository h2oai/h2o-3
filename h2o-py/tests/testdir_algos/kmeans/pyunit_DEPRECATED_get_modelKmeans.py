import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




import numpy as np
from sklearn.cluster import KMeans
from sklearn.preprocessing import Imputer

def get_modelKmeans():
    # Connect to a pre-existing cluster
      # connect to localhost:54321

    #Log.info("Importing benign.csv data...\n")
    benign_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/benign.csv"))
    #benign_h2o.summary()

    benign_sci = np.genfromtxt(pyunit_utils.locate("smalldata/logreg/benign.csv"), delimiter=",")
    # Impute missing values with column mean
    imp = Imputer(missing_values='NaN', strategy='mean', axis=0)
    benign_sci = imp.fit_transform(benign_sci)

    for i in range(2,7):
        # Log.info("H2O K-Means")
        km_h2o = h2o.kmeans(x=benign_h2o, k=i)
        km_h2o.show()
        model = h2o.get_model(km_h2o._id)
        model.show()

        km_sci = KMeans(n_clusters=i, init='k-means++', n_init=1)
        km_sci.fit(benign_sci)
        print "sckit centers"
        print km_sci.cluster_centers_




if __name__ == "__main__":
    pyunit_utils.standalone_test(get_modelKmeans)
else:
    get_modelKmeans()
