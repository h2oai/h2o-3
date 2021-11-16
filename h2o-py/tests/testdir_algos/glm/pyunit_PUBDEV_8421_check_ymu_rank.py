import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

# check parameters ymu and rank were passed 
def test_gaussian_alpha():
    h2o_data = h2o.import_file(
        path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname]
    myY = "C21"
    myX = h2o_data.names.remove(myY)
    data_frames = h2o_data.split_frame(ratios=[0.8])
    training_data = data_frames[0]
    test_data = data_frames[1]

    # test with lambda search on, generate_scoring_history on and off
    model = glm(family="gaussian", lambda_search=True, alpha=[0,0.2,0.5,0.8,1], generate_scoring_history=True)
    model.train(x=myX, y=myY, training_frame = training_data, validation_frame = test_data)
    
    assert len(model._model_json["output"]["ymu"]) > 0
    assert model._model_json["output"]["rank"] > 0

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gaussian_alpha)
else:
    test_gaussian_alpha()
