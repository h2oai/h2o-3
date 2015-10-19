



def get_modelGBM():
  
  

  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  prostate.describe()
  prostate[1] = prostate[1].asfactor()
  prostate_gbm = h2o.gbm(y=prostate[1], x=prostate[2:9], distribution="bernoulli")
  prostate_gbm.show()

  prostate_gbm.predict(prostate)
  model = h2o.get_model(prostate_gbm._id)
  model.show()


get_modelGBM()
