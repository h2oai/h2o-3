import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def isin_check():

  cars = h2o.import_file(path=pyunit_utils.locate("smalldata/junit/cars_20mpg.csv"))

  assert (cars[0].isin("AMC Gremlin") == (cars[0] == "AMC Gremlin")).all()
  assert (cars[2].isin(6) == (cars[2] == 6)).all()
  assert not (cars.isin(["AMC Gremlin","AMC Concord DL"]) == cars.isin("AMC Gremlin")).all()
  assert (cars.isin(["AMC Gremlin","AMC Concord DL",6]) == cars.isin("AMC Gremlin") + cars.isin("AMC Concord DL") 
          + cars.isin(6)).all()


def test_isin_issue_16043():
    frame = h2o.upload_file(pyunit_utils.locate("smalldata/titanic/titanic_expanded.csv"))
    column = "cabin"
    levels = frame[column].levels()[0][:-10]
    assert len(levels) > 10
    print(frame[column].isin(levels).unique())
    # expecting a binary vector as in pandas
    assert frame[column].isin(levels).unique().nrows == 2
    assert frame.as_data_frame(use_pandas=True)[column].isin(levels).nunique() == 2
  

if __name__ == "__main__":
  pyunit_utils.standalone_test(isin_check)
  pyunit_utils.standalone_test(test_isin_issue_16043)
else:
  isin_check()
  test_isin_issue_16043()
