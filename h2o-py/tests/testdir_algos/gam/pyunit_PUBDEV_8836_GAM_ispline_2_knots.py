import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# this test makes sure when N+1 = 2, the correct number of basis functions are generated for I-spliner
def test_GAM_ISpline_2_knots():
    train1 = prepareData("smalldata/glm_test/gaussian_20cols_10000Rows.csv")
    gamX = ["C11", "C12", "C13"]
    x = train1.names
    x.remove("C21")
    predictors = [ele for ele in x if not(ele in gamX)]

    numKnots = [2,2,2]
    scale= [0.001, 0.001, 0.001]
    bs_type = [2,2,2]
    spline_order = [2,3,4]

    # building multiple models with same training / test datasets to make sure it works
    h2o_model = H2OGeneralizedAdditiveEstimator(family="gaussian", gam_columns=gamX, scale=scale, bs=bs_type, seed=12345,
                                            keep_gam_cols=True, spline_orders=spline_order, num_knots=numKnots)
    h2o_model.train(x=[], y="C21", training_frame=train1)
    checkCorrectGAMCols(h2o_model, spline_order, numKnots, gamX)
    
def checkCorrectGAMCols(h2o_model, spline_order, numKnots, gamCols):
    gam_frame = h2o.get_frame(h2o_model._model_json["output"]["gam_transformed_center_key"])
    gam_col_names = gam_frame.names
    for ind in range(len(numKnots)):
        knot = numKnots[ind]
        order = spline_order[ind]
        col = gamCols[ind]
        true_num_gam_col = knot+order-2
        num_gam_col = 0
        for cname in gam_col_names:
            if col in cname:
                num_gam_col = num_gam_col + 1
        assert num_gam_col == true_num_gam_col, "Expected number of gam cols for {0}: {1}, actual: {2}.  They should " \
                                                "equal".format(col, true_num_gam_col, num_gam_col)
            
def prepareData(pathToFile):
    train_data = h2o.import_file(pyunit_utils.locate(pathToFile))
    train_data["C1"] = train_data["C1"].asfactor()
    train_data["C2"] = train_data["C2"].asfactor()
    train_data["C3"] = train_data["C3"].asfactor()
    train_data["C4"] = train_data["C4"].asfactor()
    train_data["C5"] = train_data["C5"].asfactor()
    return train_data

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_GAM_ISpline_2_knots)
else:
    test_GAM_ISpline_2_knots()
