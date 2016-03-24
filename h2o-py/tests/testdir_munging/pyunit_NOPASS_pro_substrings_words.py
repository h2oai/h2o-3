import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def pro_substring_check():

  #words are from https://raw.githubusercontent.com/dwyl/english-words/master/words.txt
  path = "/Users/ludirehak/Downloads/words.txt"
  for parse_type in ('string', 'enum'):
    frame = h2o.H2OFrame.from_python(["youtube"], column_types=[parse_type])
    g = frame.num_valid_substrings(path)
    assert abs(g[0,0] - 9) < 1e-6

  #test NA values
  string = h2o.H2OFrame.from_python([["nothing"],["NA"]], column_types=['string'], na_strings=["NA"])
  enum = h2o.H2OFrame.from_python([["nothing"],["NA"]], column_types=['enum'], na_strings=["NA"])
  assert ((string.num_valid_substrings(path).isna()) == h2o.H2OFrame([[0],[1]])).all()
  assert ((enum.num_valid_substrings(path).isna()) == h2o.H2OFrame([[0],[1]])).all()
  
   #test empty strings
  string = h2o.H2OFrame.from_python([''], column_types=['string'])
  enum = h2o.H2OFrame.from_python([''], column_types=['enum'])
  assert string.num_valid_substrings(path)[0,0] == 0
  assert enum.num_valid_substrings(path)[0,0] == 0

if __name__ == "__main__":
  pyunit_utils.standalone_test(pro_substring_check)
else:
  pro_substring_check()
