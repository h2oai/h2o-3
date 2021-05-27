from __future__ import division
from __future__ import print_function
import sys

sys.path.insert(1, "../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.gam import H2OGeneralizedAdditiveEstimator


# this test was written by Erin Ledell to show case the bug in Python when calling cross-validation
def test_gam_cross_validation():
    # create frame knots
    knots1 = [-1.99905699, -0.98143075, 0.02599159, 1.00770987, 1.99942290]
    frameKnots1 = h2o.H2OFrame(python_obj=knots1)
    knots2 = [-1.999821861, -1.005257990, -0.006716042, 1.002197392, 1.999073589]
    frameKnots2 = h2o.H2OFrame(python_obj=knots2)
    knots3 = [-1.999675688, -0.979893796, 0.007573327, 1.011437347, 1.999611676]
    frameKnots3 = h2o.H2OFrame(python_obj=knots3)

    nfold = 4
    # import the dataset
    h2o_data = h2o.import_file(
        pyunit_utils.locate("smalldata/glm_test/multinomial_10_classes_10_cols_10000_Rows_train.csv"))

    # convert the C1, C2, and C11 columns to factors
    h2o_data["C1"] = h2o_data["C1"].asfactor()
    h2o_data["C2"] = h2o_data["C2"].asfactor()
    h2o_data["C11"] = h2o_data["C11"].asfactor()

    # create a fold column
    fold_numbers = h2o_data.kfold_column(n_folds=nfold, seed=1234)

    # rename the column "fold_numbers"
    fold_numbers.set_names(["fold_numbers"])

    # append the fold_numbers column to the cars dataset
    h2o_data = h2o_data.cbind(fold_numbers)

    # split into train and validation sets
    train, test = h2o_data.split_frame(ratios=[.8])

    # set the predictor and response columns
    y = "C11"
    x = ["C1", "C2"]

    # specify the knots array
    numKnots = [5, 5, 5]

    # Both of these gives an NPE

    # build the GAM model
    h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial',
                                                gam_columns=["C6", "C7", "C8"],
                                                scale=[1, 1, 1],
                                                num_knots=numKnots,
                                                knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key],
                                                nfolds=nfold)

    h2o_model.train(x=x, y=y, training_frame=train)

    # build the GAM model
    h2o_model = H2OGeneralizedAdditiveEstimator(family='multinomial',
                                                gam_columns=["C6", "C7", "C8"],
                                                scale=[1, 1, 1],
                                                num_knots=numKnots,
                                                knot_ids=[frameKnots1.key, frameKnots2.key, frameKnots3.key])

    h2o_model.train(x=x, y=y, training_frame=train, fold_column="fold_numbers")


if __name__ == "__main__":
    pyunit_utils.standalone_test(test_gam_cross_validation)
else:
    test_gam_cross_validation()
