import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
import pandas as pd
from pandas.testing import assert_frame_equal


def test_append_levels():
    species = "C5"
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    iris[species] = iris[species].asfactor()
    assert iris[species].levels()[0] == ["Iris-setosa", "Iris-versicolor", "Iris-virginica"]

    iris[species] = iris[species].append_levels(["levelA", "levelB"])
    assert iris[species].levels()[0] == ["Iris-setosa", "Iris-versicolor", "Iris-virginica", "levelA", "levelB"]

    # appending new levels allows me to use them
    iris[0, species] = "levelA"
    iris[iris.nrow - 1, species] = "levelB"

    counts = iris[species].table().as_data_frame()
    expected_counts = {
        'C5': ["Iris-setosa", "Iris-versicolor", "Iris-virginica", "levelA", "levelB"], 
        'Count': [49, 50, 49, 1, 1]
    }
    expected = pd.DataFrame.from_dict(expected_counts)
    assert_frame_equal(expected, counts)


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_append_levels)
else:
    test_append_levels()
