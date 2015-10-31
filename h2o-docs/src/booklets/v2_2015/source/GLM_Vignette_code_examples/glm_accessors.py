binomial_fit.summary()
binomial_fit._model_json["output"]["model_summary"].__getitem__('number_of_iterations')

binomial_fit.null_degrees_of_freedom(train=True, valid=True)
binomial_fit.residual_degrees_of_freedom(train=True, valid=True)

binomial_fit.mse(train=True, valid=True)
binomial_fit.r2(train=True, valid=True)
binomial_fit.logloss(train=True, valid=True)
binomial_fit.auc(train=True, valid=True)
binomial_fit.giniCoef(train=True, valid=True)
binomial_fit.null_deviance(train=True, valid=True)
binomial_fit.residual_deviance(train=True, valid=True)
binomial_fit.aic(train=True, valid=True)

