from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from collections import OrderedDict


def pubdev_2371():
  data = OrderedDict()
  data["a"] = [3.2,'NA', None, 'nan']
  data["b"] = ['x','NA',None,'']
  d = h2o.H2OFrame.from_python(data, column_types=['numeric','string'])
  print(d)
  print(d.types)
  print(d == '')
  print(d["b"])
  print((d["b"]==None).sum())

  assert (d["b"] == None).sum() == 2
  assert (d["b"] != None).sum() == 2

  assert (d[d["b"]!=None,"a"].shape == (2,1))


if __name__ == "__main__":
  pyunit_utils.standalone_test(pubdev_2371)
else:
  pubdev_2371()
