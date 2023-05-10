import sys
sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.model_selection import H2OModelSelectionEstimator

def test_maxrsweep_replacement():
    correct_pred_dict = {7: "C78,C97,C75,C76,C88,C89,C101,Intercept",
                         8: "C86,C78,C97,C75,C76,C88,C7,C89,Intercept",
                         10: "C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,Intercept",
                         11: "C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,Intercept",
                         12: "C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,Intercept",  # error here
                         13: "C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,C67,Intercept",
                         15: "C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,C67,C170,C123,Intercept",
                         27: "C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,C67,C170,C123,C137,C138,C189,C180,C31,"
                             "C193,C178,C181,C173,C135,C194,C50,Intercept",
                         28: "C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,C67,C170,C123,C137,C138,C189,C180,C31,"
                             "C193,C178,C181,C173,C135,C194,C50,C79,Intercept",
                         29: "C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,C67,C170,C123,C137,C138,C189,C180,C31,"
                             "C193,C178,C181,C173,C135,C194,C50,C79,C35,Intercept",
                         30: "C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,C67,C170,C123,C137,"
                             "C138,C189,C180,C31,C193,C178,C181,C173,C135,C194,C50,C35,C63,C73,Intercept",
                         48: "C154,C113,C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,C67,C170,"
                             "C123,C137,C138,C189,C180,C31,C193,C178,C181,C173,C50,C79,C35,C63,C25,"
                             "C135,C194,C73,C164,C114,C54,C131,C49,C147,C32,C160,C158,C199,C5,C127,"
                             "C167,C157,Intercept",
                         57: "C157,C172,C154,C113,C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,"
                             "C67,C170,C123,C137,C138,C189,C180,C31,C193,C178,C181,C173,C50,C79,C35,"
                             "C63,C25,C135,C194,C73,C164,C114,C54,C131,C49,C147,C32,C160,C158,C199,C5,"
                             "C127,C46,C185,C167,C139,C80,C28,C84,C15,C176,Intercept",
                         58: "C157,C172,C154,C113,C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,"
                             "C67,C170,C123,C137,C138,C189,C180,C31,C193,C178,C181,C173,C50,C79,C35,"
                             "C63,C25,C135,C194,C73,C164,C114,C54,C131,C49,C147,C32,C160,C158,C199,C5,"
                             "C127,C46,C185,C167,C139,C80,C28,C84,C15,C176,C42,Intercept",
                         61: "C165,C157,C172,C154,C113,C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,C67,C170,"
                             "C123,C137,C138,C189,C180,C31,C193,C178,C181,C173,C50,C79,C35,C63,C25,C135,C194,C73,C164,"
                             "C114,C54,C131,C49,C147,C32,C160,C158,C199,C5,C127,C46,C185,C167,C139,C80,C109,C84,C28,"
                             "C45,C176,C42,C11,Intercept",
                         63: "C81,C165,C157,C172,C154,C113,C86,C78,C97,C75,C76,C88,C7,C89,C101,C4,C175,C128,C67,C170,"
                             "C123,C137,C138,C189,C180,C31,C193,C178,C181,C173,C50,C79,C35,C63,C25,C135,C194,C73,C164,"
                             "C114,C54,C131,C49,C147,C32,C160,C158,C199,C5,C127,C46,C185,C167,C139,C80,C109,C84,C28,"
                             "C45,C176,C9,C15,C42,Intercept"}
    #train = h2o.import_file("https://s3.amazonaws.com/h2o-public-test-data/bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv")
    train = h2o.import_file(pyunit_utils.locate("bigdata/laptop/model_selection/maxrglm200Cols50KRows.csv"))
    response="response"
    predictors = train.names
    predictors.remove(response)
    npred = 63
    maxrsweep_model = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True, 
                                                 multinode_mode=False, standardize=True)
    maxrsweep_model.train(x=predictors, y=response, training_frame=train)

    maxrsweep_model_MM = H2OModelSelectionEstimator(mode="maxrsweep", max_predictor_number=npred, intercept=True,
                                                    standardize=True, multinode_mode=True)
    maxrsweep_model_MM.train(x=predictors, y=response, training_frame=train)

    predSubsets = correct_pred_dict.keys()
    for oneKey in predSubsets:
        correctSubset = correct_pred_dict[oneKey]
        correct_pred_subset = correctSubset.split(',')
        correct_pred_subset.sort()
        maxrsweep_one_coef = list(maxrsweep_model.coef(oneKey).keys())
        maxrsweep_one_coef.sort()
        maxrsweep_one_coef_MM = list(maxrsweep_model_MM.coef(oneKey).keys())
        maxrsweep_one_coef_MM.sort()
        print("subset size: {0}".format(oneKey))
        assert correct_pred_subset==maxrsweep_one_coef, \
            "Expected coeffs: {0}, Actual from maxrsweep: {1}.  They are different.".format(correct_pred_subset, 
                                                                                            maxrsweep_one_coef)
        assert correct_pred_subset==maxrsweep_one_coef_MM, \
            "Expected coeffs: {0}, Actual from maxrsweep with multinode_mode: {1}.  They are different." \
            "".format(correct_pred_subset, maxrsweep_one_coef_MM)

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_maxrsweep_replacement)
else:
    test_maxrsweep_replacement()
