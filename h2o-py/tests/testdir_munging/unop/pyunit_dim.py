##
# Test out the dim() functionality
# If data frame, get back a vector of two numbers: [1] nrows ncols
# If NAs in the frame, they still count.
# If not a frame, expect NULL
##

import sys
sys.path.insert(1, "../../../")
import h2o
import numpy as np

def dim_checks(ip,port):
  # Connect to h2o
  h2o.init(ip,port)

  # Log.info("Uploading logreg/princeton/cuse.dat")
  h2o_data = h2o.import_frame(path=h2o.locate("smalldata/logreg/prostate.csv"))
  np_data = np.loadtxt(h2o.locate("smalldata/logreg/prostate.csv"), delimiter=',', skiprows=1)

  h2o_rows, h2o_cols = h2o_data.dim()
  np_rows, np_cols = list(np_data.shape)

  print 'The dimensions of h2o frame is: {0} x {1}'.format(h2o_rows, h2o_cols)
  print 'The dimensions of numpy array is: {0} x {1}'.format(np_rows, np_cols)

  assert [h2o_rows, h2o_cols] == [np_rows, np_cols], "expected equal number of columns and rows"

  # Log.info("Slice out a column and data frame it, try dim on it...")

  h2o_slice = h2o_data[4]
  np_slice = np_data[:,4]

  h2o_rows = len(h2o_slice)
  np_rows= np_slice.shape[0]

  print 'The dimension of h2o column slice is: {0} rows'.format(h2o_rows)
  print 'The dimension of numpy array column slice is: {0} rows'.format(np_rows)

  assert h2o_rows == np_rows, "expected equal number of rows"

  # Log.info("OK, now try an operator, e.g. '&', and then check dimensions agao...")

  h2oColAmpFive = h2o_slice & 5

  assert len(h2oColAmpFive) == h2o_rows, "expected the number of rows to remain unchanged"

if __name__ == "__main__":
  h2o.run_test(sys.argv, dim_checks)
