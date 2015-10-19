

import h2o, tests
import random

def lambda_search():
    
    

    #Log.info("Importing prostate.csv data...\n")
    prostate = h2o.import_file(tests.locate("smalldata/logreg/prostate.csv"))
    #prostate.summary()

    # GLM without lambda search, lambda is single user-provided value
    #Log.info("H2O GLM (binomial) with parameters: lambda_search = TRUE, nfolds: 2\n")
    prostate_nosearch = h2o.glm(x=prostate[2:9], y=prostate[1], training_frame = prostate.hex, family = "binomial", nlambdas = 5, lambda_search = False, n_folds = 2)
    params_nosearch = prostate_nosearch.params()

    try:
      prostate_nosearch.getGLMLambdaModel(0.5)
      assert False, "expected an error"
    except EnvironmentError:
      assert True

    # GLM with lambda search, return only model corresponding to best lambda as determined by H2O
    #Log.info("H2O GLM (binomial) with parameters: lambda_search: TRUE, nfolds: 2\n")
    prostate_search = h2o.glm(x=prostate[2:9], y=prostate[1], training_frame = prostate.hex, family = "binomial", nlambdas = 5, lambda_search = True, n_folds = 2)
    params_search = prostate_search.params()

    random_lambda = random.choice(prostate_search.lambda_all())
    #Log.info(cat("Retrieving model corresponding to randomly chosen lambda", random_lambda, "\n"))
    random_model = prostate_search.getGLMLambdaModel(random_lambda)
    assert random_model.getLambda() == random_lambda, "expected equal lambdas"

    #Log.info(cat("Retrieving model corresponding to best lambda", params.bestlambda$lambda_best, "\n"))
    best_model = prostate_search.getGLMLambdaModel(params_search.bestlambda())
    assert best_model.model() == prostate_search.model(), "expected models to be equal"
  

pyunit_test = lambda_search
