import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator
from h2o.grid.grid_search import H2OGridSearch

def test_glm_binomial():
    """
    PUBDEV-6349: make sure gridsearch works with negative binomial family.
    """

    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))
    myY = "GLEASON"
    myX = ["ID","AGE","RACE","CAPSULE","DCAPS","PSA","VOL","DPROS"]
    hyper_parameters = {'alpha': [0, 0.5, 0.99], 
                            'theta' : [0.000000001, 0.01, 0.1, 0.5, 1]}    # set hyper_parameters for grid search

    # train H2O GLM model with grid search
    model_h2o_grid_search = H2OGridSearch(H2OGeneralizedLinearEstimator(family="negativebinomial", Lambda=0.5),
                                              hyper_parameters)
    model_h2o_grid_search.train(x=myX, y=myY, training_frame=prostate)
    fk1 = model_h2o_grid_search.get_grid(sort_by="residual_deviance")

    model_h2o_grid_search2 = H2OGridSearch(H2OGeneralizedLinearEstimator(family="negativebinomial", lambda_search=True),
                                          hyper_parameters)
    model_h2o_grid_search2.train(x=myX, y=myY, training_frame=prostate)
    fk2 = model_h2o_grid_search2.get_grid(sort_by="residual_deviance")
    rdev1 = list(fk1.mean_residual_deviance(train=True).values())
    rdev2 = list(fk2.mean_residual_deviance(train=True).values())
    print(model_h2o_grid_search.get_grid("residual_deviance"))
    print(model_h2o_grid_search2.get_grid("residual_deviance"))
    assert(len(model_h2o_grid_search.get_grid())==15)
    assert min(rdev1) >= min(rdev2), "Lambda search should provide a better solution {0} than fixed lambda value {1}" \
                                     " but is not.".format(rdev2, rdev1)
    
 #   assert model_h2o_grid_search.

if __name__ == "__main__":
    pyunit_utils.standalone_test(test_glm_binomial)
else:
    test_glm_binomial()
