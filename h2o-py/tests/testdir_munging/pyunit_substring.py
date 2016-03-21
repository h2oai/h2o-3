import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def substring_check():

  for parse_type in ('string', 'enum'):
    frame = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"), col_types={"C5":parse_type})
    py_data = frame["C5"].as_data_frame(False)[1:]
    indices = [(1,3),(0,22),(5,6),(6,5),(5,None),(9,9)]
    for s_i, e_i in indices:
      g = frame["C5"].substring(s_i, e_i)
      assert g[0,0] == py_data[0][0][s_i:e_i]
      if parse_type == 'enum':
        data_levels = set(map(lambda x: x[s_i:e_i], list(zip(*py_data))[0]))
        if data_levels == {''}: data_levels = set([])
        assert set(g.levels()[0]) == data_levels, set(g.levels()[0])
        assert g.nlevels()[0] == len(data_levels)


  #test negative index args
  string = h2o.H2OFrame.from_python(("nothing",), column_types=['string'])
  enum = h2o.H2OFrame.from_python(("nothing",), column_types=['enum'])
  assert string.substring(-4)[0,0] == 'nothing'
  assert string.substring(-4,-9)[0,0] == ''
  assert enum.substring(-5)[0,0] == 'nothing'
  assert enum.substring(-43,-3)[0,0] == ''
  
  #test NA values
  string = h2o.H2OFrame.from_python([["nothing"],["NA"]], column_types=['string'], na_strings=["NA"])
  enum = h2o.H2OFrame.from_python([["nothing"],["NA"]], column_types=['enum'], na_strings=["NA"])
  assert ((string.substring(2,5)).isna() == h2o.H2OFrame([[0],[1]])).all()
  assert ((enum.substring(2,5)).isna() == h2o.H2OFrame([[0],[1]])).all()
  
  #test empty strings
  string = h2o.H2OFrame.from_python([''], column_types=['string'])
  enum = h2o.H2OFrame.from_python([''], column_types=['enum'])
  assert string.substring(3,6)[0,0] == ''
  assert string.substring(0,0)[0,0] == ''
  assert enum.substring(3,6)[0,0] == ''
  assert enum.substring(0,0)[0,0] == ''

if __name__ == "__main__":
  pyunit_utils.standalone_test(substring_check)
else:
  substring_check()
