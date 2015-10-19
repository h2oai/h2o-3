

import h2o, tests

def km_num_iterations():
    # Connect to a pre-existing cluster
      # connect to localhost:54321

    prostate_h2o = h2o.import_file(path=tests.locate("smalldata/logreg/prostate.csv"))

    prostate_km_h2o = h2o.kmeans(x=prostate_h2o[1:], k=3, max_iterations=4)
    num_iterations = prostate_km_h2o.num_iterations()
    assert num_iterations <= 4, "Expected 4 iterations, but got {0}".format(num_iterations)


pyunit_test = km_num_iterations
