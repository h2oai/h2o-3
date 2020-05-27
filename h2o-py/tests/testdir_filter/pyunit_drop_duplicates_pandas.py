import sys
sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
from pandas import DataFrame
import pandas._testing as tm

# Compares dropping duplicates with pandas directly
def pubdev_drop_duplicates():
    df = DataFrame(
        {
            "AAA": ["foo", "bar", "foo", "bar", "foo", "bar", "bar", "foo"],
            "B": ["one", "one", "two", "two", "two", "two", "one", "two"],
            "C": [1, 1, 2, 2, 2, 2, 1, 2],
            "D": range(8),
        }
    )

    h2o_df = h2o.H2OFrame(df);
    # single column
    result = h2o_df.drop_duplicates(["AAA"]).as_data_frame()
    expected = df[:2]
    tm.assert_frame_equal(result, expected)

    result = h2o_df.drop_duplicates(["AAA"], keep="last").as_data_frame()
    expected = df.loc[[6, 7]].reset_index(drop=True) # Index has to be re-set, as H2O treats it differently
    tm.assert_frame_equal(result, expected)

    # multi column
    expected = df.loc[[0, 1, 2, 3]].reset_index(drop=True)
    result = h2o_df.drop_duplicates(["AAA", "B"]).as_data_frame()
    tm.assert_frame_equal(result, expected)
    result = h2o_df.drop_duplicates(["AAA", "B"]).as_data_frame()
    tm.assert_frame_equal(result, expected)

    result = h2o_df.drop_duplicates(["AAA", "B"], keep="last").as_data_frame()
    expected = df.loc[[0, 5, 6, 7]].reset_index(drop=True)
    tm.assert_frame_equal(result, expected)

if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_drop_duplicates)
else:
    pubdev_drop_duplicates()
