# print out all prediction errors and run times of the models
model_grid

# print out the Test MSE for all of the models
for (model_id in model_grid@model_ids) {
  model <- h2o.getModel(model_id)
  mse <- h2o.mse(model, valid = TRUE)
  print(sprintf("Test set MSE: %f", mse))
}
