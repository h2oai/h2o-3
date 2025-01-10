import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils, assert_equals
from h2o.estimators.knn import H2OKnnEstimator
import numpy as np
from sklearn.neighbors import KNeighborsClassifier
from sklearn.neighbors import kneighbors_graph
import pandas as pd


def knn_sklearn_compare():
    seed = 12345
    id_column = "id"
    response_column = "class"
    x_names = ["sepal_len", "sepal_wid", "petal_len", "petal_wid"]

    train = pd.read_csv(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))

    knn = KNeighborsClassifier(n_neighbors=3)
    knn.fit(train[x_names], train[response_column])
    print(knn)
    knn_score = knn.score(train[x_names], train[response_column])
    print(knn_score)

    knn_graph = kneighbors_graph(train[x_names], 3, mode='connectivity', include_self=False, metric="euclidean")
    print(knn_graph)

    train_h2o = h2o.H2OFrame(train)
    train_h2o[response_column] = train_h2o[response_column].asfactor()
    train_h2o[id_column] = h2o.H2OFrame(np.arange(0, train_h2o.shape[0]))

    h2o_knn = H2OKnnEstimator(
        k=3,
        id_column=id_column,
        distance="euclidean",
        seed=seed,
        auc_type="macroovr"
    )
    
    h2o_knn.train(y=response_column, x=x_names, training_frame=train_h2o)
    distances_key = h2o_knn._model_json["output"]["distances"]
    print(distances_key)
    distances_frame = h2o.get_frame(distances_key)
    print(distances_frame)
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(knn_sklearn_compare)
else:
    knn_sklearn_compare()
