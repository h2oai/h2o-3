import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def cbind():
  # Connect to a pre-existing cluster
  

  hdf = h2o.import_file(path=pyunit_utils.locate('smalldata/jira/pub-180.csv'))
  otherhdf = h2o.import_file(path=pyunit_utils.locate('smalldata/jira/v-11.csv'))
  rows, cols = hdf.dim

  ##################################
  ##### non-mutating h2o.cbind #####
  ##################################
  # frame to frame
  hdf2 = hdf.cbind(hdf)
  rows2, cols2 = hdf2.dim
  assert rows == 12 and cols == 4, "unexpected dimensions in original"
  assert rows2 == 12 and cols2 == 8, "unexpected dimensions in result"

  # vec to vec
  xx = hdf[0]
  yy = hdf[1]
  hdf3 = xx.cbind(yy)
  rows3, cols3 = hdf3.dim
  assert rows == 12 and cols == 4, "unexpected dimensions in original"
  assert rows3 == 12 and cols3 == 2, "unexpected dimensions in result"

  # vec to frame
  hdf4 = hdf.cbind(hdf[1])
  rows4, cols4 = hdf4.dim
  assert rows == 12 and cols == 4, "unexpected dimensions in original"
  assert rows4 == 12 and cols4 == 5, "unexpected dimensions in result"

  # frame to vec
  hdf5 = yy.cbind(hdf)
  rows5, cols5 = hdf5.dim
  assert rows == 12 and cols == 4, "unexpected dimensions in original"
  assert rows5 == 12 and cols5 == 5, "unexpected dimensions in result"

  # logical expressions
  hdf6 = (hdf[2] <= 5).cbind(hdf[3] >= 4)
  rows6, cols6 = hdf6.dim
  assert rows6 == 12 and cols6 == 2, "unexpected dimensions in result"

  # sets column names correctly
  hdf_names = xx.cbind(yy).names
  assert hdf_names == ['colgroup', 'colgroup2'], "expected column names to be the same"

  # unequal rows should fail
  try:
    hdf7 = hdf.cbind(otherhdf).frame_id
    assert False, "expected an error"
  except EnvironmentError:
    assert True


  ###################################
  ##### mutating H2OFrame.cbind #####
  ###################################
  # frame to frame
  hdf = hdf.cbind(hdf)
  rows, cols = hdf.dim
  assert rows == 12 and cols == 8, "unexpected dimensions in result"

  # frame to vec
  hdf = hdf.cbind(yy)
  rows, cols = hdf.dim
  assert rows == 12 and cols == 9, "unexpected dimensions in result"

  # logical expressions
  hdf = hdf.cbind(hdf[2] <= 5)
  rows, cols = hdf.dim
  assert rows == 12 and cols == 10, "unexpected dimensions in result"

  # sets column names correctly
  assert hdf.names == ['colgroup','colgroup2','col1','col2','colgroup0','colgroup20','col10','col20','colgroup21','col11'],\
    "expected column names to be the same"

  # unequal rows should fail
  #try:
  #  hdf.cbind(otherhdf)
  #  assert False, "expected an error"
  #except EnvironmentError:
  #  assert True

  ###################################
  ##### non-mutating H2OVec.cbind ###
  ###################################
  hdf = h2o.import_file(path=pyunit_utils.locate('smalldata/jira/pub-180.csv'))
  rows, cols = hdf.dim

  # vec to frame
  hdf8 = hdf[1].cbind(hdf)
  rows8, cols8 = hdf8.dim
  assert rows == 12 and cols == 4, "unexpected dimensions in original"
  assert rows8 == 12 and cols8 == 5, "unexpected dimensions in result"

  # vec to vec
  hdf9 = hdf[1].cbind(hdf[2])
  rows9, cols9 = hdf9.dim
  assert rows == 12 and cols == 4, "unexpected dimensions in original"
  assert rows9 == 12 and cols9 == 2, "unexpected dimensions in result"

  # logical expressions
  hdf10 = (hdf[3] >= 4).cbind(hdf[2] <= 5)
  rows10, cols10 = hdf10.dim
  assert rows == 12 and cols == 4, "unexpected dimensions in original"
  assert rows10 == 12 and cols10 == 2, "unexpected dimensions in result"

  # sets column names correctly
  hdf_names = xx.cbind(yy).names
  assert hdf_names == ['colgroup', 'colgroup2'], "expected column names to be the same"




if __name__ == "__main__":
    pyunit_utils.standalone_test(cbind)
else:
    cbind()
