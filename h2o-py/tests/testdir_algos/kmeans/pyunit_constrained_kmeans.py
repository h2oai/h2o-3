from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

from h2o.estimators.kmeans import H2OKMeansEstimator


def test_constrained_kmeans():

    iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    
    k = 3

    start = h2o.H2OFrame(
        [[4.9, 3.0, 1.4, 0.2],
         [5.6, 2.5, 3.9, 1.1],
         [6.5, 3.0, 5.2, 2.0]]
    )
    
    constraints = [
        [100, 40, 0],
        [100, 0, 0],
        [0, 100, 0],
        [0, 40, 100],
        [0, 0, 0],
        [0, 0, 150],
        [150, 0, 0],
        [0, 150, 0],
        [1, 1, 1],
        [50, 50, 50]
    ]

    for i in list(range(len(constraints))):
        print("===== Train KMeans model with constraints: ======")
        print(constraints[i])
        kmm = H2OKMeansEstimator(k=k, user_points=start, standardize=False, cluster_size_constraints=constraints[i], 
                                 score_each_iteration=True)
        kmm.train(x=list(range(4)), training_frame=iris_h2o)
        
        kmm.show()
    
        for j in list(range(k)):
            number_points = kmm._model_json['output']['training_metrics']._metric_json['centroid_stats']._cell_values[j][2]
            assert number_points >= constraints[i][j], "Number of points ("+str(number_points)+") in cluster "+str(i+1)+" should be >= constraint value ("+str(constraints[i][j])+")" 


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_constrained_kmeans)
else:
    test_constrained_kmeans()
