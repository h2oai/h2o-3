import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.transforms.preprocessing import H2OScaler


def test_scaler():
    iris = h2o.import_file(pyunit_utils.locate("smalldata/iris/iris.csv"))

    scaler = H2OScaler()
    scaler.fit(iris)

    iris_transformed = scaler.transform(iris)

    assert [[u'Iris-setosa', u'Iris-versicolor', u'Iris-virginica']] == iris_transformed["C5"].levels()
    assert max(iris_transformed[["C1", "C2", "C3", "C4"]].mean().as_data_frame().transpose()[0].tolist()) < 1e-10


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_scaler)
else:
    test_scaler()
