from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# test that h2o.import_file works on a directory of files!
def import_folder():

  cars = h2o.import_file(path=pyunit_utils.locate("smalldata/synthetic_perfect_separation"))  # without trailing slash
  print(cars.head())
  cars = h2o.import_file(path=pyunit_utils.locate("smalldata/synthetic_perfect_separation/"))  # with trailing slash
  print(cars.head())

if __name__ == "__main__":
  pyunit_utils.standalone_test(import_folder)
else:
  import_folder()
