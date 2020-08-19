#'
#' Calculate Permutation Feature Importance 
#'

h2o.permutation_varim() <- function(model, validation_frame){
    if (is.H2OFrame(validation_frame))
         tryCatch(permutation_varim_table <- .newExpr('Perm_Feature_importance', model, validation_frame), 
                error = function(err) {
                    message(err)
                    stop("argument " validation_frame" is a valid H2OFrame, newExpr didnt work")
                },
                warning = function(err){
                    message("warning message:")
                    message(err)
                },
                finally = {
                    message("Loading permutation_varim")
                    return(permutation_varim_table)
                })
    else stop("Input frame" validation_frame "is not H2OFrame") # find out how to check for model
        
}
