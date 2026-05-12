import sys
sys.path.insert(1,"../../../")
import h2o
import os
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm


# checking pr_plot when we have cross-validation enabled.
def glm_pr_plot_test():
    print("Testing glm cross-validation with alpha array, default lambda values for binomial models.")
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname]
    myY = "C21"
    h2o_data["C21"] = h2o_data["C21"].asfactor()
    myX = h2o_data.names.remove(myY)
    data_frames = h2o_data.split_frame(ratios=[0.8])
    training_data = data_frames[0]
    test_data = data_frames[1]
    
    # build model with CV but no validation dataset
    cv_model = glm(family='binomial',alpha=[0.1,0.5,0.9], nfolds = 3, fold_assignment="modulo", seed=12345)
    cv_model.train(training_frame=training_data,x=myX,y=myY, validation_frame=test_data)
    fn = "pr_plot_train_valid_cx.png"
    perf = cv_model.model_performance(xval=True)
    perf.plot(type="pr", server=True, save_plot_path=fn)
    if os.path.isfile(fn):
        os.remove(fn)

    (recall, precision) = perf.plot(type="pr", server=True, plot=False)
    assert len(precision) == len(recall), "Expected precision and recall to have the same shape but they are not."


if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_pr_plot_test)
else:
    glm_pr_plot_test()
