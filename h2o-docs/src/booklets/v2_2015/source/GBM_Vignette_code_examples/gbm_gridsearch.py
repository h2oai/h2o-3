#Define parameters for gridsearch
ntrees_opt = [5,10,15]
max_depth_opt = [2,3,4]
learn_rate_opt = [0.1,0.2]
hyper_parameters = {"ntrees": ntrees_opt, "max_depth":max_depth_opt, "learn_rate":learn_rate_opt}

from h2o.grid.grid_search import H2OGridSearch
gs = H2OGridSearch(H2OGradientBoostingEstimator, hyper_params=hyper_parameters)
gs.train(x=myX, y="IsDepDelayed", training_frame=air_train_hex, validation_frame=air_valid_hex)
