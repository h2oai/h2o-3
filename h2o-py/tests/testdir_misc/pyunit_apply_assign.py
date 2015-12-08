from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def pyunit_apply_assign():
  
  fr = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  bool_fr = fr.apply(lambda x: x['PSA'] > x['VOL'],axis=1)
  h2o.assign(fr.cbind(bool_fr), 'supp_fr')
  print(h2o.get_frame('supp_fr'))

if __name__ == "__main__":
  pyunit_utils.standalone_test(pyunit_apply_assign)
else:
  pyunit_apply_assign()