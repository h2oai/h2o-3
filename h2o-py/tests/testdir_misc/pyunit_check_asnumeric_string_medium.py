from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def pyunit_asnumeric_string():

  small_test = [pyunit_utils.locate("bigdata/laptop/lending-club/LoanStats3a.csv")]

  print("Import and Parse data")
  types = {"int_rate":"String", "revol_util":"String", "emp_length":"String"}
  data = h2o.import_file(path=small_test, col_types=types)
  assert data['int_rate'].gsub('%','').trim().asnumeric().isna().sum() == 3


if __name__ == "__main__":
  pyunit_utils.standalone_test(pyunit_asnumeric_string)
else:
  pyunit_asnumeric_string()
