setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")
library(testthat)

checkMerge <- function(x, y, all.x, all.y) {
    h2o_radix_merge_result = h2o.merge(x=x, y=y, all.x=all.x, all.y=all.y, method="radix")
    print(h2o_radix_merge_result)

    #h2o_hash_merge_result = h2o.merge(x=x, y=y, by.x=by.x, by.y=by.y, all.x=all.x, all.y=all.y, method="hash")
    #print(h2o_hash_merge_result)

    base_merge_result = merge(x=as.data.frame(x), y=as.data.frame(y), all.x=all.x, all.y=all.y)

    # check dimensions
    base_rows = nrow(base_merge_result)
    base_cols = ncol(base_merge_result)
    h2o_radix_rows = nrow(h2o_radix_merge_result)
    h2o_radix_cols = ncol(h2o_radix_merge_result)
    #h2o_hash_rows = nrow(h2o_hash_merge_result)
    #h2o_hash_cols = ncol(h2o_hash_merge_result)

    rows_equal_radix = base_rows == h2o_radix_rows
    if (!rows_equal_radix) {
        print(paste0("Base and H2O (radix) merge results have different number of rows! Base rows: ",base_rows,",
                     H2O (radix) rows: ", h2o_radix_rows))
    }
    expect_true(rows_equal_radix)

    #rows_equal_hash = base_rows == h2o_hash_rows
    #if (!rows_equal_hash) {
    #    print(paste0("Base and H2O (hash) merge results have different number of rows! Base rows: ",base_rows,",
    #                 H2O (hash) rows: ", h2o_hash_rows))
    #}
    #expect_true(rows_equal_hash)

    cols_equal_radix = base_cols == h2o_radix_cols
    if (!cols_equal_radix) {
        print(paste0("Base and H2O (radix) merge results have different number of cols! Base cols: ",base_cols,",
                     H2O (radix) cols: ", h2o_radix_cols))
    }
    expect_true(cols_equal_radix)

    #cols_equal_hash = base_cols == h2o_hash_cols
    #if (!cols_equal_hash) {
    #    print(paste0("Base and H2O (hash) merge results have different number of cols! Base cols: ",base_cols,",
    #                 H2O (hash) cols: ", h2o_hash_cols))
    #}
    #expect_true(cols_equal_hash)

    # compare the base R result with the h2o result: shared columns
    for (c in intersect(names(x), names(y))) {
        h = as.data.frame(h2o_radix_merge_result[[c]])
        b = base_merge_result[[c]]
        diff = setdiff(h[[1]],b)
        if (!(length(diff) == 0)) {
            print(paste0("Shared column ",c," of the base R result and the h2o result differ by: ", diff))
        }
        expect_true(length(diff) == 0)
    }
}

test.merge <- function() {
    # real datasets
    x_y_paths <- list(c("smalldata/merge/tourism.csv", "smalldata/merge/heart.csv"),
                      c("smalldata/merge/fertility.nuts.csv", "smalldata/merge/livestock.nuts.csv"),
                      c("smalldata/merge/state.name.abb.csv", "smalldata/merge/state.name.area.csv"),
                      c("smalldata/merge/state.name.abb.csv", "smalldata/merge/state.name.center.csv"),
                      c("smalldata/merge/state.name.abb.csv", "smalldata/merge/state.name.division.csv"),
                      c("smalldata/merge/state.name.abb.csv", "smalldata/merge/state.name.region.csv"),
                      c("smalldata/merge/state.name.abb.csv", "smalldata/merge/state.name.x77.csv"))

    for (xy in x_y_paths) {
        for (ax in c(TRUE, FALSE)) {
            for (ay in c(FALSE)) {   # TODO: implement all.y=TRUE
                print(""); print(paste0("########### all.x=", ax,
                                                  ", all.y=", ay,
                                                  ", x dataset=", xy[[1]],
                                                  ", y dataset=", xy[[2]],
                                                  " ###########")); print("")

                x = h2o.importFile(h2oTest.locate(xy[[1]]), header=TRUE)
                y = h2o.importFile(h2oTest.locate(xy[[2]]), header=TRUE)

                # HACK: convert (common) string columns to factors
                # TODO: should be allowed to merge on string columns
                # HACK2: can't have non-join columns as strings currently, either
                # TODO: should be allowed to have string non-join columns
                # for (c in intersect(names(x), names(y)))
                for (c in names(x))   # TODO: 1:ncol(x) doesn't work here?! What if dup names?
                    if (!is.factor(x[,c]) && is.character(x[,c])) x[,c] = as.factor(x[,c])
                for (c in names(y))
                    if (!is.factor(y[,c]) && is.character(y[,c])) y[,c] = as.factor(y[,c])

                checkMerge(x=x, y=y, all.x=ax, all.y=ay)
            }
        }
    }
}

h2oTest.doTest("Test merge", test.merge)
