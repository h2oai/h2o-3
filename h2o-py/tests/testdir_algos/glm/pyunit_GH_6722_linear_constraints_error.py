import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from tests import pyunit_utils

# this test will try to specify a linear constraints of only one coefficients and this should throw an error.
def test_bad_linear_constraints():
    h2o_data = h2o.import_file(
        path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))
    enum_columns = ["C1", "C2", "C3", "C4", "C5", "C6", "C7", "C8", "C9", "C10"]
    for cname in enum_columns:
        h2o_data[cname] = h2o_data[cname].asfactor()
    myY = "C21"
    myX = h2o_data.names.remove(myY)

    dictLinearBounds = {'names': ["C11", "constant"], 'types':['Equal', 'Equal'], 'values': [0.5, -1.5], 
                        'constraint_numbers': [0, 0]}
    linearConstraints = h2o.H2OFrame(dictLinearBounds)
    linearConstraints = linearConstraints[["names", "types", "values", "constraint_numbers"]]
    try:
        model = H2OGeneralizedLinearEstimator(linear_constraints = linearConstraints, solver="irlsm")
        model.train(x=myX, y=myY, training_frame=h2o_data)
        print("Should have thrown an error....")
    except Exception as e:
        print(e.args[0])
        assert 'Linear constraint must have at least two coefficients' in e.args[0]
        

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_bad_linear_constraints)
else:
    test_bad_linear_constraints()
