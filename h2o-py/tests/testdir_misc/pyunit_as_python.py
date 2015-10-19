

import h2o, tests

def as_python_test():
  
  

  iris = h2o.import_file(path=tests.locate("smalldata/iris/iris_wheader.csv"))
  prostate = h2o.import_file(path=tests.locate("smalldata/prostate/prostate.csv.zip"))
  airlines = h2o.import_file(path=tests.locate("smalldata/airlines/allyears2k.zip"))

  iris.show()
  prostate.show()
  airlines.show()


  print h2o.as_list(iris)

  print h2o.as_list(prostate)

  print h2o.as_list(airlines)


pyunit_test = as_python_test
