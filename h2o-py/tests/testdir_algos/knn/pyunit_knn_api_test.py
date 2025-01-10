import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators.knn import H2OKnnEstimator
import numpy as np


def knn_api_smoke():
    seed = 12345
    id_column = "id"
    response_column = "class"
    x_names = ["sepal_len", "sepal_wid", "petal_len", "petal_wid"]

    train_h2o = h2o.upload_file(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    train_h2o[response_column] = train_h2o[response_column].asfactor()
    train_h2o[id_column] = h2o.H2OFrame(np.arange(0, train_h2o.shape[0]))


    model = H2OKnnEstimator(
        k=3,
        id_column=id_column,
        distance="euclidean",
        seed=seed,
        auc_type="macroovr"
    )
    model.train(y=response_column, x=x_names, training_frame=train_h2o)
    perf = model.model_performance()

    print(perf)

    assert_equals(perf.auc(), model.auc())
    assert_equals(perf.logloss(), model.logloss())
    assert_equals(perf.mse(), model.mse())
    assert_equals(perf.multinomial_auc_table(), model.multinomial_auc_table())
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(knn_api_smoke)
else:
    knn_api_smoke()
