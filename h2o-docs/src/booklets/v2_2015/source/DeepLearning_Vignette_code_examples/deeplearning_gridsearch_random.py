
hidden_opt = [[17,32],[8,19],[32,16,8],[100],[10,10,10,10]]
l1_opt = [s/1e6 for s in range(1,1001)]
hyper_parameters = {"hidden":hidden_opt, "l1":l1_opt}
search_criteria = {"strategy":"RandomDiscrete", 
    "max_models":10, "max_runtime_secs":100, 
    "seed":123456}

from h2o.grid.grid_search import H2OGridSearch
model_grid = H2OGridSearch(H2ODeepLearningEstimator, 
    hyper_params=hyper_parameters, 
    search_criteria=search_criteria)
model_grid.train(x=x, y=y, 
    distribution="multinomial", epochs=1000,
    training_frame=train, validation_frame=test,
    score_interval=2, stopping_rounds=3, 
    stopping_tolerance=0.05, 
    stopping_metric="misclassification")
