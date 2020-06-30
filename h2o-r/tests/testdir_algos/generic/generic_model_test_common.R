compare_output <- function(original_output, generic_output, skipped_original, skipped_generic){
  
  removed_original <- c()
  for (i in 1:length(original_output)) {
    line <- original_output[i]
    
    for (name in skipped_original) {
      if(grepl(name, line)){
        removed_original <- append(removed_original, i)
      }
    }
  }
  if(length(removed_original) > 0){
    original_output <- original_output[-removed_original]        
  }
  
  removed_generic <- c()
  for (i in 1:length(generic_output)) {
    line <- generic_output[i]
    for (name in skipped_generic) {
      if(grepl(name, line)){
        removed_generic <- append(removed_generic, i)
      }
    }

  }
  if(length(removed_generic) > 0){
    generic_output <- generic_output[-removed_generic]
  }
  print(original_output)
  print(generic_output)
  print(length(original_output))
  print(length(generic_output))
  expect_equal(TRUE, all.equal(original_output, generic_output))
}

compare_params <- function(original_model, generic_model){
    names <- names(original_model@parameters)
    print("Original params:")
    for (param_name in names){
        print(original_model@allparameters[param_name])
    }
    print("Generic model params:")
    for (param_name in names){
        print(generic_model@allparameters[param_name])
    }
    
    for (param_name in names(original_model@parameters)){
        original_param = original_model@allparameters[param_name]
        generic_param = generic_model@allparameters[param_name]
        expect_false(is.null(original_param))
        expect_false(is.null(generic_param))
    }
}
