from __future__ import division
from past.utils import old_div
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def insert_missing():
  air_path = [pyunit_utils.locate("smalldata/airlines/allyears2k_headers.zip")]

  data = h2o.H2OFrame.from_python([[5,4],[2,4],[3,4],[3,5]], column_types=["enum"]*2)
  print (data.levels())
  print(data.nlevels())
  data.structure()



if __name__ == "__main__":
    pyunit_utils.standalone_test(insert_missing)
else:
    insert_missing()
