from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator

# simple test to set beta constraints tests with various number of predictors.  Make sure the coefficient bounds are
# within the beta constraints bounds.
def test_beta_constraints_gaussian():
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C3"] = h2o_data["C3"].asfactor()
    h2o_data["C4"] = h2o_data["C4"].asfactor()
    h2o_data["C5"] = h2o_data["C5"].asfactor()
    h2o_data["C6"] = h2o_data["C6"].asfactor()
    h2o_data["C7"] = h2o_data["C7"].asfactor()
    h2o_data["C8"] = h2o_data["C8"].asfactor()
    h2o_data["C9"] = h2o_data["C9"].asfactor()
    h2o_data["C10"] = h2o_data["C10"].asfactor()
    y = "C21"
    x = h2o_data.names
    x.remove(y)
    nfolds = 4
    seed = 12345

    printText = "running model with beta constrains on C1, C2, C3, C11, C12, C13"
    constraints = h2o.H2OFrame({'names': ["C1.1", "C2.0", "C3.0", "C11", "C12", "C13"],
                                'lower_bounds': [0.3696965402743819 * 0.1, 3.8273867830358252 * 0.1,
                                                 2.2831399185698835 * 0.1,
                                                 10.20086488314071 * 0.1, 2.9111423805443195 * 0.1,
                                                 20.130364463336967 * 0.1],
                                'upper_bounds': [0.3696965402743819 * 0.8, 3.8273867830358252 * 0.8,
                                                 2.2831399185698835 * 0.8,
                                                 10.20086488314071 * 0.8, 2.9111423805443195 * 0.8,
                                                 20.130364463336967 * 0.8]})
    constraints = constraints[["names", "lower_bounds", "upper_bounds"]]
    run_print_model_performance('gaussian', h2o_data, nfolds, constraints, x, y, printText, seed, 'coordinate_descent')

def run_print_model_performance(family, train, nfolds, bc_constraints, x, y, printText, seed, solver):
    print("With lambda search, solver = {0}".format(solver))
    h2o_model2 = H2OGeneralizedLinearEstimator(family=family, nfolds=nfolds, beta_constraints=bc_constraints,
                                               seed=seed,
                                               lambda_search=True, solver=solver)
    h2o_model2.train(x=x, y=y, training_frame=train)
    # make sure coefficients are within bounds
    coeff2 = h2o_model2.coef()
    colNames = bc_constraints["names"]
    lowerB = bc_constraints["lower_bounds"]
    upperB = bc_constraints["upper_bounds"]
    for count in range(0, len(colNames)):
        # fix issue due to rounding difference between bounds and coefficients.
        coef_inactive2 = coeff2[colNames[count,0]]==0
        assert (round(coeff2[colNames[count,0]],6) >= round(lowerB[count,0],6) and round(coeff2[colNames[count,0]],6) 
                <= round(upperB[count,0], 6)) or coef_inactive2, \
            "With lambda search: coef for {0}: {1}, lower bound: {2}, upper bound: " \
            "{3}".format(colNames[count,0], coeff2[colNames[count,0]], lowerB[count,0], upperB[count,0])

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_beta_constraints_gaussian)
else:
    test_beta_constraints_gaussian()
