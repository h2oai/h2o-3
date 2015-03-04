import sys, os
sys.path.insert(1, "../../../")
import h2o

def parametersKmeans(ip,port):
    # Connect to a pre-existing cluster
    h2o.init(ip,port)  # connect to localhost:54321

    #Log.info("Getting data...")
    iris = h2o.import_frame(path=h2o.locate("smalldata/iris/iris.csv"))

    #Log.info("Create and and duplicate...")
    iris_km = h2o.kmeans(x=iris[0:4], k=3, seed=1234)
    parameters = iris_km._model_json['parameters']
    param_dict = {}
    for p in range(len(parameters)):
        param_dict[parameters[p]['label']] = parameters[p]['actual_value']

    iris_km_again = h2o.kmeans(x=iris[0:4], **param_dict)

    #Log.info("wmse")
    wmse = iris_km.within_mse().sort()
    wmse_again = iris_km_again.within_mse().sort()
    assert wmse == wmse_again, "expected wmse to be equal"

    #Log.info("centers")
    centers = iris_km.centers()
    centers_again = iris_km_again.centers()
    assert centers == centers_again, "expected centers to be the same"

if __name__ == "__main__":
    h2o.run_test(sys.argv, parametersKmeans)