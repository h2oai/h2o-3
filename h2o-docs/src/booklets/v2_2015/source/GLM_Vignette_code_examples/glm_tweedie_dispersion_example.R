# Import the training data:
training_data <- h2o.importFile("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/tweedie_p3_phi1_10KRows.csv")

# Set the predictors and response:
predictors <- c('abs.C1.', 'abs.C2.', 'abs.C3.', 'abs.C4.', 'abs.C5.')
response <- 'x'

# Build and train the model:
model <- h2o.glm(x = predictors, 
                 y = response, 
                 training_frame = training_data, 
                 family = 'tweedie',
                 solver = 'IRLSM',
                 tweedie_variance_power = 3, 
                 lambda = 0, 
                 compute_p_values = TRUE, 
                 dispersion_parameter_method = "pearson", 
                 init_dispersion_parameter = 0.5, 
                 dispersion_epsilon = 1e-4, 
                 max_iterations_dispersion = 100)

# Retrieve the estimated dispersion:
model@model$dispersion
[1] 0.7599965