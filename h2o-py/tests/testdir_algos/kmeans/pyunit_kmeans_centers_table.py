from __future__ import division
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.kmeans import H2OKMeansEstimator
from h2o.two_dim_table import H2OTwoDimTable


def kmeans_centers_as_table():
  ozone_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/ozone.csv"))

  model = H2OKMeansEstimator(k=3, max_iterations=5)
  model.train(training_frame=ozone_h2o)
  
  centers = model.centers(as_table=True)
  centers_std = model.centers_std(as_table=True)
  
  def check_structure(o):
    print(o)
    assert isinstance(o, H2OTwoDimTable)
    assert ["centroid", "ozone", "radiation", "temperature", "wind"] == o.col_header

  check_structure(centers)
  check_structure(centers_std)


if __name__ == "__main__":
  pyunit_utils.standalone_test(kmeans_centers_as_table)
else:
  kmeans_centers_as_table()
