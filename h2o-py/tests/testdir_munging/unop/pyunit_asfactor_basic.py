import sys
sys.path.insert(1, "../../../")
import h2o

def asfactor_basic(ip,port):
  
  

  #Log.info("Printing out the head of the cars datasets")
  h2oframe =  h2o.import_file(path=h2o.locate("smalldata/junit/cars.csv"))
  h2oframe.show()

  h2oframe['cylinders'].show()
  foo = h2oframe["cylinders"]
  foo.show()

  h2oframe['cylinders'].asfactor().show()

  meow = h2oframe['cylinders'].asfactor()
  meow.show()

  foo = h2oframe["cylinders"].isfactor()
  assert not foo, "expected the foo H2OVec to be a not factor"

  h2oframe["cylinders"] = h2oframe['cylinders'].asfactor()
  h2oframe.show()

  bar = h2oframe["cylinders"].isfactor()
  assert bar, "expected the bar H2OVec to be a factor"

if __name__ == "__main__":
  h2o.run_test(sys.argv, asfactor_basic)
