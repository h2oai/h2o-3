# Import the training data:
training_data = h2o.import_file("http://h2o-public-test-data.s3.amazonaws.com/smalldata/glm_test/tweedie_p3_phi1_10KRows.csv")

# Set the predictors and response:
predictors = ["abs.C1.", "abs.C2.", "abs.C3.", "abs.C4.", "abs.C5.""]
response = "x"

# Build and train the model:
model = H2OGeneralizedLinearEstimator(family="tweedie", 
      	  lambda_=0, 
      	  solver="IRLSM",
      	  compute_p_values=True, 
      	  dispersion_parameter_method="pearson", 
    	  init_dispersion_parameter=0.5, 
    	  dispersion_epsilon=1e-4,
       	  tweedie_variance_power=3, 
       	  max_iterations_dispersion=100)
model.train(x=predictors, y=response, training_frame=training_data)

# Retrieve the estimated dispersion:
model._model_json["output"]["dispersion"]
0.7599964835351135
