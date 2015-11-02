import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils




def insert_missing():
    # Connect to a pre-existing cluster
    

    data = [[1, 2, 3, 1, 'a', 1, 9],
            [1, 6, 4, 2, 'a', 1, 9],
            [2, 3, 8, 6, 'b', 1, 9],
            [3, 4, 3, 2, 'b', 3, 8],
            [4, 5, 9, 5, 'c', 2, 8],
            [5, 7, 10,7, 'b', 8, 8]]
    h2o_data = h2o.H2OFrame(zip(*data))

    h2o_data.insert_missing_values(fraction = 0.0)
    num_nas = sum([h2o_data[c].isna().sum() for c in range(h2o_data.ncol)])
    assert num_nas == 0, "Expected no missing values inserted, but got {0}".format(num_nas)

    h2o_data.insert_missing_values(fraction = 1.0)
    num_nas = sum([h2o_data[c].isna().sum() for c in range(h2o_data.ncol)])
    assert num_nas == h2o_data.nrow*h2o_data.ncol, "Expected all missing values inserted, but got {0}".format(num_nas)




if __name__ == "__main__":
    pyunit_utils.standalone_test(insert_missing)
else:
    insert_missing()
