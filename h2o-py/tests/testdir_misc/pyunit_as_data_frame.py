import sys
sys.path.insert(1, "../../")
import h2o, tests


def pyunit_as_data_frame():

  smallbike = h2o.import_file(tests.locate("smalldata/jira/citibike_head.csv"))

  ##use_pandas = False
  small_bike_list = smallbike.as_data_frame(use_pandas=False)
  assert isinstance(small_bike_list, list)
  assert len(small_bike_list) == smallbike.nrow + 1 #one extra for header
  assert len(small_bike_list[0]) == smallbike.ncol

  truncated_small_bike = smallbike.as_data_frame(nrows=3, ncols=6, use_pandas=False)
  assert len(truncated_small_bike) == 3 + 1
  assert len(truncated_small_bike[-1]) == 6

  head_small_bike = smallbike.head(rows=5, cols=2, use_pandas=False)
  tail_small_bike = smallbike.tail(rows=5, cols=2, use_pandas=False)
  assert len(head_small_bike) == len(tail_small_bike) == 5 + 1
  assert len(head_small_bike[3]) == len(tail_small_bike[4]) == 2
  assert head_small_bike[-1] == tail_small_bike[1]

  ##use_pandas = True
  small_bike_pandas = smallbike.as_data_frame(use_pandas=True)
  assert small_bike_pandas.__class__.__name__ == "DataFrame"
  assert small_bike_pandas.shape == (smallbike.nrow, smallbike.ncol)

  truncated_small_bike_pandas = smallbike.as_data_frame(nrows=3, ncols=6, use_pandas=True)
  assert truncated_small_bike_pandas.shape == (3,6)

  head_small_bike_pandas = smallbike.head(rows=5, use_pandas=True)
  tail_small_bike_pandas = smallbike.tail(rows=5, use_pandas=True)
  assert head_small_bike_pandas.shape == tail_small_bike_pandas.shape == (5,smallbike.ncol)
  assert head_small_bike_pandas.loc[1][2] == small_bike_pandas.loc[1][2]
  assert tail_small_bike_pandas.loc[2][3] == small_bike_pandas.loc[6][3]
  assert head_small_bike_pandas.loc[4][0] == tail_small_bike_pandas.loc[0][0]

if __name__ == "__main__":
  tests.run_test(sys.argv, pyunit_as_data_frame)
