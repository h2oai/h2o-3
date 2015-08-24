import sys
sys.path.insert(1, "../../")
import h2o, tests

def pyunit_types(ip,port):

  pros = h2o.import_file(h2o.locate("smalldata/prostate/prostate.csv"))
  types = pros.types
  print types

  pros[1] = pros[1].asfactor()

  types2 = pros.types

  print types2

if __name__ == "__main__":
  tests.run_test(sys.argv, pyunit_types)
