import sys
sys.path.insert(1, "../../")
import h2o, tests

def offset_1897():
    

    print 'Checking binomial models for GLM with and without offset'
    print 'Import prostate dataset into H2O and R...'
    prostate_hex = h2o.import_file(h2o.locate("smalldata/prostate/prostate.csv"))

    print "Checking binomial model without offset..."
    prostate_glm_h2o = h2o.glm(x=prostate_hex[["RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"]],
                               y=prostate_hex["CAPSULE"], training_frame=prostate_hex, family="binomial", standardize=False)
    print "h2o residual: {0}".format(prostate_glm_h2o.residual_deviance())
    print "r residual: {0}".format(379.053509501537)
    assert abs(379.053509501537 - prostate_glm_h2o.residual_deviance()) < 0.1

    print "Checking binomial model with offset..."
    prostate_glm_h2o = h2o.glm(x=prostate_hex[["RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON", "AGE"]],
                               y=prostate_hex["CAPSULE"], training_frame=prostate_hex, family="binomial",
                               offset_column = "AGE", standardize = False)
    print "h2o residual: {0}".format(prostate_glm_h2o.residual_deviance())
    print "r residual: {0}".format(1515.91815848623)
    assert abs(1515.91815848623 - prostate_glm_h2o.residual_deviance()) < 0.1

    print "Checking binomial model without offset..."
    prostate_glm_h2o = h2o.glm(x=prostate_hex[["RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON"]],
                               y=prostate_hex["CAPSULE"], training_frame=prostate_hex, family="poisson", standardize=False)
    print "h2o residual: {0}".format(prostate_glm_h2o.residual_deviance())
    print "r residual: {0}".format(216.339989007507)
    assert abs(216.339989007507 - prostate_glm_h2o.residual_deviance()) < 0.1

    print "Checking binomial model with offset..."
    prostate_glm_h2o = h2o.glm(x=prostate_hex[["RACE", "DPROS", "DCAPS", "PSA", "VOL", "GLEASON", "AGE"]],
                               y=prostate_hex["CAPSULE"], training_frame=prostate_hex, family="poisson",
                               offset_column = "AGE", standardize = False)
    print "h2o residual: {0}".format(prostate_glm_h2o.residual_deviance())
    print "r residual: {0}".format(2761.76218461138)
    assert abs(2761.76218461138 - prostate_glm_h2o.residual_deviance()) < 0.1

if __name__ == "__main__":
    tests.run_test(sys.argv, offset_1897)
