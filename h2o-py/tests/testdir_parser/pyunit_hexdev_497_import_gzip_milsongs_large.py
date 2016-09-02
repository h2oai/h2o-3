from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# This test is to make sure that we have fixed the following JIRA properly using milsongs data:
# HEXDEV-497: Merged Gzip Files not read properly.
# I will import the original files and then the zip directory and compare them to see if they are the same.

def import_folder():

  tol_time = 200              # comparing in ms or ns
  tol_numeric = 1e-5          # tolerance for comparing other numeric fields
  numElements2Compare = 100   # choose number of elements per column to compare.  Save test time.

  # compressed the whole directory of files.
  multi_file_gzip_comp = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/parser/hexdev_497/milsongs_csv.zip"))

  # directory containing the gzip version of csv files here.
  multi_file_csv = h2o.import_file(path=pyunit_utils.locate("bigdata/laptop/parser/hexdev_497/milsongs_csv_gzip"))

  # make sure the two agrees
  assert pyunit_utils.compare_frames(multi_file_csv, multi_file_gzip_comp, numElements2Compare, tol_time, tol_numeric,
                                       True), "H2O frame parsed from multiple orc and single orc files are different!"


if __name__ == "__main__":
  pyunit_utils.standalone_test(import_folder)
else:
  import_folder()
