import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def pyunit_pop():

  pros = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
  nc = pros.ncol
  popped_col = pros.pop(pros.names[0])

  print pros.dim
  print popped_col.dim

  assert popped_col.ncol==1
  assert pros.ncol==nc-1



if __name__ == "__main__":
  pyunit_utils.standalone_test(pyunit_pop)
else:
  pyunit_pop()
