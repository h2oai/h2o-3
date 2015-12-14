import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def nacnt():
  fr = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
  nacnts1 = fr.nacnt()
  assert all([i==0 for i in nacnts1])
  fr.insert_missing_values()
  nacnts2 = fr.nacnt()
  assert all([i>0 for i in nacnts2])

if __name__ == "__main__":
  pyunit_utils.standalone_test(nacnt)
else:
  nacnt()