# TO DO: Add Python version
# (Python Grid API is not complete yet)

# Example R version below
# # print out all prediction errors and run times of the models
# model_grid
# 
# # print out the auc for all of the models
# grid_models <- lapply(model_grid@model_ids, function(model_id) { model = h2o.getModel(model_id) })
# for (i in 1:length(grid_models)) {
#   print(sprintf("auc: %f", h2o.auc(grid_models[[i]])))
# }
