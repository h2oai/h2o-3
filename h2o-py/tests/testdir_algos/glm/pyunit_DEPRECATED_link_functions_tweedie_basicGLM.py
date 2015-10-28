import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils




def link_functions_tweedie_basic():
    
    

    print "Read in prostate data."
    hdf = h2o.upload_file(pyunit_utils.locate("smalldata/prostate/prostate_complete.csv.zip"))

    print "Testing for family: TWEEDIE"
    print "Set variables for h2o."
    y = "CAPSULE"
    x = ["AGE","RACE","DCAPS","PSA","VOL","DPROS","GLEASON"]

    print "Create models with canonical link: TWEEDIE"
    model_h2o_tweedie = h2o.glm(x=hdf[x], y=hdf[y], family="tweedie", link="tweedie", alpha=[0.5], Lambda = [0])

    print "Compare model deviances for link function tweedie (using precomputed values from R)"
    deviance_h2o_tweedie = model_h2o_tweedie.residual_deviance() / model_h2o_tweedie.null_deviance()

    assert 0.721452 - deviance_h2o_tweedie <= 0.01, "h2o's residual/null deviance is more than 0.01 lower than R's. h2o: " \
                                                    "{0}, r: {1}".format(deviance_h2o_tweedie, 0.721452)





if __name__ == "__main__":
    pyunit_utils.standalone_test(link_functions_tweedie_basic)
else:
    link_functions_tweedie_basic()
