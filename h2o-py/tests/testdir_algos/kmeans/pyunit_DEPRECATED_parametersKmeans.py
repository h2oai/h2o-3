import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def parametersKmeans():

    print "Getting data..."
    iris = h2o.import_file(path=pyunit_utils.locate("smalldata/iris/iris.csv"))

    print "Create and and duplicate..."
    iris_km = h2o.kmeans(x=iris[0:4], k=3, seed=1234)
    parameters = iris_km._model_json['parameters']
    param_dict = {}
    for p in range(len(parameters)):
        param_dict[parameters[p]['label']] = parameters[p]['actual_value']

    iris_km_again = h2o.kmeans(x=iris[0:4], **param_dict)

    print "wss"
    wss = iris_km.withinss().sort()
    wss_again = iris_km_again.withinss().sort()
    assert wss == wss_again, "expected wss to be equal"

    print "centers"
    centers = iris_km.centers()
    centers_again = iris_km_again.centers()
    assert centers == centers_again, "expected centers to be the same"



if __name__ == "__main__":
    pyunit_utils.standalone_test(parametersKmeans)
else:
    parametersKmeans()
