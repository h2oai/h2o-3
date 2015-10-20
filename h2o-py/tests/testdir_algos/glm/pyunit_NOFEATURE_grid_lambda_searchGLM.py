


import random

def grid_lambda_search():
  
  

  # Log.info("Importing prostate.csv data...\n")
  prostate = h2o.import_file(path=pyunit_utils.locate("smalldata/logreg/prostate.csv"))

  #prostate.summary()

  # Log.info("H2O GLM (binomial) with parameters: alpha = c(0.25, 0.5), nlambda = 20, lambda_search = TRUE, nfolds: 2\n")
  model = h2o.glm(x=prostate[2:9], y=prostate[1], family="binomial", nlambdas=5, lambda_search=True, n_folds=2)
  if random.random() < 0.5:
    model_idx = 0
  else:
    model_idx = 1

  model_bestlambda = model.models(model_idx)
  params_bestlambda = model.params()

  # Log.info(cat("All lambda values returned:\n", params_bestlambda.lambdas()))
  assert len(params_bestlambda.lambdas()) <= 5, "expected 5 or less lambdas"

  random_lambda = random.choice(params_bestlambda.lambdas())
  print("RANDOM LAMBDA")
  print(random_lambda)

  # Log.info(cat("Retrieving model corresponding to alpha =", params_bestlambda.alpha(), "and randomly chosen lambda", random_lambda, "\n"))
  random_model = model.getGLMLambdaModel(model_bestlambda, random_lambda)

  # Log.info("EXPECTING THESE TO BE EQUAL")
  print(random_model.Lambda())
  print(random_lambda)

  assert random_model.Lambda() == random_lambda, "expected lambdas to be equal"

  # Log.info(cat("Retrieving model corresponding to alpha =", params_bestlambda.alpha(), "and best lambda", params_bestlambda.lambdaBest(), "\n"))
  best_model = h2o.getGLMLambdaModel(model_bestlambda, params_bestlambda.lambda_best())
  assert best_model.model() ==  model_bestlambda.model(), "expected models to be equal"

  # Log.info("H2O GLM (binomial) with parameters: alpha = [0.25, 0.5], nlambda = 20, lambda_search = TRUE, nfolds: 2\n")
  prostate_search = h2o.glm(x=prostate[2:9], y=prostate[1], family="binomial", alpha=[0.25, 0.5], nlambdas=5, lambda_search=True, n_folds=2)
  model_search = prostate_search.models(model_idx)
  models_best = model_search.models(model_search.best_model())
  params_best = models_best.params()

  assert params_bestlambda.lambda_best() == params_best.lambda_best(), "expected lambdas to be equal"
  assert len(params_best.lambda_all()) <= 20, "expected 20 or fewer lambdas"


grid_lambda_search()
