import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def pro_substring_check():

  #words are from https://raw.githubusercontent.com/dwyl/english-words/master/words.txt
  path = "/Users/ludirehak/Downloads/words.txt"
  for parse_type in ('string', 'enum'):
    frame = h2o.H2OFrame.from_python(["youtube"], column_types=[parse_type])
    g = frame.pro_substrings_words(path)
    assert abs(g - 0.428571) < 1e-6

  #test NA values
  string = h2o.H2OFrame.from_python([["nothing"],["NA"]], column_types=['string'], na_strings=["NA"])
  enum = h2o.H2OFrame.from_python([["nothing"],["NA"]], column_types=['enum'], na_strings=["NA"])
  assert ((string.pro_substrings_words(path).isna()) == h2o.H2OFrame([[0],[1]])).all()
  assert ((enum.pro_substrings_words(path).isna()) == h2o.H2OFrame([[0],[1]])).all()
  
   #test empty strings
  string = h2o.H2OFrame.from_python([''], column_types=['string'])
  enum = h2o.H2OFrame.from_python([''], column_types=['enum'])
  assert string.pro_substrings_words(path)[0,0] == 0
  assert enum.pro_substrings_words(path)[0,0] == 0

if __name__ == "__main__":
  pyunit_utils.standalone_test(pro_substring_check)
else:
  pro_substring_check()
