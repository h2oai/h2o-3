import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators.knn import H2OKnnEstimator
import numpy as np


def knn_categorical_data():
    seed = 12345
    id_column = "id"
    response_column = "IsDepDelayed"
    x_names = ["Origin", "Distance", "Dest"]

    train = h2o.upload_file(pyunit_utils.locate("smalldata/testng/airlines_train.csv"))
    train[response_column] = train[response_column].asfactor()
    train[id_column] = h2o.H2OFrame(np.arange(0, train.shape[0]))
    print(train.shape)
    train = train[0:1000, :]

    # test AUTO categorical encoding 
    model = H2OKnnEstimator(
        k=3,
        id_column=id_column,
        distance="euclidean",
        seed=seed,
        auc_type="macroovr"
    )
    
    model.train(y=response_column, x=x_names, training_frame=train)
    assert model is not None
    
    preds = model.predict(train)
    assert preds is not None

    # test enum categorical encoding 
    model = H2OKnnEstimator(
        k=3,
        id_column=id_column,
        distance="euclidean",
        seed=seed,
        auc_type="macroovr",
        categorical_encoding="enum"
    )

    model.train(y=response_column, x=x_names, training_frame=train)

    # test different categorical encoding than enum
    try:
        model = H2OKnnEstimator(
            k=3,
            id_column=id_column,
            distance="euclidean",
            seed=seed,
            auc_type="macroovr",
            categorical_encoding="one_hot_explicit")

        model.train(y=response_column, x=x_names, training_frame=train)
    except Exception as ex:
        exception = str(ex)
        assert ("H2OModelBuilderIllegalArgumentException" in exception)
        assert ("_categorical_encoding: Only enum categorical encoding is supported." in exception)
        

if __name__ == "__main__":
    pyunit_utils.standalone_test(knn_categorical_data)
else:
    knn_categorical_data()
