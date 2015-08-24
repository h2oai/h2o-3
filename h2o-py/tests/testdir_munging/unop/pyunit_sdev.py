##
# Test out the sdev() functionality
# If NAs in the frame, they are skipped in calculation unless na.rm = F
# If any categorical columns, throw an error
##

import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np

def sdev(ip,port):
  
  

  iris_h2o = h2o.import_file(path=h2o.locate("smalldata/iris/iris_wheader.csv"))
  iris_np = np.genfromtxt(h2o.locate("smalldata/iris/iris_wheader.csv"),
                          delimiter=',',
                          skip_header=1,
                          usecols=(0, 1, 2, 3))

  sd_np = np.std(iris_np, axis=0, ddof=1)
  for i in range(4):
    sd_h2o = iris_h2o[i].sd()
    assert abs(sd_np[i] - sd_h2o) < 1e-10, "expected standard deviations to be the same"

  iris_h2o[0:2].sd()
  
if __name__ == "__main__":
  h2o.run_test(sys.argv, sdev)
