# print out all prediction errors and run times of the models
air.grid
air.grid@model

# print out a *short* summary of each of the models (indexed by parameter)
air.grid@sumtable

# print out *full* summary of each of the models
all_params = lapply(air.grid@model, function(x) { x@model$params })
all_params

# access a particular parameter across all models
shrinkages = lapply(air.grid@model, function(x) { x@model$params$shrinkage })

shrinkages
