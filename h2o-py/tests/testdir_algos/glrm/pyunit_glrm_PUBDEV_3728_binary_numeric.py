from __future__ import print_function
import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glrm import H2OGeneralizedLowRankEstimator

# This unit test is to make sure that logistic loss works with both binary columns read in as either
# enum or numerics.  In addition, we check that logistic loss work with different init modes for GLRM,
# namely plusplus (default), user, SVD, random.
#
# When the init mode is set to user, we have total control of the initial values of X and Y into the GLRM.  In
# this case, the singular values obtained for GLRM when binary columns are read as numeric or enum should be the same.
# This is what I use to make sure my logistic implementation is correct.  We will compare the singular values
# and throw an error when this is not the case.  For other init modes, the values are close but not equal.
def glrm_pubdev_3728_arrest():
    print("Importing prostate.csv data...")

    # frame binary data is read in as enums.  Let's see if it runs.
    prostateF = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    prostateF_num = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_cat.csv"))
    prostateF_num[0] = prostateF_num[0].asnumeric()
    prostateF_num[4] = prostateF_num[4].asnumeric()

    loss_all = ["Logistic", "Quadratic", "Categorical", "Categorical", "Logistic", "Quadratic", "Quadratic",
                "Quadratic"]

    print("check with init = plusplus")
    glrm_h2o = H2OGeneralizedLowRankEstimator(k=5, loss_by_col=loss_all, recover_svd=True, transform="STANDARDIZE",
                                              seed=12345)
    glrm_h2o.train(x=prostateF.names, training_frame=prostateF, validation_frame=prostateF)
    glrm_h2o.show()

    # exercise logistic loss with numeric columns
    glrm_h2o_num = H2OGeneralizedLowRankEstimator(k=5, loss_by_col=loss_all, recover_svd=True, transform="STANDARDIZE",
                                                  seed=12345)
    glrm_h2o_num.train(x=prostateF_num.names, training_frame=prostateF_num, validation_frame=prostateF_num)
    glrm_h2o_num.show()

    print("check with init = random")
    glrm_h2o = H2OGeneralizedLowRankEstimator(k=5, loss_by_col=loss_all, recover_svd=True, transform="DEMEAN",
                                              seed=12345, init="random")
    glrm_h2o.train(x=prostateF.names, training_frame=prostateF, validation_frame=prostateF)
    glrm_h2o.show()

    # exercise logistic loss with numeric columns
    glrm_h2o_num = H2OGeneralizedLowRankEstimator(k=5, loss_by_col=loss_all, recover_svd=True, transform="DEMEAN",
                                                  seed=12345, init="random")
    glrm_h2o_num.train(x=prostateF_num.names, training_frame=prostateF_num, validation_frame=prostateF_num)
    glrm_h2o_num.show()

    print("check with init = SVD")
    glrm_h2o = H2OGeneralizedLowRankEstimator(k=5, loss_by_col=loss_all, recover_svd=True, seed=12345, init="SVD")
    glrm_h2o.train(x=prostateF.names, training_frame=prostateF, validation_frame=prostateF)
    glrm_h2o.show()

    # exercise logistic loss with numeric columns
    glrm_h2o_num = H2OGeneralizedLowRankEstimator(k=5, loss_by_col=loss_all, recover_svd=True, seed=12345, init="SVD")
    glrm_h2o_num.train(x=prostateF_num.names, training_frame=prostateF_num, validation_frame=prostateF_num)
    glrm_h2o_num.show()

    print("check with init = user")
    initial_y = [[-1.27675647831893E-15,64.87421383647799,2.0,1.0,2.0816681711721685E-16,8.533270440251574,
                  9.380440251572328,5.886792452830188],
                 [0.7297297297297298,66.05405405405405,2.0,0.0,1.0,23.270270270270274,9.589189189189193,
                  7.27027027027027],
                 [0.01754385964912314,70.35087719298245,2.0,1.0,-1.3877787807814457E-17,10.078947368421053,
                  42.37543859649123,6.157894736842105],
                 [0.9,65.95,2.0,0.0,0.2,81.94500000000001,16.375,7.4],
                 [0.9999999999999989,65.48598130841121,2.0,3.0,1.3877787807814457E-16,13.3092523364486,
                  13.268411214953275,6.747663551401869]]
    initial_y_h2o = h2o.H2OFrame(list(initial_y))
    glrm_h2o = H2OGeneralizedLowRankEstimator(k=5, loss_by_col=loss_all, recover_svd=True, transform="STANDARDIZE",
                                              seed=12345, init="User", user_y=initial_y_h2o)
    glrm_h2o.train(x=prostateF.names, training_frame=prostateF, validation_frame=prostateF)
    glrm_h2o.show()

    # exercise logistic loss with numeric columns
    glrm_h2o_num = H2OGeneralizedLowRankEstimator(k=5, loss_by_col=loss_all, recover_svd=True, transform="STANDARDIZE",
                                                  seed=12345, init="User", user_y=initial_y_h2o)
    glrm_h2o_num.train(x=prostateF_num.names, training_frame=prostateF_num, validation_frame=prostateF_num)
    glrm_h2o_num.show()

    # singular values from glrm models should equal if binary columns with binary loss are read in as either
    # categorical or numerics.  If not, something is wrong.
    assert pyunit_utils.equal_two_arrays(glrm_h2o._model_json["output"]["singular_vals"],
                                         glrm_h2o_num._model_json["output"]["singular_vals"], 1e-6, 1e-4), \
        "Singular values obtained from logistic loss with column type as enum and numeric do not agree.  Fix it now."

if __name__ == "__main__":
    pyunit_utils.standalone_test(glrm_pubdev_3728_arrest)
else:
    glrm_pubdev_3728_arrest()
