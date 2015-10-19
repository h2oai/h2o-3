



def link_incompatible_error():
    
    


    print("Reading in original prostate data.")
    prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/prostate/prostate.csv.zip"))

    print("Throw error when trying to create model with incompatible logit link.")
    try:
        h2o.model = h2o.glm(x=prostate[1:8], y=prostate[8], family="gaussian", link="logit")
        assert False, "expected an error"
    except EnvironmentError:
        assert True

    try:
        h2o.model = h2o.glm(x=prostate[1:8], y=prostate[8], family="tweedie", link="log")
        assert False, "expected an error"
    except EnvironmentError:
        assert True

    try:
        h2o.model = h2o.glm(x=prostate[2:9], y=prostate[1], family="binomial", link="inverse")
        assert False, "expected an error"
    except EnvironmentError:
        assert True



link_incompatible_error()
