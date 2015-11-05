hidden_opt = [[32,32],[32,16,8],[100]]
l1_opt = [1e-4,1e-3]

hyper_parameters = {"hidden":hidden_opt, "l1":l1_opt}

from h2o.grid.grid_search import H2OGridSearch

model_grid = H2OGridSearch(H2ODeepLearningEstimator, hyper_params=hyper_parameters)
model_grid.train(x=x, y=y, distribution="multinomial", epochs=1000,
                 training_frame=train, validation_frame=test, score_interval=2,
                 stopping_rounds=3, stopping_tolerance=0.05, stopping_metric="misclassification")
