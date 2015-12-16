from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def lists_equal(l1,l2):
  return len(l1) == len(l2) and sorted(l1) == sorted(l2)

def pyunit_types():

  fr = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  include_cols = fr.filter_na_cols()  # should be all columns
  assert lists_equal(include_cols, list(range(fr.ncol)))

  fr[1,1] = None  # make a value None, filter out the second column

  include_cols = fr.filter_na_cols(0.001)
  print(include_cols)
  assert lists_equal(include_cols, [0,2,3,4,5,6,7,8])

if __name__ == "__main__":
  pyunit_utils.standalone_test(pyunit_types)
else:
  pyunit_types()
