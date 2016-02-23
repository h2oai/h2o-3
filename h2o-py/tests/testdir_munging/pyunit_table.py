from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from collections import Counter
import itertools


def table_check():
  df = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv"))
  print(df[['AGE','RACE']].table(dense=True).head().as_data_frame(True))
  print(df[['AGE','RACE']].table(dense=False).head().as_data_frame(True))
  print(df[['RACE','AGE']].table(dense=True).head().as_data_frame(True))
  print(df[['RACE','AGE']].table(dense=False).head().as_data_frame(True))
  iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

  # single column (frame)
  table1 = iris["C5"].table()
  assert table1[0,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[0,0], table1[0,1])
  assert table1[1,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[1,0], table1[1,1])
  assert table1[2,1] == 50, "Expected 50 of {0}, but got {1}".format(table1[2,0], table1[2,1])

  # two-column (one argument)
  
  #dense
  table2 = iris["C1"].table(iris["C5"])
  
  #not dense
  table3 = iris["C1"].table(iris["C5"],dense=False)
  
  #check same value
  assert (table3[table3['C1'] == 5,'Iris-setosa'] == table2[(table2['C1'] == 5) & (table2['C5'] == 'Iris-setosa'),'Counts']).all()
  
  assert (table2 == iris[["C1","C5"]].table()).all()
  assert (table3 == iris[["C1","C5"]].table(dense=False)).all()

  cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))
  table = cars[2].table().as_data_frame(False)
  table = dict(table[1:])
  table = {k:int(v) for k,v in list(table.items())}
  expected = Counter(itertools.chain(*cars[2].as_data_frame(False)[1:]))
  assert table == expected, "Expected {} for table counts but got {}".format(expected, table)
  
if __name__ == "__main__":
  pyunit_utils.standalone_test(table_check)
else:
  table_check()
