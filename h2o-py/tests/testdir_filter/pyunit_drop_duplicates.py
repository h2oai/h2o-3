import sys
sys.path.insert(1, "../../")
import h2o
from tests import pyunit_utils
from pandas.util.testing import assert_frame_equal

def pubdev_drop_duplicates():
    # Test using column name
    insurance = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/insurance.csv"))
    deduplicated_frame = insurance.drop_duplicates(columns=["District"], keep="first")
    
    assert deduplicated_frame is not None
    assert deduplicated_frame.nrows == 4
    insurance.names == deduplicated_frame.names

    # Test using column index
    deduplicated_frame = insurance.drop_duplicates(columns=[0], keep="first")
    assert deduplicated_frame is not None
    assert deduplicated_frame.nrows == 4
    insurance.names == deduplicated_frame.names

    # Test using two columns
    deduplicated_frame = insurance.drop_duplicates(columns=["District", "Group"], keep="first")
    assert deduplicated_frame is not None
    assert deduplicated_frame.nrows == 16
    insurance.names == deduplicated_frame.names

    # Compare INSURANCE to pandas - keep first
    compare_dataset_deduplication("smalldata/glm_test/insurance.csv", ["District", "Group"], "first")
    compare_dataset_deduplication("smalldata/glm_test/insurance.csv", ["District", "Group"], "last")

    # Compare Airlines to PANDAS
    compare_dataset_deduplication("smalldata/testng/airlines_train.csv", ["Origin", "Dest", "Distance"], "first")
    compare_dataset_deduplication("smalldata/testng/airlines_train.csv", ["Origin", "Dest", "Distance"], "last")

    # Compare IRIS to PANDAS
    compare_dataset_deduplication("smalldata/extdata/iris_wheader.csv", ["sepal_len", "class"], "first")
    compare_dataset_deduplication("smalldata/extdata/iris_wheader.csv", ["sepal_len", "class"], "last")
    
    # Compare CREDIT CARD to PANDAS
    compare_dataset_deduplication("smalldata/kaggle/CreditCard/creditcard_train_cat.csv", ["SEX", "EDUCATION", "MARRIAGE"], "first")
    compare_dataset_deduplication("smalldata/kaggle/CreditCard/creditcard_train_cat.csv", ["SEX", "EDUCATION", "MARRIAGE"], "last")

    # Compare WEATHER - no duplicates (date column is used)
    compare_dataset_deduplication("smalldata/junit/weather.csv", ["Date"], "first")
    compare_dataset_deduplication("smalldata/junit/weather.csv", ["Date"], "last")

    # Compare CHESS - contains NAs
    compare_dataset_deduplication("smalldata/parser/orc/orc2csv/chess_train_NA.csv", ["C2"], "first")
    compare_dataset_deduplication("smalldata/parser/orc/orc2csv/chess_train_NA.csv", ["C2"], "last")
    


def compare_dataset_deduplication(dataPath, columns, keep):
    print(dataPath)
    data = h2o.import_file(pyunit_utils.locate(dataPath))
    deduplicated_frame = data.drop_duplicates(columns=columns, keep=keep)
    
    data_pd = data.as_data_frame()
    deduplicated_frame_pd = data_pd.drop_duplicates(subset=columns, keep=keep)
    deduplicated_frame_pd.reset_index(drop=True, inplace=True)
    assert_frame_equal(deduplicated_frame_pd, deduplicated_frame.as_data_frame())


if __name__ == "__main__":
    pyunit_utils.standalone_test(pubdev_drop_duplicates)
else:
    pubdev_drop_duplicates()
