from __future__ import print_function
from builtins import range
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils

from h2o.estimators.kmeans import H2OKMeansEstimator


def test_constrained_kmeans_iris():

    iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    
    k = 3

    start_points = h2o.H2OFrame(
        [[4.9, 3.0, 1.4, 0.2],
         [5.6, 2.5, 3.9, 1.1],
         [6.5, 3.0, 5.2, 2.0]]
    )
    
    constraints = [
        [100, 40, 1],
        [1, 100, 1],
        [1, 40, 100],
        [1, 1, 1],
        [148, 1, 1],
        [1, 148, 1],
        [1, 1, 148],
        [50, 50, 50]
    ]

    for i in list(range(len(constraints))):
        for j in [True, False]:
            print("===== Train KMeans model with Iris dataset and constraints: ", constraints[i], " standardize "+str(j)+" ======")
            kmm = H2OKMeansEstimator(k=k, user_points=start_points, standardize=j, cluster_size_constraints=constraints[i], score_each_iteration=True)
            kmm.train(x=list(range(4)), training_frame=iris_h2o)
        
            kmm.show()
    
            for j in list(range(k)):
                number_points = kmm._model_json['output']['training_metrics']._metric_json['centroid_stats']._cell_values[j][2]
                assert number_points >= constraints[i][j], "Number of points ("+str(number_points)+") in cluster "+str(i+1)+" should be >= constraint value ("+str(constraints[i][j])+")"


def test_constrained_kmeans_chicago():
    
    iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/chicago/chicagoAllWeather.csv"))

    k = 3

    start_points = h2o.H2OFrame(
        [[0.9223065747871615,1.016292569726567,1.737905586557139,-0.2732881352142627,0.8408705963844509,-0.2664469441473223,-0.2881728818872508],
         [-1.4846149848792978,-1.5780763628717547,-1.330641758390853,-1.3664503532612082,-1.0180638458160431,-1.1194221247071547,-1.2345088149586547],
         [1.4953511836400069,-1.001549933405461,-1.4442916600555933,1.5766442462663375,-1.855936520243046,-2.07274732650932,-2.2859931850379924]]
    )

    constraints = [
        [1000, 3000, 1000],
        [1000, 1000, 1000],
        [100, 2000, 10],
        [10, 1000, 2000]
    ]

    for i in range(len(constraints)):
        for j in [True, False]:
            print("===== Train KMeans model  with Chicago dataset and constraints: ", constraints[i], " standardize "+str(j)+" ======")
            kmm = H2OKMeansEstimator(k=k, user_points=start_points, standardize=j, cluster_size_constraints=constraints[i], score_each_iteration=True)
            kmm.train(x=list(range(7)), training_frame=iris_h2o)

            kmm.show()

            for j in list(range(k)):
                number_points = kmm._model_json['output']['training_metrics']._metric_json['centroid_stats']._cell_values[j][2]
                assert number_points >= constraints[i][j], "Number of points ("+str(number_points)+") in cluster "+str(i+1)+" should be >= constraint value ("+str(constraints[i][j])+")"


def test_constrained_kmeans_iris_cv():

    iris_h2o = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))
    k = 3

    start_points = h2o.H2OFrame(
        [[4.9, 3.0, 1.4, 0.2],
         [5.6, 2.5, 3.9, 1.1],
         [6.5, 3.0, 5.2, 2.0]]
    )

    constraints = [
        [100, 40, 1],
        [1, 100, 1],
        [1, 40, 100],
        [1, 1, 1],
        [148, 1, 1],
        [1, 148, 1],
        [1, 1, 148],
        [50, 50, 50]
    ]

    for i in list(range(len(constraints))):
        for j in [True, False]:
            print("===== Train KMeans model with Iris dataset and constraints: ", constraints[i], " standardize "+str(j)+" ======")
            kmm = H2OKMeansEstimator(k=k, user_points=start_points, standardize=j, cluster_size_constraints=constraints[i],
                                     nfold=3, score_each_iteration=True)
            kmm.train(x=list(range(4)), training_frame=iris_h2o)

            kmm.show()

            for j in list(range(k)):
                number_points = kmm._model_json['output']['training_metrics']._metric_json['centroid_stats']._cell_values[j][2]
                assert number_points >= constraints[i][j], "Number of points ("+str(number_points)+") in cluster "+str(i+1)+" should be >= constraint value ("+str(constraints[i][j])+")"
                


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_constrained_kmeans_iris)
    pyunit_utils.standalone_test(test_constrained_kmeans_chicago)
    #pyunit_utils.standalone_test(test_constrained_kmeans_iris_cv)
else:
    test_constrained_kmeans_iris()
    test_constrained_kmeans_chicago()
    #test_constrained_kmeans_iris_cv()
