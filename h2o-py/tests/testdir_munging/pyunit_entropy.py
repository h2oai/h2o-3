import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def entropy_check():

  for parse_type in ('string', 'enum'):
    frame = h2o.H2OFrame.from_python(["redrum"], column_types=[parse_type])
    g = frame.entropy()
    assert abs(g[0,0] - 2.25162916739) < 1e-6 

  #test NA values
  string = h2o.H2OFrame.from_python([["nothing"],["NA"]], column_types=['string'], na_strings=["NA"])
  enum = h2o.H2OFrame.from_python([["nothing"],["NA"]], column_types=['enum'], na_strings=["NA"])
  assert ((string.entropy().isna()) == h2o.H2OFrame([[0],[1]])).all()
  assert ((enum.entropy().isna()) == h2o.H2OFrame([[0],[1]])).all()
  
  # #test empty strings
  string = h2o.H2OFrame.from_python([''], column_types=['string'])
  enum = h2o.H2OFrame.from_python([''], column_types=['enum'])
  assert string.entropy()[0,0] == 0
  assert enum.entropy()[0,0] == 0

if __name__ == "__main__":
  pyunit_utils.standalone_test(entropy_check)
else:
  entropy_check()
