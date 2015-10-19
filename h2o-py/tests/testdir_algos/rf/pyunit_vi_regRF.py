

import h2o, tests

def vi_reg():
    
    

    data = h2o.import_file(path=tests.locate("smalldata/gbm_test/BostonHousing.csv"))
    #data.summary()

    rf = h2o.random_forest(x=data[0:13], y=data[13], ntrees=100, max_depth=20, nbins=100, seed=0)

    ranking = [rf._model_json['output']['variable_importances'].cell_values[v][0] for v in range(data.ncol-1)]
    print(ranking)
    assert tuple([ranking[0],ranking[1]]) == tuple(["rm","lstat"]), "expected specific variable importance ranking"


pyunit_test = vi_reg
