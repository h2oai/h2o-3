import h2o
from h2o.estimators.glm import H2OGeneralizedLinearEstimator as glm
from tests import pyunit_utils
import numpy as np
import pandas as pd


def data_prep(seed, n=10000):
    rng = np.random.default_rng(seed)
    x1 = rng.normal(0, 10, n)
    x2 = rng.normal(10, 100, n)
    x3 = rng.normal(20, 200, n)
    x4 = rng.normal(30, 3000, n)
    x5 = rng.normal(400, 4000, n)

    y_raw = np.sin(x1) * 100 + np.sin(x2) * 100 + x3 / 20 + x3 / 30 + x5 / 400
    y = rng.normal(y_raw, 20)

    data = {'x1': x1, 'x2': x2, 'x3': x3, 'x4': x4, 'x5': x5, 'y': y}
    return h2o.H2OFrame(pd.DataFrame(data))


def test_equality_constraint_enforced():
    """
    GH-16864: verifies the constrained GLM solver actually enforces linear equality constraints
    (x2 + x3 = 0) instead of silently ignoring them.
    """
    train_data = data_prep(123)
    common_params = {
        'family': 'gaussian', 'link': 'identity',
        'lambda_': 0, 'seed': 1234, 'nfolds': 0,
        'compute_p_values': True, 'calc_like': True,
        'solver': 'irlsm', 'standardize': True,
    }

    # Equality constraint: x2 + x3 = 0
    linear_constraints = h2o.H2OFrame([
        ["x2", 1, "Equal", 0],
        ["x3", 1, "Equal", 0],
        ["constant", 0, "Equal", 0],
    ])
    linear_constraints.set_names(["names", "values", "types", "constraint_numbers"])

    model_constrained = glm(linear_constraints=linear_constraints, **common_params)
    model_constrained.train(x=['x1', 'x2', 'x3', 'x4', 'x5'], y='y', training_frame=train_data)
    coef_c = model_constrained.coef()

    model_unconstrained = glm(**common_params)
    model_unconstrained.train(x=['x1', 'x2', 'x3', 'x4', 'x5'], y='y', training_frame=train_data)
    coef_u = model_unconstrained.coef()

    x2_x3_constrained = coef_c['x2'] + coef_c['x3']
    x2_x3_unconstrained = coef_u['x2'] + coef_u['x3']

    print("Constrained   x2+x3 = {0:.6f}".format(x2_x3_constrained))
    print("Unconstrained x2+x3 = {0:.6f}".format(x2_x3_unconstrained))

    # The equality constraint x2+x3=0 must be satisfied within a tight tolerance.
    assert abs(x2_x3_constrained) < 0.1, \
        "Equality constraint x2+x3=0 not enforced: x2+x3 = {0:.6f} (expected < 0.1)".format(x2_x3_constrained)

    # The unconstrained model should not accidentally satisfy the constraint.
    assert abs(x2_x3_unconstrained) > abs(x2_x3_constrained), \
        "Constrained model (x2+x3={0:.4f}) should be closer to 0 than unconstrained model (x2+x3={1:.4f})".format(
            x2_x3_constrained, x2_x3_unconstrained)


def test_beta_and_linear_constraints_no_npe():
    """
    GH-16864: verifies combining beta_constraints with linear_constraints trains without error and
    respects both a lower-bound-only and an upper-bound-only beta constraint.
    """
    train_data = data_prep(42)
    predictors = ['x1', 'x2', 'x3', 'x4', 'x5']

    linear_constraints_raw = [
        ["x2", 1, "LessThanEqual", 0],
        ["x3", -1, "LessThanEqual", 0],
        ["constant", 0, "LessThanEqual", 0],
    ]
    linear_constraints = h2o.H2OFrame(linear_constraints_raw)
    linear_constraints.set_names(["names", "values", "types", "constraint_numbers"])

    common_params = {
        'family': 'gaussian', 'link': 'identity',
        'lambda_': 0.0, 'seed': 1234, 'nfolds': 0,
        'compute_p_values': True, 'calc_like': True,
        'solver': 'irlsm',
        'linear_constraints': linear_constraints,
    }

    # --- lower-bound-only beta constraint ---
    bc_lower = h2o.H2OFrame([['x1', 0.03]])
    bc_lower.set_names(["names", "lower_bounds"])

    model_lb = glm(beta_constraints=bc_lower, **common_params)
    model_lb.train(x=predictors, y='y', training_frame=train_data)
    coef_lb = model_lb.coef()
    print("Lower-bound model coefs:", coef_lb)

    assert coef_lb['x1'] >= 0.03 - 1e-6, \
        "Beta lower-bound constraint x1 >= 0.03 violated: x1 = {0}".format(coef_lb['x1'])

    # --- upper-bound-only beta constraint ---
    bc_upper = h2o.H2OFrame([['x1', 1.5]])
    bc_upper.set_names(["names", "upper_bounds"])

    model_ub = glm(beta_constraints=bc_upper, **common_params)
    model_ub.train(x=predictors, y='y', training_frame=train_data)
    coef_ub = model_ub.coef()
    print("Upper-bound model coefs:", coef_ub)

    assert coef_ub['x1'] <= 1.5 + 1e-6, \
        "Beta upper-bound constraint x1 <= 1.5 violated: x1 = {0}".format(coef_ub['x1'])


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_equality_constraint_enforced)
    pyunit_utils.standalone_test(test_beta_and_linear_constraints_no_npe)
else:
    test_equality_constraint_enforced()
    test_beta_and_linear_constraints_no_npe()
