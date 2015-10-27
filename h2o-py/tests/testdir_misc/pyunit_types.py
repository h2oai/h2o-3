import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

import numpy as np

def pyunit_types():

  pros = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
  types = pros.types
  print types

  pros[1] = pros[1].asfactor()

  types2 = pros.types

  print types2

  df = h2o.H2OFrame(zip(*np.random.randn(100,4).tolist()), column_names=list("ABCD"), column_types=["Enum"]*4)
  assert df.types == {"A": "enum", "C": "enum", "B": "enum", "D": "enum"}, "Expected {} for column types " \
                      "but got {}".format({"A": "enum", "C": "enum", "B": "enum", "D": "enum"}, df.types)

  df = h2o.H2OFrame(zip(*np.random.randn(100,4).tolist()))
  assert df.types == {"C3": "real", "C2": "real", "C1": "real", "C4": "real"}, "Expected {}" \
          " for column types but got {}".format({"C3": "real", "C2": "real", "C1": "real",
                                                "C4": "real"}, df.types)



if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_types)
else:
    pyunit_types()