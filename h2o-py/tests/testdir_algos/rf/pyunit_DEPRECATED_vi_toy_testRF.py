import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def vi_toy_test():
    
    

    toy_data = h2o.import_file(path=pyunit_utils.locate("smalldata/gbm_test/toy_data_RF.csv"))
    #toy_data.summary()

    toy_data[6] = toy_data[6].asfactor()
    toy_data.show()
    rf = h2o.random_forest(x=toy_data[[0,1,2,3,4,5]], y=toy_data[6], ntrees=500, max_depth=20, nbins=100, seed=0)

    ranking = [rf._model_json['output']['variable_importances'].cell_values[v][0] for v in range(toy_data.ncol-1)]
    print(ranking)
    assert tuple(ranking) == tuple(["V3","V2","V6","V5","V1","V4"]), "expected specific variable importance ranking"



if __name__ == "__main__":
    pyunit_utils.standalone_test(vi_toy_test)
else:
    vi_toy_test()
