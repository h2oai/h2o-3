#'
#' Calculate Permutation Feature Importance 
#'
#' @param model     Trained model which's score is going to be used.
#' @param frame     Training frame of the model which is going to be permuted
#' @param metric    Loss Function metric (defalt is MSE)
#' @return          Frame with Relative, Scaled and Percentage scaled importances

h2o.permutation_varimp <- function(model, frame, metric = "MSE"){
    if (!is.H2OFrame(frame)){
        permutation_varim_table <- .newExpr("PermutationVarImp", model, frame, metric)
    }   else    {
        warning(paste0("Permutation Variable Importance cannot be calculated for ", class(frame), ". H2OFrame is requrired"))
        return(NULL)
    }
    
    return(permutation_varim_table)
}

h2o.permutation_varimp.oat <- function(model, frame){
    if (!is.H2OFrame(frame)){
        permutation_varim_table <- .newExpr("PermutationVarImpOat", model, frame)
    }   else    {
        warning(paste0("Permutation Variable Importance cannot be calculated for ", class(frame), ". H2OFrame is requrired"))
        return(NULL)
    }

    return(permutation_varim_table)
}
