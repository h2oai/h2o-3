setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

test.pubdev_8218 = function(){
    # 1. check that interaction term does not create incorrect values anymore:
    df = data.frame(
    v1 = c('Y', 'Y', 'Y', 'N', 'N'),
    v2 = c('S', 'S', 'S', 'A', 'A'),
    v3 = c('E1', 'E1', 'E1', 'B1', 'B1'),
    stringsAsFactors = TRUE
    )
    df.hex = as.h2o(df, 'dfhex')
    interaction = h2o.interaction(
    data = df.hex,
    destination_frame = 'interaction',
    factors =  c('v2','v3', 'v1'),
    pairwise = F,
    max_factors = 1000,
    min_occurrence = 1
    )
    combined = h2o.cbind(df.hex, interaction)
    print(combined)
    expect_true(interaction[1,1] == "S_E1_Y")
    expect_true(interaction[2,1] == "S_E1_Y")
    expect_true(interaction[3,1] == "S_E1_Y")
    expect_true(interaction[4,1] == "A_B1_N")
    expect_true(interaction[5,1] == "A_B1_N")
    
    
    # 2. check that there is no error during interaction creation for data with more that 5 rows anymore:
    df = data.frame(
    v1 = c('Y', 'Y', 'Y', 'N', 'N', 'Y'),
    v2 = c('S', 'S', 'S', 'A', 'A', 'N'),
    v3 = c('E1', 'E1', 'E1', 'B1', 'B1', 'B1'),
    stringsAsFactors = TRUE
    )
    df.hex = as.h2o(df, 'dfhex')
    interaction = h2o.interaction(
    data = df.hex,
    destination_frame = 'interaction',
    factors =  c('v2','v3', 'v1'),
    pairwise = F,
    max_factors = 1000,
    min_occurrence = 1
    )
    combined = h2o.cbind(df.hex, interaction)
    print(combined)
    expect_true(interaction[1,1] == "S_E1_Y")
    expect_true(interaction[2,1] == "S_E1_Y")
    expect_true(interaction[3,1] == "S_E1_Y")
    expect_true(interaction[4,1] == "A_B1_N")
    expect_true(interaction[5,1] == "A_B1_N")
    expect_true(interaction[6,1] == "N_B1_Y")
    
}

doTest("h2o.interaction() does not output the desired outcomes", test.pubdev_8218)
