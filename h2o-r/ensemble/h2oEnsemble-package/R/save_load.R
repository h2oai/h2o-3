# Functions for saving base models and ensembles
# TO DO: Write tests

# # Save all the base models from an ensemble
# h2o.saveBaseModels <- function(object, path = "", force = FALSE) {
#   for (model in basefits) {
#     h2o.saveModel(object = model, path = path, force = force)
#   }
# }

# Generic save function for ensemble that will allow users to save and reload the entire ensemble
h2o.save_ensemble <- function(object, path = "", force = FALSE, export_levelone = FALSE) {
  
  # Save the base learner models
  for (model in object$basefits) {
    h2o.saveModel(object = model, path = path, force = force)
  }
  # Save the metalearner model
  h2o.saveModel(object = object$metafit, path = path, force = force)
  
  # Save the ensemble object as a RData file
  # TO DO: Autogen a name of the ensemble file similar to H2OModels (currently hardcoded)
  rdata_file <- "ensemble.RData"
  fit <- object  #maybe change this, want this object to be called "fit"
  save(fit, file = sprintf("%s/%s", path, rdata_file))
  
  if (export_levelone) {
    # Export the levelone data, cbind(Z, y), to CSV
    levelone <- fit$levelone
    h2o.exportFile(levelone, path = sprintf("%s/levelone.csv", path), force = force)
  }
}


# Note: If the h2o cluster that was used to train the ensemble model is still running,
# then you don't have to import the levelone frame since it will still be in memory.
# Otherwise, you will need `import_levelone = TRUE`
h2o.load_ensemble <- function(path, import_levelone = FALSE) {
  
  model_files <- list.files(path)
  ensemble_file <- grep("RData", model_files, value = TRUE)
  
  # Load the ensemble object as a RData file, object named `fit`
  load(sprintf("%s/%s", path, ensemble_file)) 
  
  # Load the base learner models
  base_model_ids <- sapply(fit$basefits, function(ll) ll@model_id)
  for (model_id in base_model_ids) {
    h2o.loadModel(sprintf("%s/%s", path, model_id))
    print(model_id)
  }
  
  # Load the metalearner model (TO DO)
  meta_model_id <- fit$metafit@model_id
  h2o.loadModel(sprintf("%s/%s", path, meta_model_id))
  print(meta_model_id)
  
  # Import levelone data from disk
  if (import_levelone) {
    fit$levelone <- h2o.importFile(path = sprintf("%s/levelone.csv", path))
  }
  
  # Return the fit
  return(fit)
}


