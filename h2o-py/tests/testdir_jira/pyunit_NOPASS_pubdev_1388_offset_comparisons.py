



def offset_1388():
    

    print "Loading datasets..."
    pros_hex = h2o.import_file(pyunit_utils.locate("smalldata/prostate/prostate.csv"))
    pros_hex[1] = pros_hex[1].asfactor()
    pros_hex[3] = pros_hex[3].asfactor()
    pros_hex[4] = pros_hex[4].asfactor()
    pros_hex[5] = pros_hex[5].asfactor()
    pros_hex[8] = pros_hex[8].asfactor()

    cars_hex = h2o.import_file(pyunit_utils.locate("smalldata/junit/cars.csv"))
    cars_hex[0] = cars_hex[0].asfactor()
    cars_hex[2] = cars_hex[2].asfactor()

    print "Running Binomial Comparison..."
    glm_bin_h2o = h2o.glm(x=pros_hex[2:9], y=pros_hex[1], training_frame=pros_hex, family="binomial", standardize=False,
                          offset_column="AGE", Lambda=[0], max_iterations=100)
    print "binomial"
    print "R:"
    print "deviance: {0}".format(1464.9565781185)
    print "null deviance: {0}".format(2014.93087862689)
    print "aic: {0}".format(1494.9565781185)

    print "H2O:"
    print "deviance {0}".format(glm_bin_h2o.residual_deviance())
    print "null deviance {0}".format(glm_bin_h2o.null_deviance())
    print "aic {0}".format(glm_bin_h2o.aic())

    assert abs(1464.9565781185 - glm_bin_h2o.residual_deviance()) < 0.1
    assert abs(2014.93087862689 - glm_bin_h2o.null_deviance()) < 0.1
    assert abs(1494.9565781185 - glm_bin_h2o.aic()) < 0.1

    print "Running Regression Comparisons..."

    glm_h2o = h2o.glm(x=cars_hex[2:8], y=cars_hex[1], training_frame=cars_hex, family="gaussian", standardize=False,
                      offset_column="year", Lambda = [0], max_iterations = 100)
    print "gaussian"
    print "R:"
    print "deviance: {0}".format(4204.68399275449)
    print "null deviance: {0}".format(16072.0955102041)
    print "aic: {0}".format(2062.54330117177)

    print "H2O:"
    print "deviance {0}".format(glm_h2o.residual_deviance())
    print "null deviance {0}".format(glm_h2o.null_deviance())
    print "aic {0}".format(glm_h2o.aic())

    assert abs(4204.68399275449 - glm_h2o.residual_deviance()) < 0.1
    assert abs(16072.0955102041 - glm_h2o.null_deviance()) < 0.1
    assert abs(2062.54330117177 - glm_h2o.aic()) < 0.1

    glm_h2o = h2o.glm(x=cars_hex[2:8], y=cars_hex[1], training_frame=cars_hex, family="poisson", standardize=False,
                      offset_column="year", Lambda = [0], max_iterations = 100)
    print "poisson"
    print "R:"
    print "deviance: {0}".format(54039.1725227918)
    print "null deviance: {0}".format(59381.5624028358)
    print "aic: {0}".format("Inf")

    print "H2O:"
    print "deviance {0}".format(glm_h2o.residual_deviance())
    print "null deviance {0}".format(glm_h2o.null_deviance())
    print "aic {0}".format(glm_h2o.aic())

    assert abs(54039.1725227918 - glm_h2o.residual_deviance()) < 0.1
    assert abs(59381.5624028358 - glm_h2o.null_deviance()) < 0.1
    assert abs(float('inf') - glm_h2o.aic()) < 0.1


offset_1388()
