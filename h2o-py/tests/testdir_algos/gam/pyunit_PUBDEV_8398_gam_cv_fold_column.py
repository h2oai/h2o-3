from __future__ import division
from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator

# This example is taken from Erin.  The following model building crashed on her.  I have fixed all those problems.
def test_gam_cv_fold_columns():
    # create frame knots
    knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
    frameKnots1 = h2o.H2OFrame(python_obj=knots1)
    knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
    frameKnots2 = h2o.H2OFrame(python_obj=knots2)
    knots3 = [-1.999675688, -0.979893796, 0.007573327, 1.011437347, 1.999611676]
    frameKnots3 = h2o.H2OFrame(python_obj=knots3)

    # import the dataset
    h2o_data = h2o.import_file(
        pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))
    # convert the C1, C2, and C11 columns to factors
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C11"] = h2o_data["C11"].asfactor()

    # split into train and validation sets
    train, test = h2o_data.split_frame(ratios=[.8])

    # set the predictor and response columns
    y = "C11"
    x = ["C1", "C2"]

    # specify the knots array
    numKnots = [5, 5, 5]

    # Both of these gives an NPE, should be fixed now.

    # build the GAM model gam_columns=["C6","C7","C8"]
    h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial',
                                                gam_columns=["C6", "C7", "C8"],
                                                scale=[0, 1, 2],
                                                num_knots=numKnots,
                                                knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key],
                                                nfolds=5,
                                                seed=1234,
                                                fold_assignment='modulo')

    h2o_model.train(x=x, y=y, training_frame=train)

    # create a fold column for train
    fold_numbers = train.kfold_column(n_folds=5, seed=1234)
    # rename the column "fold_numbers"
    fold_numbers.set_names(["fold_numbers"])
    train = train.cbind(fold_numbers)

    # build the GAM model
    h2o_model_fold_column = H2OGeneralizedAdditiveEstimator(family='multinomial',
                                                            gam_columns=["C6", "C7", "C8"],
                                                            scale=[0, 1, 2],
                                                            num_knots=numKnots,
                                                            knot_ids=[frameKnots1.key, frameKnots2.key,
                                                                      frameKnots3.key])

    h2o_model_fold_column.train(x=x, y=y, training_frame=train, fold_column="fold_numbers")

    # both model should return the same coefficients since they use the same fold assignment
    coeff = h2o_model.coef()
    coeff_fold_column = h2o_model_fold_column.coef()
    pyunit_utils.assertCoefDictEqual(coeff['coefficients'], coeff_fold_column['coefficients'])


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_cv_fold_columns)
else:
    test_gam_cv_fold_columns()
