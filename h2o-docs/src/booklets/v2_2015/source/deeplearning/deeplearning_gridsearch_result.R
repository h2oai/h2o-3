# TO DO: Check that this works

# print out all prediction errors and run times of the models
grid

# print out the auc for all of the models
grid_models <- lapply(grid@model_ids, function(model_id) { model = h2o.getModel(model_id) })
for (i in 1:length(grid_models)) {
  print(sprintf("auc: %f", h2o.auc(grid_models[[i]])))
}
