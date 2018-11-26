from __future__ import print_function
import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import os

def test_parser_svmlight_column_skip():
  # generate a big frame with all datatypes and save it to svmlight
  nrow = 10000
  ncol = 50
  seed=12345

  f1 = h2o.create_frame(rows=nrow, cols=ncol, real_fraction=0.5, integer_fraction=0.5, missing_fraction=0.2,
                         has_response=False, seed=seed)
  f2 = h2o.create_frame(rows=nrow, cols=1, real_fraction=1, integer_fraction=0, missing_fraction=0,
                         has_response=False, seed=seed)
  f2.set_name(0,"target")
  f1 = f2.cbind(f1)

  tmpdir = os.path.normpath(os.path.join(os.path.dirname(os.path.realpath('__file__')), "..", "results"))
  if not(os.path.isdir(tmpdir)):
    os.mkdir(tmpdir)
  savefilenamewithpath = os.path.join(tmpdir, 'out.svm')
  pyunit_utils.write_H2OFrame_2_SVMLight(savefilenamewithpath, f1) # write h2o frame to svm format

  skip_all = list(range(ncol))
  skip_even = list(range(0, ncol, 2))

  try:
    loadFileSkipAll = h2o.upload_file(savefilenamewithpath, skipped_columns = skip_all)
    sys.exit(1) # should have failed here
  except:
    pass

  try:
    importFileSkipAll = h2o.import_file(savefilenamewithpath, skipped_columns = skip_all)
    sys.exit(1) # should have failed here
  except:
    pass

  try:
    importFileSkipSome = h2o.import_file(savefilenamewithpath, skipped_columns = skip_even)
    sys.exit(1) # should have failed here
  except:
    pass

  # check for correct parsing only
  checkCorrectSkips(savefilenamewithpath, f1)


def checkCorrectSkips(csvfile, originalFrame):
  skippedFrameUF = h2o.upload_file(csvfile)
  skippedFrameIF = h2o.import_file(csvfile) # this two frames should be the same
  pyunit_utils.compare_frames_local(skippedFrameUF, skippedFrameIF, prob=1)

  # test with null skipped_column list
  skippedFrameUF2 = h2o.upload_file(csvfile, skipped_columns=[])
  skippedFrameIF2 = h2o.import_file(csvfile, skipped_columns=[]) # this two frames should be the same
  pyunit_utils.compare_frames_local(skippedFrameUF2, skippedFrameIF2, prob=1)

  # frame from not skipped_columns specification and empty skipped_columns should return same result
  pyunit_utils.compare_frames_local(skippedFrameUF2, skippedFrameIF, prob=1)

  # compare skipped frame with originalFrame
  assert originalFrame.ncol==skippedFrameUF.ncol, \
    "Expected return frame column number: {0}, actual frame column number: " \
    "{1}".format((originalFrame.ncol, skippedFrameUF.ncol))
  pyunit_utils.compare_frames_local_svm(originalFrame, skippedFrameIF2, prob=1)

if __name__ == "__main__":
  pyunit_utils.standalone_test(test_parser_svmlight_column_skip)
else:
  test_parser_svmlight_column_skip()
