#' Get all default arguments for an H2O algorithm
#' @param Function Any function that is in your environment (h2o.glm, h2o.deeplearning, h2o.gbm, data.frame, etc)
#' @author navdeepgill

GetArgs = function(Function){
  
  #Extract out argument names and default values:
  arguments_names = data.frame(as.factor(names(formals(Function))),stringsAsFactors = F)
  arguments_vals = data.frame(as.character(formals(Function)),stringsAsFactors = F)
  
  #Bind names and default values together to get a frame:
  arguments_defaults = cbind(arguments_names,arguments_vals)
  colnames(arguments_defaults) = c("arg_name", "arg_value")
  
  #Return frame:
  return(arguments_defaults)
  
}

#***Note, be sure to load in a library if you're calling something outside of native R for this function***

#Some possible ways to use this function:
glm_args = GetArgs(h2o.glm)
dl_args = GetArgs(h2o.deeplearning)
gbm_args = GetArgs(h2o.gbm)
rf_args = GetArgs(h2o.randomForest)
kmeans_args = GetArgs(h2o.kmeans)

