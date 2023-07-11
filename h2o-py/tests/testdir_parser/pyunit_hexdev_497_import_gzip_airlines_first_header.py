import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils

# This test is to make sure that we have fixed the following issue properly using airlines data:
# https://github.com/h2oai/private-h2o-3/issues/341: Merged Gzip Files not read properly.
# I will import the original files and then the zip directory and compare them to see if they are the same.
#
# In this test, the first, third and fifth files have headers while the second and fourth do not.  Just curious
# to see what is going on.
#
# Note that in this test, I did not compare the content of the two frames due to different OS will
# read in the file in different order.  Hence, the only comparison here is the frame summary per
# suggestion from Michal K.

def import_folder():
  multi_file_csv = h2o.import_file(path=pyunit_utils.locate("smalldata/parser/hexdev_497/airlines_first_header"))
  multi_file_gzip_comp = \
    h2o.import_file(path=pyunit_utils.locate("smalldata/parser/hexdev_497/airlines_first_header.zip"))

  multi_file_gzip_comp.summary()
  zip_summary = h2o.frame(multi_file_gzip_comp.frame_id)["frames"][0]["columns"]

  multi_file_csv.summary()
  csv_summary = h2o.frame(multi_file_csv.frame_id)["frames"][0]["columns"]
  pyunit_utils.compare_frame_summary(zip_summary, csv_summary)

if __name__ == "__main__":
  pyunit_utils.standalone_test(import_folder)
else:
  import_folder()
