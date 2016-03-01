from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def pyunit_set_item():

  pros = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))

  id_col = pros['ID']
  replacement_val = 10
  replaced_val = 4
  id_col[id_col==replaced_val] = replacement_val
  for i in list(range(replaced_val-1)) + list(range(replaced_val,pros.nrow)): assert id_col[(i,0)] == i+1
  assert id_col[(replaced_val-1,0)] == replacement_val
  
if __name__ == "__main__":
  pyunit_utils.standalone_test(pyunit_set_item)
else:
  pyunit_set_item()
