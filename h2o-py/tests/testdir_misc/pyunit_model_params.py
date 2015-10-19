

import h2o, tests

def pyunit_model_params():

  pros = h2o.import_file(tests.locate("smalldata/prostate/prostate.csv"))

  m = h2o.kmeans(pros,k=4)
  print m.params
  print m.full_parameters



pyunit_test = pyunit_model_params
