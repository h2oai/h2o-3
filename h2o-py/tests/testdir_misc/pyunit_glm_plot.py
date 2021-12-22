import sys
sys.path.insert(1,"../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def glm_plot_test():
    prostate = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    prostate["DPROS"] = prostate["DPROS"].asfactor()

    glm_bin = H2OGeneralizedLinearEstimator(family="binomial")
    glm_bin.train(ignored_columns=["ID"], y="CAPSULE", training_frame=prostate)
    glm_bin.plot(server=True)

    glm_mult = H2OGeneralizedLinearEstimator(family="multinomial")
    glm_mult.train(ignored_columns=["ID"], y="DPROS", training_frame=prostate)
    glm_mult.plot(server=True)

    glm_reg = H2OGeneralizedLinearEstimator(family="gaussian", score_each_iteration=True, generate_scoring_history=True)
    glm_reg.train(ignored_columns=["ID"], y="CAPSULE", training_frame=prostate)
    glm_reg.plot(server=True)

    glm_ord = H2OGeneralizedLinearEstimator(family="ordinal")
    glm_ord.train(ignored_columns=["ID"], y="DPROS", training_frame=prostate)
    glm_ord.plot(server=True)

if __name__ == "__main__":
    pyunit_utils.standalone_test(glm_plot_test)
else:
    glm_plot_test()
