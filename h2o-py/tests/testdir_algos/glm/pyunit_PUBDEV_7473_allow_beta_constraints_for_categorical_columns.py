from __future__ import print_function

import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils

def test_prostate():
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/logreg/prostate.csv"))
    h2o_data["AGE"] = h2o_data["AGE"].asfactor()

    bc = []

    name = "AGE"
    lower_bound = 0.1
    upper_bound = 0.5
    bc.append([name, lower_bound, upper_bound])

    name = "RACE"
    lower_bound = -0.5
    upper_bound = 0.5
    bc.append([name, lower_bound, upper_bound])

    name = "DCAPS"
    lower_bound = -0.4
    upper_bound = 0.4
    bc.append([name, lower_bound, upper_bound])

    name = "DPROS"
    lower_bound = -0.3
    upper_bound = 0.3
    bc.append([name, lower_bound, upper_bound])

    name = "PSA"
    lower_bound = -0.2
    upper_bound = 0.5
    bc.append([name, lower_bound, upper_bound])

    name = "VOL"
    lower_bound = -0.5
    upper_bound = 0.5
    bc.append([name, lower_bound, upper_bound])

    name = "GLEASON"
    lower_bound = -0.5
    upper_bound = 0.5
    bc.append([name, lower_bound, upper_bound])

    beta_constraints = h2o.H2OFrame(bc)
    beta_constraints.set_names(["names", "lower_bounds", "upper_bounds"])

    h2o_glm = H2OGeneralizedLinearEstimator(family="binomial", nfolds=10, alpha=0.5, beta_constraints=beta_constraints)
    h2o_glm.train(x=list(range(2, h2o_data.ncol)), y=1, training_frame=h2o_data )

    for i in range(len(h2o_glm._model_json['output']['coefficients_table'][0])):
        for constraint in beta_constraints.as_data_frame().get_values():
            if (h2o_glm._model_json['output']['coefficients_table'][0][i].startswith(constraint[0])):
                assert h2o_glm._model_json['output']['coefficients_table'][1][i] >= constraint[1]
                assert h2o_glm._model_json['output']['coefficients_table'][1][i] <= constraint[2]



if __name__ == "__main__":
    pyunit_utils.standalone_test(test_prostate)
else:
    test_prostate()
