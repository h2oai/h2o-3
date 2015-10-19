



def framesliceGBM():
  
  

  #Log.info("Importing prostate data...\n")
  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  prostate = prostate[1:9]

  #Log.info("Running GBM on a sliced data frame...\n")
  model = h2o.gbm(x=prostate[1:8], y = prostate[0])


framesliceGBM()
