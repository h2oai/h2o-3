from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

def loco_regression():
   iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
   g = h2o.h2o.H2OGradientBoostingEstimator()
   g.train(x=list(range(0,3)),y="C4",training_frame=iris_h2o)

   #Run LOCO
   g.loco(iris_h2o)
   g.loco(iris_h2o, replace_val="mean")
   g.loco(iris_h2o, replace_val="median")

if __name__ == "__main__":
    pyunit_utils.standalone_test(loco_regression)
else:
    loco_regression()