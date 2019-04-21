setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# This is a replica of the test used by Nidhi Mehta when she filed JIRA PUBDEV-4595 model error when
# there are white spaces (for R client only) or when there are /.  I have fixed the backend code to replace
# / with _ and generate a warning to the user about the change in model_id.  This test is to make sure that
# the R client does not generate warnings when there are spaces in the model id.  It should generate a warning
# when the user includes / in the model id.
glm2Benign <- function() {
	my_model_id = "Wendy Wong and Nidhi Mehta"	# should raise no warning
	bhexFV <- h2o.uploadFile(locate("smalldata/logreg/benign.csv"), destination_frame="benignFV.hex")
	maxX <- 11
	Y <- 4
	X   <- 3:maxX
	X   <- X[ X != Y ]

	Log.info(paste0("The model_id is ", my_model_id))
	Log.info("Build the model and expect no warning.")
	mFV <- h2o.glm(model_id = my_model_id, y = Y, x = colnames(bhexFV)[X], training_frame = bhexFV,
	family = "binomial", nfolds = 5, alpha = 0, lambda = 1e-5)

	my_model_id = "Wendy / Wong / and / Nidhi / Mehta"	# expect warning here
	Log.info(paste0("The model_id is ", my_model_id))
	Log.info("Build the model and expect a warning.")

	expect_warning(h2o.glm(model_id = my_model_id, y = Y, x = colnames(bhexFV)[X], training_frame = bhexFV,
	family = "binomial", nfolds = 5, alpha = 0, lambda = 1e-5))

	my_model_id = ' test -  june/fifteenth ' # expect warning here too.
	Log.info(paste0("The model_id is ", my_model_id))
	Log.info("Build the model and expect a warning.")

	expect_warning(h2o.glm(model_id = my_model_id, y = Y, x = colnames(bhexFV)[X], training_frame = bhexFV,
	family = "binomial", nfolds = 5, alpha = 0, lambda = 1e-5))
}

doTest("GLM: Benign Data", glm2Benign)