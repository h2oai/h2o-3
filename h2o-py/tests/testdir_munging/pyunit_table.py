from __future__ import print_function
from builtins import zip
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from collections import Counter
import itertools


def table_check():
  # Connect to a pre-existing cluster

  iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

  # single column (frame)
  table1 = iris["C5"].table()
  assert table1[0,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[0,0], table1[0,1])
  assert table1[1,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[1,0], table1[1,1])
  assert table1[2,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[2,0], table1[2,1])

  # two-column (one argument)
  table2 = iris["C1"].table(iris["C5"])
  print(table2)

  cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
  table = cars[2].table().as_data_frame()
  table = dict(table[1:])
  table = {k:int(v) for k,v in list(table.items())}
  expected = Counter(itertools.chain(*cars[2].as_data_frame()[1:]))
  assert table == expected, "Expected {} for table counts but got {}".format(expected, table)
  


if __name__ == "__main__":
  pyunit_utils.standalone_test(table_check)
else:
  table_check()
