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


def get_model_kmeans():
    print("Importing benign.csv data...")
    benign_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/benign.csv"))

    benign_sci = np.genfromtxt(pyunit_utils.locate("smalldata/logreg/benign.csv"), delimiter=",")
    # Impute missing values with column mean
    imp = SimpleImputer(missing_values=np.nan, strategy="mean")
    benign_sci = imp.fit_transform(benign_sci)

    for i in range(2,7):
        km_h2o = H2OKMeansEstimator(k=i)
        km_h2o.train(x=list(range(benign_h2o.ncol)), training_frame=benign_h2o)
        km_h2o.show()
        model = h2o.get_model(km_h2o._id)
        model.show()

        km_sci = KMeans(n_clusters=i, init='k-means++', n_init=1)
        km_sci.fit(benign_sci)
        print("sckit centers")
        print(km_sci.cluster_centers_)


if __name__ == "__main__":
    pyunit_utils.standalone_test(get_model_kmeans)
else:
    get_model_kmeans()
