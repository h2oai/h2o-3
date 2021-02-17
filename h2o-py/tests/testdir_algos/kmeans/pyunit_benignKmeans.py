from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils

import numpy as np
from sklearn.cluster import KMeans
from sklearn.impute import SimpleImputer
from h2o.estimators.kmeans import H2OKMeansEstimator


def benign_kmeans():
    print("Importing benign.csv data...")
    benign_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/benign.csv"))

    benign_sci = np.genfromtxt(pyunit_utils.locate("smalldata/logreg/benign.csv"), delimiter=",")
    # Impute missing values with column mean
    imp = SimpleImputer(missing_values=np.nan, strategy="mean")
    benign_sci = imp.fit_transform(benign_sci)

    for i in range(1,7):
        print("H2O K-Means with " + str(i) + " clusters:")
        benign_h2o_km = H2OKMeansEstimator(k=i)
        benign_h2o_km.train(x=list(range(benign_h2o.ncol)), training_frame=benign_h2o)
        print("H2O centers")
        print(benign_h2o_km.centers())

        benign_sci_km = KMeans(n_clusters=i, init='k-means++', n_init=1)
        benign_sci_km.fit(benign_sci)
        print("sckit centers")
        print(benign_sci_km.cluster_centers_)


if __name__ == "__main__":
    pyunit_utils.standalone_test(benign_kmeans)
else:
    benign_kmeans()
