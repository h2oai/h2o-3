import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


#analogous to runit_empty_strings.R
def empty_strings():

  #dictionary
  d = h2o.H2OFrame(
    {'e':["",""]
    ,'c':["",""]
    ,'f':["",""]
     }
  )
  assert d.isna().sum().sum(axis=1)[:,0] == 0
  assert (d == '').sum().sum(axis=1)[:,0] == d.nrow * d.ncol

  #single list
  d = h2o.H2OFrame([""]*4)
  assert d.isna().sum().sum(axis=1)[:,0] == 0
  assert (d == '').sum().sum(axis=1)[:,0] == d.nrow * d.ncol

  #list of lists
  d = h2o.H2OFrame([[""]*4]*3)
  assert d.isna().sum().sum(axis=1)[:,0] == 0
  assert (d == '').sum().sum(axis=1)[:,0] == d.nrow * d.ncol


if __name__ == "__main__":
  pyunit_utils.standalone_test(empty_strings)
else:
  empty_strings()
