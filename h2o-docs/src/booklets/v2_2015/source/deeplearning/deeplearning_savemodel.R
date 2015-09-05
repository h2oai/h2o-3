# TO DO: Check this code

# Specify a model and the file path where it is to be saved
h2o.saveModel(object = grid@model[[1]], 
              name = "/tmp/mymodel", 
              force = TRUE)

# Alternatively, save the model key in some directory (here we use /tmp)
h2o.saveModel(object = grid@model[[1]], 
              dir = "/tmp", 
              force = TRUE)