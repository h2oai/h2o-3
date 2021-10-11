from builtins import range
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def test_beta_constraints_binomial():
    # binomial
    h2o_data = h2o.import_file(pyunit_utils.locate("smalldata/glm_test/binomial_20_cols_10KRows.csv"))
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
    h2o_data["C21"] = h2o_data["C21"].asfactor()

    run_print_model_performance('binomial', h2o_data, nfolds, None, x, y, "No beta constraints", seed, 
                                "coordinate_descent")
    run_print_model_performance('binomial', h2o_data, nfolds, None, x, y, "No beat constraints", seed, "irlsm")
    printText = "running model with beta constrains on C1, C11"
    dictBounds = {'names': ["C1.0", "C11"], 'lower_bounds': [1.3498144060671078*0.1, 1.959130413902314*0.1],
                  'upper_bounds': [1.3498144060671078*0.8, 1.959130413902314*0.8]}
    constraints = h2o.H2OFrame(dictBounds)
    constraints = constraints[["names", "lower_bounds", "upper_bounds"]]
    run_print_model_performance('binomial', h2o_data, nfolds, constraints, x, y, printText, seed, "coordinate_descent")
    run_print_model_performance('binomial', h2o_data, nfolds, constraints, x, y, printText, seed, "irlsm")
    
    printText = "running model with beta constrains on C1, C2, C11, C12"
    dictBounds = {'names': ["C1.0", "C2.0", "C11", "C12"], 
                  'lower_bounds': [1.3498144060671078*0.1, 0.8892709168416222*0.1, 1.959130413902314*0.1, 0.13139198387980652*0.1],
                  'upper_bounds': [1.3498144060671078*0.8, 0.8892709168416222*0.8, 1.959130413902314*0.8, 0.13139198387980652*0.8]}
    constraints = h2o.H2OFrame(dictBounds)
    constraints = constraints[["names", "lower_bounds", "upper_bounds"]]
    run_print_model_performance('binomial', h2o_data, nfolds, constraints, x, y, printText, seed, 'coordinate_descent')
    run_print_model_performance('binomial', h2o_data, nfolds, constraints, x, y, printText, seed, 'irlsm')

    printText = "running model with beta constrains on C1, C2, C3, C11, C12, C13"
    dictBounds = {'names': ["C1.0", "C2.0", "C3.0", "C11", "C12", "C13"],
                  'lower_bounds': [1.3498144060671078*0.1, 0.8892709168416222*0.1, 2.5406690227893254*0.1,
                                   1.959130413902314*0.1, 0.13139198387980652*0.1, 1.80498551446445*0.1],
                  'upper_bounds': [1.3498144060671078*0.8, 0.8892709168416222*0.8, 2.5406690227893254*0.8,
                                   1.959130413902314*0.8, 0.13139198387980652*0.8, 1.80498551446445*0.8]}
    constraints = h2o.H2OFrame(dictBounds)
    constraints = constraints[["names", "lower_bounds", "upper_bounds"]]
    run_print_model_performance('binomial', h2o_data, nfolds, constraints, x, y, printText, seed, 'coordinate_descent')
    run_print_model_performance('binomial', h2o_data, nfolds, constraints, x, y, printText, seed, 'irlsm')

def run_print_model_performance(family, train, nfolds, bc_constraints, x, y, printText, seed, solver):
    print(printText)
    if bc_constraints is None:
        print("No beta constraints: Without lambda search and with solver {0}".format(solver))
        h2o_model = H2OGeneralizedLinearEstimator(family=family, nfolds=nfolds, beta_constraints=bc_constraints, seed=seed,
                                              solver = solver)
        h2o_model.train(x=x, y=y, training_frame=train)
        print(h2o_model.model_performance(xval=True))
        print("No beta constraints: With lambda search and with solver {0}".format(solver))
        h2o_model2 = H2OGeneralizedLinearEstimator(family=family, nfolds=nfolds, beta_constraints=bc_constraints, seed=seed,
                                               lambda_search=True, solver = solver)
        h2o_model2.train(x=x, y=y, training_frame=train)
        print(h2o_model.model_performance(xval=True))
    else:
        print("Without lambda search and with solver {0}".format(solver))
        h2o_model = H2OGeneralizedLinearEstimator(family=family, nfolds=nfolds, beta_constraints=bc_constraints, seed=seed,
                                              solver = solver)
        h2o_model.train(x=x, y=y, training_frame=train)
        print(h2o_model.model_performance(xval=True))
        print("With lambda search and with solver {0}".format(solver))
        h2o_model2 = H2OGeneralizedLinearEstimator(family=family, nfolds=nfolds, beta_constraints=bc_constraints, seed=seed,
                                              lambda_search=True, solver = solver)
        h2o_model2.train(x=x, y=y, training_frame=train)
        print(h2o_model.model_performance(xval=True))
        coeff = h2o_model.coef()
        coeff2 = h2o_model2.coef()
        colNames = bc_constraints["names"]
        lowerB = bc_constraints["lower_bounds"]
        upperB = bc_constraints["upper_bounds"]
        for count in range(0, len(colNames)):
            assert (coeff[colNames[count,0]] >= lowerB[count,0] and coeff[colNames[count,0]] <= upperB[count,0]) or \
                   coeff[colNames[count,0]]==0,\
                "coefficient exceed limits"
            assert (coeff2[colNames[count,0]] >= lowerB[count,0] and coeff2[colNames[count,0]] <= upperB[count,0]) or\
                   coeff2[colNames[count,0]]==0, \
                "coefficient exceed limits"
        #assert h2o_model.logloss() >= h2o_model2.logloss(), "Logloss without lambda_search {0} should exceed logloss with" \
        #                                                " lambda_saerch {1}".format(h2o_model.logloss(), h2o_model2.logloss())


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_beta_constraints_binomial)
else:
    test_beta_constraints_binomial()
