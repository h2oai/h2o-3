library(h2o)
h2o.init()
path = system.file("extdata", "prostate.csv", package = "h2o")
h2o_df = h2o.importFile(path)
h2o_df$CAPSULE = as.factor(h2o_df$CAPSULE)
lambda_opts = c(1, 0.5, 0.1, 0.01, 0.001, 0.0001, 0.00001, 0)
hyper_parameters = list(lambda = lambda_opts)
grid <- h2o.grid("glm", grid_id="glm_grid_test", hyper_params = hyper_parameters,
                 y = "CAPSULE", x = c("AGE", "RACE", "PSA", "GLEASON"), training_frame = h2o_df, family = "binomial")
grid_models <- lapply(grid@model_ids, function(mid) { model = h2o.getModel(mid) })

