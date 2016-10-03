from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# This test is to make sure that we have fixed the following JIRA properly using airlines data:
# HEXDEV-497: Merged Gzip Files not read properly.
# I will import the original files and then the zip directory and compare them to see if they are the same.

def import_folder():

  tol_time = 200              # comparing in ms or ns for timestamp columns
  tol_numeric = 1e-5          # tolerance for comparing other numeric fields
  numElements2Compare = 0   # choose number of elements per column to compare.  Save test time.

  multi_file_gzip_comp = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/hexdev_497/airlines_small_csv.zip"))
  multi_file_csv = \
    h2o.import_file(path=pyunit_utils.locate("smalldata/parser/hexdev_497/airlines_small_csv/all_airlines.csv"))

  # make sure H2O frames built from a zip file of a directory and the original files are the same.
  assert pyunit_utils.compare_frames(multi_file_csv, multi_file_gzip_comp, numElements2Compare, tol_time, tol_numeric,
                                     True), "H2O frame parsed from zip directory and unzipped directory are different!"


if __name__ == "__main__":
  pyunit_utils.standalone_test(import_folder)
else:
  import_folder()
