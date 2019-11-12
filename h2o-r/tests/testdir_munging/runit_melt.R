setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.melt <- function(){
    df <- h2o.createFrame(
            rows = 1000, cols=3, factors=10,
            integer_fraction=1.0/3,
            categorical_fraction=1.0/3,
            missing_fraction=0.0,
            seed=123
         )
    df$ID <- as.h2o(seq(1000))

    print(df)
    
    df_pivoted <- h2o.pivot(df, index="ID", column="C2", value="C1")
    
    print(df_pivoted)
    
    df_unpivoted <- h2o.melt(df_pivoted, id_vars="ID",
                             var_name="C2",
                             value_name="C1", skipna=TRUE)

    df_unpivoted_loc <- as.data.frame(df_unpivoted)
    df_loc <- as.data.frame(df)
    
    print(df_loc)
    print(df_unpivoted_loc)
    
    expect_equal(df_loc[, c("ID", "C2", "C1")], df_unpivoted_loc)
}

doTest("Test h2o.melt is an inverse to h2o.pivot", test.melt)
