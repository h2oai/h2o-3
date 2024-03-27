import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
from tests import pyunit_utils
from tests.pyunit_utils import utils_for_glm_tests

def test_separate_linear_beta_gaussian():
    '''
    This test will check that when separate_linear_beta=True, those coefficients should be within the beta constraint
    range.
    '''
    h2o_data = h2o.import_file(
        path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname].asfactor()
    myY = "C21"
    myX = h2o_data.names.remove(myY)

    linear_constraints = [] # this constraint is satisfied by default coefficient initialization
    name = "C1.2"
    values = 1
    types = "Equal"
    contraint_numbers = 0
    linear_constraints.append([name, values, types, contraint_numbers])

    name = "C11"
    values = 1
    types = "Equal"
    contraint_numbers = 0
    linear_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = 13.56 
    types = "Equal"
    contraint_numbers = 0
    linear_constraints.append([name, values, types, contraint_numbers])

    name = "C5.2"
    values = 1
    types = "LessThanEqual"
    contraint_numbers = 1
    linear_constraints.append([name, values, types, contraint_numbers])

    name = "C12"
    values = 1
    types = "LessThanEqual"
    contraint_numbers = 1
    linear_constraints.append([name, values, types, contraint_numbers])

    name = "C15"
    values = 1
    types = "LessThanEqual"
    contraint_numbers = 1
    linear_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -5
    types = "LessThanEqual"
    contraint_numbers = 1
    linear_constraints.append([name, values, types, contraint_numbers])

    linear_constraints2 = h2o.H2OFrame(linear_constraints)
    linear_constraints2.set_names(["names", "values", "types", "constraint_numbers"])

    bc = []
    name = "C1.1"
    c1p1LowerBound = -36
    c1p1UpperBound=-35
    bc.append([name, c1p1LowerBound, c1p1UpperBound])

    name = "C5.2"
    c5p2LowerBound=-14
    c5p2UpperBound=-13
    bc.append([name, c5p2LowerBound, c5p2UpperBound])

    name = "C11"
    c11LowerBound=25
    c11UpperBound=26
    bc.append([name, c11LowerBound, c11UpperBound])

    name = "C15"
    c15LowerBound=14
    c15UpperBound=15
    bc.append([name, c15LowerBound, c15UpperBound])

    beta_constraints = h2o.H2OFrame(bc)
    beta_constraints.set_names(["names", "lower_bounds", "upper_bounds"])
          
    m_sep = glm(family='gaussian', linear_constraints=linear_constraints2, solver="irlsm", lambda_=0.0,
                 beta_constraints=beta_constraints, separate_linear_beta=True, constraint_eta0=0.1, constraint_tau=10,
                 constraint_alpha=0.01, constraint_beta=0.9, constraint_c0=100)
    m_sep.train(training_frame=h2o_data,x=myX, y=myY)
    coef_sep = m_sep.coef()
    
    # check coefficients under beta constraints are within limits for when separate_linear_beta = True compared to the
    # case when separate_linear_beta = False
    # check C1.1
    assert coef_sep["C1.1"] >= c1p1LowerBound and coef_sep["C1.1"] <= c1p1UpperBound, \
        "Coefficient C1.1: {0} should be between -36, and -35 but is not!".format(coef_sep["C1.1"])
    # check C5.2
    assert coef_sep["C5.2"] >= c5p2LowerBound and coef_sep["C5.2"] <= c5p2UpperBound, \
        "Coefficient C5.2: {0} should be between -14, and -13 but is not!".format(coef_sep["C5.2"])
    # check C11
    assert coef_sep["C11"] >= c11LowerBound and coef_sep["C11"] <= c11UpperBound, \
        "Coefficient C11: {0} should be between 25, and 26 but is not!".format(coef_sep["C11"])
    # check C15
    assert coef_sep["C15"] >= c15LowerBound and coef_sep["C15"] <= c15UpperBound, \
        "Coefficient C15: {0} should be between 14, and 15 but is not!".format(coef_sep["C15"])    
    
if __name__ == "__main__":
    pyunit_utils.standalone_test(test_separate_linear_beta_gaussian)
else:
    test_separate_linear_beta_gaussian()
