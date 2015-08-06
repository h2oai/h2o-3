# Validate given models' parameters against expected values
expect_model_param <- function(models, attribute_name, expected_values) {
  params <- unique(lapply(models, function(model) { model@allparameters[[attribute_name]] } ))
  expect_equal(length(params), length(expected_values))
  expect_true(all(params %in% expected_values))
  expect_true(all(expected_values %in% params))
}

