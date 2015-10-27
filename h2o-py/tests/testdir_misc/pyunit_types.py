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


if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_types)
else:
    pyunit_types()