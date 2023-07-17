import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils

# The purpose of this test to make sure that constrainted GLM works with collinear column removal.  In this case,
# the collinear columns are added to the back of the frame.
def test_constraints_collinear_columns():
    # first two columns are enums, the last 4 are real columns
    h2o_data = pyunit_utils.genTrainFrame(10000, 6, enumCols=2, enumFactors=2, responseLevel=2, miscfrac=0, randseed=12345)
    # create extra collinear columns
    num1 = h2o_data[2]*0.2-0.5*h2o_data[3]
    num2 = -0.8*h2o_data[4]+0.1*h2o_data[5]
    h2o_collinear = num1.cbind(num2)
    h2o_collinear.set_names(["corr1", "corr2"])
    train_data = h2o_data.cbind(h2o_collinear)
    
    y = "response"
    x = train_data.names
    x.remove(y)
    lc2 = []

    name = "C10"
    values = 1
    types = "LessThanEqual"
    contraint_numbers = 0
    lc2.append([name, values, types, contraint_numbers])

    name = "corr2"
    values = 1
    types = "LessThanEqual"
    contraint_numbers = 0
    lc2.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -2
    types = "LessThanEqual"
    contraint_numbers = 0
    lc2.append([name, values, types, contraint_numbers])

    linear_constraints2 = h2o.H2OFrame(lc2)
    linear_constraints2.set_names(["names", "values", "types", "constraint_numbers"])    

    h2o_glm = H2OGeneralizedLinearEstimator(family="binomial", compute_p_values=True, remove_collinear_columns=True,
                                            lambda_=0.0, solver="irlsm", linear_constraints=linear_constraints2,
                                            seed = 1234)
    h2o_glm.train(x=x, y=y, training_frame=train_data )
    # there should be two coefficients with zero
    coefs = h2o_glm.coef().values()
    numZero = [x for x in coefs if x == 0]
    assert len(numZero) == 2, "Length of non-zero coefficients should be 2 but is not."


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_constraints_collinear_columns)
else:
    test_constraints_collinear_columns()
