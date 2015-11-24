import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def pubdev_2371():
  d = h2o.H2OFrame.from_python(
    {'a':[3.2,'NA', None, 'nan'], 'b':['x','NA',None,'']}
    ,column_types=['numeric','string'])
  print d
  print d.types
  print d == ''

  assert (d["b"] == None).sum() == 2
  assert (d["b"] != None).sum() == 2

  assert (d[d["b"]!=None,"a"].shape == (2,1))


if __name__ == "__main__":
  pyunit_utils.standalone_test(pubdev_2371)
else:
  pubdev_2371()
