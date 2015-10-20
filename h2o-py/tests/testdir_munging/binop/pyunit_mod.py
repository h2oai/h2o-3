



def frame_as_list():
  
  

  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv.zip"))

  (prostate % 10).show()
  (prostate[4] % 10).show()


  airlines = h2o.import_file(path=pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip"))

  (airlines["CRSArrTime"] % 100).show()


frame_as_list()
