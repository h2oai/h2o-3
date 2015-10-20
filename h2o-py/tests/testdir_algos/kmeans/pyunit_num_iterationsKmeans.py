import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def km_num_iterations():
    # Connect to a pre-existing cluster
      # connect to localhost:54321

    prostate_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

    prostate_km_h2o = h2o.kmeans(x=prostate_h2o[1:], k=3, max_iterations=4)
    num_iterations = prostate_km_h2o.num_iterations()
    assert num_iterations <= 4, "Expected 4 iterations, but got {0}".format(num_iterations)



if __name__ == "__main__":
    pyunit_utils.standalone_test(km_num_iterations)
else:
    km_num_iterations()
