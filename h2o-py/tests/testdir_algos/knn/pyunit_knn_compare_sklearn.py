import sys, os

sys.path.insert(1, os.path.join("..", "..", ".."))
import h2o
from tests import pyunit_utils
from h2o.estimators.knn import H2OKnnEstimator
import numpy as np
from sklearn.neighbors import kneighbors_graph
import pandas as pd


def knn_sklearn_compare():
    seed = 12345
    id_column = "id"
    response_column = "class"
    x_names = ["sepal_len", "sepal_wid", "petal_len", "petal_wid"]
    k = 3
    metrics = ["euclidean", "manhattan", "cosine"]

    train = pd.read_csv(pyunit_utils.locate("smalldata/iris/iris_wheader.csv"))
    
    train_h2o = h2o.H2OFrame(train)
    train_h2o[response_column] = train_h2o[response_column].asfactor()
    train_h2o[id_column] = h2o.H2OFrame(np.arange(0, train_h2o.shape[0]))
    
    for metric in metrics:
        print("Check results for "+metric+" metric.")
        sklearn_knn_graph = kneighbors_graph(train[x_names],
                                             k, 
                                             mode='connectivity', 
                                             include_self=True, 
                                             metric=metric)

        h2o_knn = H2OKnnEstimator(k=k,
                                  id_column=id_column,
                                  distance=metric,
                                  seed=seed)
    
        h2o_knn.train(y=response_column, x=x_names, training_frame=train_h2o)
        
        distances_frame = h2o_knn.distances().as_data_frame()
        assert distances_frame is not None
    
        diff = 0
        allowed_diff = 20
        for i in range(train.shape[0]):
            sklearn_neighbours = sklearn_knn_graph[i].nonzero()[1]
            for j in range(k):
                sklearn_n = sklearn_neighbours[j]
                h2o_n = distances_frame["id_"+str(j+1)][i]
                if sklearn_n != h2o_n:
                    print(distances_frame.loc[[i]])
                    print("["+str(i)+","+str(j)+"] sklearn:h2o "+str(sklearn_n)+" == "+str(h2o_n))
                    diff += 1
                
        # some neighbours should have different order due to parallelization
        print("Number of different neighbours: "+str(diff))      
        assert diff < allowed_diff
    

if __name__ == "__main__":
    pyunit_utils.standalone_test(knn_sklearn_compare)
else:
    knn_sklearn_compare()
