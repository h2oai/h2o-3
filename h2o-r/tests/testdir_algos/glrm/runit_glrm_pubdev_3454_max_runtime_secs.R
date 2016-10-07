setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# I am trying to resolve a customer issue as captured in PUBDEV-3454.  GLRM seems to sometimes stall and takes
# hours to generate a model.  In this setting, we show the customers how to reduce the model run time by
# setting a time limit on how long we will let the algorithm to run.  Basically, let the glrm run a few times
# and observe how long it takes to converge.  Then, set the maximum run time to be the average of those runs or
# something like that.  This will prevent the algo from stalling but may not get to the desirable local
# optimal.

test.glrm.pubdev_3454 <- function() {

	datasets = "/mnt/0xcustomer-datasets/c66/glrm_data_DTolstonogov.csv"
	running_inside_hexdata = file.exists(datasets)

	if (!running_inside_hexdata) {
		stop("0xdata internal test and data.  Running in the wrong environment.")
	}

	Log.info("Input data files...")
	data.hex <- h2o.uploadFile(datasets, destination_frame="data.hex", na.strings=rep("NA", 10))

	features = c("emps_cnt", "client_revenue", "esdb_state", "esdb_zip", "revenue_adp", "status", "revenue_region", "business_unit", "naics3")
	seedUsed = as.integer(Sys.time()) # set random seed for current run
	max_runtime_sec = 60;   # restrict maximum algorithm run time in seconds

	clients_glrm <- h2o.glrm(training_frame=data.hex, cols=features, k=9, model_id="clients_core_glrm", loading_name="arch_x", loss="Quadratic", transform="STANDARDIZE", multi_loss="Categorical", regularization_x="L2",  regularization_y="L1", gamma_x=0.2, gamma_y=0.5, max_iterations=1000, init="SVD", seed=seedUsed, max_runtime_secs=max_runtime_sec)

	print("************** GLRM model run time in seconds: ")
	print(find_grid_runtime(clients_glrm@model_id))
}

doTest("GLRM Test: PUBDEV-3454, GLRM stalling, prevented by setting max_runtime_secs", test.glrm.pubdev_3454)