h2o.metalearn_cv <- function(object, metalearner = "h2o.glm.wrapper", seed = 1, keep_levelone_data = TRUE){
  out <- vector("list", length(object))
  for (i in 1:length(out)){
    out[[i]] <- h2o.metalearn(object[[i]], metalearner=metalearner, keep_levelone_data=keep_levelone_data)
    out[[i]]$metalearner <- metalearner
  }
  names(out) <- names(object)
  class(out) <- "h2o.ensemble_cv"
  return(out)
}