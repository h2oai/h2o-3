



def pyunit_model_params():

  pros = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))

  m = h2o.kmeans(pros,k=4)
  print m.params
  print m.full_parameters



pyunit_model_params()
