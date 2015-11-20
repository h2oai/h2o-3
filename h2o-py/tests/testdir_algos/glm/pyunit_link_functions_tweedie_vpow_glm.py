import sys
sys.path.insert(1,"../../../")
import h2o
from tests import pyunit_utils
from h2o.estimators.glm import H2OGeneralizedLinearEstimator


def link_functions_tweedie_vpow():
  # Load example data from HDtweedie, y = aggregate claim loss
  hdf = h2o.upload_file(pyunit_utils.locate("smalldata/glm_test/auto.csv"))
  y = "y"
  x = list(set(hdf.names) - set(["y"]))

  print "Testing for family: TWEEDIE"
  print "Create models with canonical link: TWEEDIE"
  # Iterate over different variance powers for tweedie
  vpower = [0, 1, 1.5]
  r_dev = [0.7516627, 0.6708826, 0.7733762]
  r_null = [221051.88369951, 32296.29783702, 20229.47425307]
  for ridx, vpow in enumerate(vpower):
    print "Fit h2o.glm:"
    h2ofit = H2OGeneralizedLinearEstimator(family="tweedie",
                                           link="tweedie",
                                           tweedie_variance_power=vpow,
                                           tweedie_link_power=1-vpow,
                                           alpha=0.5,
                                           Lambda=0)
    h2ofit.train(x=x,y=y, training_frame=hdf)

    print "Testing Tweedie variance power: {0}".format(vpow)

    print "Compare model deviances for link function tweedie"
    deviance_h2o_tweedie = h2ofit.residual_deviance() / h2ofit.null_deviance()

    assert r_dev[ridx] - deviance_h2o_tweedie <= 0.01, "h2o's residual/null deviance is more than 0.01 lower than " \
                                                       "R's. h2o: {0}, r: {1}".format(deviance_h2o_tweedie, r_dev[ridx])

    print "compare null and residual deviance between R glm and h2o.glm for tweedie"
    assert abs(r_null[ridx] - h2ofit.null_deviance()) < 1e-6, "h2o's null deviance is not equal to R's. h2o: {0}, r: " \
                                                              "{1}".format(h2ofit.null_deviance(), r_null[ridx])




if __name__ == "__main__":
  pyunit_utils.standalone_test(link_functions_tweedie_vpow)
else:
  link_functions_tweedie_vpow()
