import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def substring_check():

  frame = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"), col_types={"C5":"enum"})
  py_data = frame["C5"].as_data_frame()[1:]
  indices = [(1,3),(0,22),(5,6),(6,5),(5,None)]
  for s_i, e_i in indices:
    g = frame["C5"].substring(s_i, e_i)
    assert g[0,0] == py_data[0][0][s_i:e_i]
    data_levels = set(map(lambda x: x[s_i:e_i], list(zip(*py_data))[0]))
    assert set(g.levels()[0]) == data_levels
    assert g.nlevels()[0] == len(data_levels)


if __name__ == "__main__":
  pyunit_utils.standalone_test(substring_check)
else:
  substring_check()
