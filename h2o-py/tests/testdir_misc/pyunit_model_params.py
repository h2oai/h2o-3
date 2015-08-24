import sys
sys.path.insert(1, "../../")
import h2o

def pyunit_model_params(ip,port):

  pros = h2o.import_file(h2o.locate("smalldata/prostate/prostate.csv"))

  m = h2o.kmeans(pros,k=4)
  print m.params
  print m.full_parameters


if __name__ == "__main__":
  h2o.run_test(sys.argv, pyunit_model_params)
