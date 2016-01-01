setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")



checkRollups <- function(h2o_data, r_data) {
    h2o_num_cols <- ncol(h2o_data)
    r_num_cols <- ncol(r_data)
    print("Expect H2O and R datasets to have the same number of columns.")
    print(paste0("H2O cols: ",h2o_num_cols))
    print(paste0("R cols: ",r_num_cols))
    expect_equal(h2o_num_cols, r_num_cols)

    h2o_sum <- .h2o.__remoteSend(paste0("Frames/", attr(h2o_data, "id"), "/summary"), method = "GET")$frames[[1]]
    for (c in 1:h2o_num_cols) {
        print("")
        print(paste0("--- Column number ---: ",c))
        print("")
        if (h2o_sum$columns[[c]]$type == "enum") {
            r_col_is_factor <- is.factor(r_data[[c]])
            print("H2O column is enum. Expect R column to be a factor.")
            print(paste0("R column is factor: ",r_col_is_factor))
            expect_true(r_col_is_factor)

            h2o_r_level_diff <- setdiff(levels(r_data[[c]]),h2o_sum$columns[[c]]$domain)
            print("Expect H2O and R levels to be equivalent")
            print(paste0("Difference in levels: ",h2o_r_level_diff))
            expect_equal(length(h2o_r_level_diff),0)
        } else if ((h2o_sum$columns[[c]]$type == "real") || (h2o_sum$columns[[c]]$type == "int")) {
            r_col_is_numeric <- is.numeric(r_data[[c]])
            print("H2O column is real or int. Expect R column to be numeric.")
            print(paste0("R column is numeric: ",r_col_is_numeric))
            expect_true(r_col_is_numeric)

            h2o_mean_rollup    <- h2o_sum$columns[[c]]$mean
            h2o_sigma_rollup   <- h2o_sum$columns[[c]]$sigma
            h2o_max_rollup     <- h2o_sum$columns[[c]]$maxs[1]
            h2o_min_rollup     <- h2o_sum$columns[[c]]$mins[1]
            h2o_zeros_rollup   <- h2o_sum$columns[[c]]$zero_count
            h2o_missing_rollup <- h2o_sum$columns[[c]]$missing_count

            #h2o_mean         <- mean(h2o_data[[c]])
            #h2o_sigma        <- sd(h2o_data[[c]])
            #h2o_max          <- max(h2o_data[[c]])
            #h2o_min          <- min(h2o_data[[c]])

            r_mean           <- mean(r_data[[c]],na.rm=TRUE)
            r_sigma          <- sd(r_data[[c]],na.rm=TRUE)
            r_max            <- max(r_data[[c]],na.rm=TRUE)
            r_min            <- min(r_data[[c]],na.rm=TRUE)
            r_zeros          <- sum(r_data[[c]]==0,na.rm=TRUE)
            r_missing        <- sum(is.na(r_data[[c]]))

            print("Expect H2O and R mean, sd, max, and min to be equivalent")
            h2o_rollup_df <- data.frame(mean=h2o_mean_rollup,sd=h2o_sigma_rollup,max=h2o_max_rollup,min=h2o_min_rollup,
                                        zeros=h2o_zeros_rollup,missing=h2o_missing_rollup)
            print(paste0("H2O-rollup (mean, sd, max, min, zeros, missing): ",h2o_rollup_df))
            print("")
            #h2o_df <- data.frame(mean=h2o_mean,sd=h2o_sigma,max=h2o_max,min=h2o_min)
            #print(paste0("H2O (mean, sd, max, min): ",h2o_df))
            #print("")
            r_df <- data.frame(mean=r_mean,sd=r_sigma,max=r_max,min=r_min,zeros=r_zeros,missing=r_missing)
            print(paste0("R (mean, sd, max, min, zeros, missing): ",r_df))
            print("")

            expect_equal(h2o_rollup_df$mean, r_df$mean, tol=1e-8)
            expect_equal(h2o_rollup_df$max, r_df$max, tol=1e-8)
            expect_equal(h2o_rollup_df$min, r_df$min, tol=1e-8)
            expect_equal(h2o_rollup_df$sd, r_df$sd, tol=1e-8)
            expect_equal(h2o_rollup_df$zeros, r_df$zeros)
            expect_equal(h2o_rollup_df$missing, r_df$missing)
        } else if ((h2o_sum$columns[[c]]$type == "string")) { next
        } else { stop(paste0("Unknown H2O column type: ",h2o_sum$columns[[c]]$type)) }
    }
}

test <- function() {
    h2o_datasets <- list()
    r_datasets <- list()

    smalldata_paths <- c("smalldata/iris/iris.csv", "smalldata/logreg/prostate.csv", "smalldata/junit/cars_20mpg.csv",
                         "smalldata/junit/australia.csv", "smalldata/airlines/AirlinesTrain.csv.zip",
                         "smalldata/arcene/arcene_train.data", "smalldata/chicago/chicagoCensus.csv",
                         "smalldata/covtype/covtype.20k.data" )
    smalldata <- lapply(smalldata_paths, function (p) h2o.importFile(h2oTest.locate(p)))
    for (data in smalldata) { h2o_datasets[[length(h2o_datasets)+1]] <- data }

    seed <- sample(1:10000,1)
    print(paste0("SEED: ", seed))

    sparse_binary <- h2o.createFrame(categorical_fraction = 0.0, integer_fraction = 0.0, binary_fraction = 1.0,
                                     binary_ones_fraction = 0.01, missing_fraction = 0.0, seed = seed)
    h2o_datasets[[length(h2o_datasets)+1]] <- sparse_binary

    binary <- h2o.createFrame(categorical_fraction = 0.0, integer_fraction = 0.0, binary_fraction = 1.0,
                              binary_ones_fraction = 0.5, missing_fraction = 0.0, seed = seed)
    h2o_datasets[[length(h2o_datasets)+1]] <- binary

    integer <- h2o.createFrame(categorical_fraction = 0.0, integer_fraction = 1.0, binary_fraction = 0.0,
                               missing_fraction = 0.0, seed = seed)
    h2o_datasets[[length(h2o_datasets)+1]] <- integer

    double <- h2o.createFrame(categorical_fraction = 0.0, integer_fraction = 0.0, binary_fraction = 0.0,
                              missing_fraction = 0.0, seed = seed)
    h2o_datasets[[length(h2o_datasets)+1]] <- double

    categorical <- h2o.createFrame(categorical_fraction = 1.0, integer_fraction = 0.0, binary_fraction = 0.0,
                                   missing_fraction = 0.0, seed = seed)
    h2o_datasets[[length(h2o_datasets)+1]] <- categorical

    alltypes <- h2o.createFrame(categorical_fraction = 0.25, integer_fraction = 0.25, binary_fraction = 0.25,
                                missing_fraction = 0.0, seed = seed)
    h2o_datasets[[length(h2o_datasets)+1]] <- alltypes

    for (data in h2o_datasets) { r_datasets[[length(r_datasets)+1]] <- as.data.frame(data) }

    num_datasets <- length(h2o_datasets)
    for (d in 1:num_datasets) {
        print("")
        print(paste0("<<<<<<<<<<<<<<<< Dataset number >>>>>>>>>>>>>>>>>: ",d))
        print("")
        checkRollups(h2o_datasets[[d]],r_datasets[[d]]) }

}

h2oTest.doTest("Rollup Stats", test)

