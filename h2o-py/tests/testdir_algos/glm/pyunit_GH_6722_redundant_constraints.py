import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm

def test_redundant_constraints():
    d = h2o.import_file(path=pyunit_utils.locate("smalldata/glm_test/gaussian_20cols_10000Rows.csv"))  
    enum_cols = [0,1,2,3,4,5,6,7,8,9]
    for colInd in enum_cols:
        d[colInd] = d[colInd].asfactor()

    tight_constraints = [] # this constraint is satisfied by default coefficient initialization
    # add light tight constraints
    name = "C1.1"
    values = 0.5
    types = "LessThanEqual"
    contraint_numbers = 0
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C1.3"
    values = 1.0
    types = "LessThanEqual"
    contraint_numbers = 0
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -3
    types = "LessThanEqual"
    contraint_numbers = 0
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C2.3"
    values = 3
    types = "LessThanEqual"
    contraint_numbers = 1
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C11"
    values = -4
    types = "LessThanEqual"
    contraint_numbers = 1
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C12"
    values = 0.5
    types = "LessThanEqual"
    contraint_numbers = 1
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C13"
    values = 0.1
    types = "Equal"
    contraint_numbers = 2
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C14"
    values = -0.2
    types = "Equal"
    contraint_numbers = 2
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C15"
    values = 2
    types = "LessThanEqual"
    contraint_numbers = 3
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C16"
    values = -0.1
    types = "LessThanEqual"
    contraint_numbers = 3
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C17"
    values = -0.4
    types = "LessThanEqual"
    contraint_numbers = 3
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = 0.8
    types = "LessThanEqual"
    contraint_numbers = 3
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C1.4"
    values = 0.1
    types = "Equal"
    contraint_numbers = 4
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C2.1"
    values = 0.7
    types = "Equal"
    contraint_numbers = 4
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -1.1
    types = "Equal"
    contraint_numbers = 4
    tight_constraints.append([name, values, types, contraint_numbers])


    name = "C2.1"
    values = 0.1
    types = "Equal"
    contraint_numbers = 2
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C14"
    values = 2
    types = "Equal"
    contraint_numbers = 5
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C18"
    values = 0.5
    types = "Equal"
    contraint_numbers = 5
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C2.2"
    values = -0.3
    types = "Equal"
    contraint_numbers = 5
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C11"
    values = 0.5
    types = "Equal"
    contraint_numbers = 6
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C13"
    values = -1.5
    types = "Equal"
    contraint_numbers = 6
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -0.3
    types = "Equal"
    contraint_numbers = 6
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C15"
    values = 4
    types = "LessThanEqual"
    contraint_numbers = 7
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C16"
    values = -0.2
    types = "LessThanEqual"
    contraint_numbers = 7
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C17"
    values = -0.8
    types = "LessThanEqual"
    contraint_numbers = 7
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -0.8
    types = "LessThanEqual"
    contraint_numbers = 7
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C11"
    values = 1.5
    types = "Equal"
    contraint_numbers = 8
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "C13"
    values = -4.5
    types = "Equal"
    contraint_numbers = 8
    tight_constraints.append([name, values, types, contraint_numbers])

    name = "constant"
    values = -0.9
    types = "Equal"
    contraint_numbers = 8
    tight_constraints.append([name, values, types, contraint_numbers])
    
    linear_constraints = h2o.H2OFrame(tight_constraints)
    linear_constraints.set_names(["names", "values", "types", "constraint_numbers"])

    try:
        m = glm(family='gaussian', max_iterations=1, linear_constraints=linear_constraints, solver="irlsm", 
                lambda_=0.0)
        m.train(training_frame=d,y= "C21")
        assert False, "Should have thrown exception of redundant constraints"
    except Exception as ex:
        print(ex)
        temp = str(ex)
        assert ("redundant and possibly conflicting linear constraints" in temp), "Wrong exception was received."
        print("redundant constraint test passed!")

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_redundant_constraints)
else:
    test_redundant_constraints()
