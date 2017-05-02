# print out all prediction errors and run times of the models
model_grid

# print out the Test MSE for all of the models
for (model_id in model_grid@model_ids) {
  mse <- h2o.mse(h2o.getModel(model_id), valid = TRUE)
  print(sprintf("Test set MSE: %f", mse))
}
