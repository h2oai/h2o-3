#Define parameters for gridsearch
ntrees_opt = range(0,100,1)
max_depth_opt = range(0,20,1)
learn_rate_opt = [s/float(1000) for s in range(1,101)]
hyper_parameters = {"ntrees": ntrees_opt, 
    "max_depth":max_depth_opt, "learn_rate":learn_rate_opt}
search_criteria = {"strategy":"RandomDiscrete", 
    "max_models":10, "max_runtime_secs":100, "seed":123456}

from h2o.grid.grid_search import H2OGridSearch
gs = H2OGridSearch(H2OGradientBoostingEstimator, 
    hyper_params=hyper_parameters, search_criteria=search_criteria)
gs.train(x=myX, y="IsDepDelayed", training_frame=air_train_hex, 
    validation_frame=air_valid_hex)
