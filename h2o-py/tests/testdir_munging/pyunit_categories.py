import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils


def pyunit_categories():

    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))
    category_list =  iris['C5'].categories()
    print(category_list)
    assert set(category_list) == set(['Iris-setosa', 'Iris-versicolor', 'Iris-virginica'])



if __name__ == "__main__":
    pyunit_utils.standalone_test(pyunit_categories)
else:
    pyunit_categories()