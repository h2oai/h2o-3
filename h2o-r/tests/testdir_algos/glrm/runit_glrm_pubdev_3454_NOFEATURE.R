setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# I am trying to resolve a customer issue as captured in PUBDEV-3454.  Dmitry Tolstonogov (ADP)
# said that he ran a GLRM model on a data set (which he has shared with us but would not want
# us to put it out to public) with many categorical leves (~13000 columns in Y matrix).  Model
# converges in ~1 hour for 76 iterations but job runs for another 6.5 hours while nothing happens.
#  Bar indicates 10% done.
#
# The following test is written to duplicate this scenario and captured the stalling.  Once we
# found the cause and fix it, this test will be used to test our results with a different dataset
# with similar characteristic.

test.glrm.pubdev_3454 <- function() {
  Log.info("Input data files...")
  feature_types = c("enum","int","int","enum","enum","real","enum","enum","enum","int")
  data.hex <- h2o.uploadFile(locate("smalldata/glrm_test/glrm_data_DTolstonogov.csv"), destination_frame="data.hex", na.strings=rep("NA", 10), col.types=feature_types)

  features = c("emps_cnt", "client_revenue", "esdb_state", "esdb_zip", "revenue_adp", "status", "revenue_region", "business_unit", "naics3")

  ptm <- proc.time()
  clients_glrm <- h2o.glrm(training_frame=data.hex, cols=features, k=9, model_id="clients_core_glrm", loading_name="arch_x", loss="Quadratic", transform="STANDARDIZE", multi_loss="Categorical", regularization_x="L2",  regularization_y="L1", gamma_x=0.2, gamma_y=0.5, max_iterations=1000, init="SVD")
  timepassed = (proc.time() - ptm)
  print("************** GLRM model run time (seconds): ")
  print(timepassed)
  }

doTest("GLRM Test: PUBDEV-3454, GLRM stalling", test.glrm.pubdev_3454)
