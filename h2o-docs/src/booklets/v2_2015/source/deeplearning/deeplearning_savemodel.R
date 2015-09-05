# Specify a model and the file path where it is to be saved
best_model <- h2o.getModel(model_grid@model_ids[[2]])
h2o.saveModel(object = good_model, 
              path = "/tmp/mymodel", 
              force = TRUE)