from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

def runif_check():

  fr = h2o.H2OFrame([[r] for r in range(1,1001)])
  runif1 = fr[0].runif(1234)
  runif2 = fr[0].runif(1234)
  runif3 = fr[0].runif(42)

  assert (runif1 == runif2).all(), "Expected runif with the same seeds to return the same values."
  assert not (runif1 == runif3).all(), "Expected runif with different seeds to return different values."

if __name__ == "__main__":
  pyunit_utils.standalone_test(runif_check)
else:
  runif_check()
