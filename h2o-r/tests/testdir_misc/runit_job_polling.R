setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

train.models <- function(prostate.hex) {
    wasted_times <- c()
    for (i in seq(5)) {
        start_time <- Sys.time()
        prostate.h2o <- h2o.gbm(x = 3:9, y = "CAPSULE", training_frame = prostate.hex,
                                ntrees = 100, max_depth = 5, learn_rate = 0.01)
        end_time <- Sys.time()

        print("Waited:")
        print(end_time - start_time)
        print("Run-time (ms):")
        print(prostate.h2o@model$run_time)
        print("Wasted time (ms):")
        wasted <- as.numeric(end_time - start_time)*1000 - prostate.h2o@model$run_time
        print(wasted)
        wasted_times <- c(wasted_times, wasted)
    }
    return(wasted_times)
}

test.job.polling <- function() {
    if (isClient()) {
        # Backend-waiting polling will not be any faster in the client mode because Job is owned by a different
        # node other than the one that is exposing the rest api (=client) 
        return()
    }

    prostate.hex <- h2o.importFile(locate("smalldata/logreg/prostate.csv"))

    prostate.hex$CAPSULE <- as.factor(prostate.hex$CAPSULE)
    prostate.hex$RACE <- as.factor(prostate.hex$RACE)

    conn <- h2o.getConnection()

    # check that default timeout is 1000ms
    default_timeout <- .get.session.property(conn@mutable$session_id, "job.fetch_timeout_ms")
    expect_equal(default_timeout, "1000")

    print("\n CLASSIC POLLING \n")
    .set.session.property(conn@mutable$session_id, "job.fetch_timeout_ms", "-1")

    overhead.classic <- train.models(prostate.hex)

    print("\n BACKEND WAIT POLLING \n")
    .set.session.property(conn@mutable$session_id, "job.fetch_timeout_ms", "1000")

    overhead.waiting <- train.models(prostate.hex)

    expect_gt(mean(overhead.classic) / mean(overhead.waiting), 2) # at least 2x fast but in reality much more
}

doTest("Test Job polling works better with internal wait on backend", test.job.polling)

