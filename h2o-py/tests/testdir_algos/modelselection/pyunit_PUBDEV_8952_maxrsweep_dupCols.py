import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

#  This test is used to make sure duplicated columns are removed and if max_predictor_number exceeds the final
# predictor count, an error message should have been generated.
def test_maxrsweep_replacement():
    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv"))
    train2 = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv"))
    train2 = train2.drop(200, axis = 1)
    train = train.cbind(train2)
    response = "response"
    predictors = train.names
    predictors.remove(response)

    try:
        maxrsweep3_model = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=300, intercept=True,
                                                      build_glm_model=False)
        maxrsweep3_model.train(x=predictors, y=response, training_frame=train)
        assert False, "Should have throw exception of bad max_predictor_number!"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert "max_predictor_number: Your dataset contains duplicated predictors.  After removal, reduce your" \
               " max_predictor_number to" in temp
        print("coefficient test passed!")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrsweep_replacement)
else:
    test_maxrsweep_replacement()
