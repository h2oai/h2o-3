import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# I am testing a bug in parser that will run into null pointer when parsing 100k.svm file.  It is okay in
# this case not to have an assert at the end of this test.  Please do not remove this test.
def test_parser_svmlight_column_skip():
  f1 = h2o.import_file(pyunit_utils.locate("bigdata/laptop/parser/100k.svm")) 

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_parser_svmlight_column_skip)
else:
  test_parser_svmlight_column_skip()
