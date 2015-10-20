library(h2o)
h2o.init()

## Find and import data into H2O
locate <- h2o:::.h2o.locate
pathToACSNames  <- locate("bigdata/laptop/census/ACS_13_5YR_DP02_colnames.csv")
pathToACSData   <- locate("bigdata/laptop/census/ACS_13_5YR_DP02_cleaned.zip")
pathToWHDData   <- locate("bigdata/laptop/census/whd_zcta_cleaned.zip")

print("Importing ACS 2013 5-year DP02 demographic dataset into H2O...")
acs_names <- h2o.uploadFile(pathToACSNames)
acs_orig <- h2o.uploadFile(pathToACSData, col.types = c("enum", rep("numeric", 149)), col.names = acs_names)

## Save and drop zip code column from training frame
acs_zcta_col <- acs_orig$ZCTA5
acs_full <- acs_orig[,-which(colnames(acs_orig) == "ZCTA5")]

## Grab a summary of ACS frame
dim(acs_full)
summary(acs_full)

print("Importing WHD 2014-2015 labor violations dataset into H2O...")
whd_zcta <- h2o.uploadFile(pathToWHDData, col.types = c(rep("enum", 7), rep("numeric", 97)))

## Grab a summary of WHD frame
dim(whd_zcta)
summary(whd_zcta)

print("Run GLRM to reduce ZCTA demographics to k = 10 archetypes")
acs_model <- h2o.glrm(training_frame = acs_full, k = 10, transform = "STANDARDIZE", 
                      loss = "Quadratic", regularization_x = "Quadratic", 
                      regularization_y = "L1", max_iterations = 100, gamma_x = 0.25, gamma_y = 0.5)
acs_model

print("Plot objective function value each iteration")
acs_model.score <- acs_model@model$scoring_history
plot(acs_model.score$iteration, acs_model.score$objective, xlab = "Iteration", ylab = "Objective", main = "Objective Function Value per Iteration")

## Embedding of ZCTAs into archetypes (X)
zcta_arch_x <- h2o.getFrame(acs_model@model$representation_name)
head(zcta_arch_x)

## Archetype to full feature mapping (Y)
arch_feat_y <- acs_model@model$archetypes
arch_feat_y

## Split WHD data into test/train with 20/80 ratio
split <- h2o.runif(whd_zcta)
train <- whd_zcta[split <= 0.8,]
test  <- whd_zcta[split > 0.8,]

print("Build a DL model on original WHD data to predict repeat violators")
myY <- "flsa_repeat_violator"
myX <- setdiff(5:ncol(train), which(colnames(train) == myY))
orig_time <- system.time(dl_orig <- h2o.deeplearning(x = myX, y = myY, training_frame = train, epochs = 0.1,
                                                     validation_frame = test, distribution = "multinomial"))

print("Replace ZCTA5 column in WHD data with GLRM archetypes")
zcta_arch_x$zcta5_cd <- acs_zcta_col
whd_arch <- h2o.merge(whd_zcta, zcta_arch_x, all.x = TRUE, all.y = FALSE)
whd_arch$zcta5_cd <- NULL
summary(whd_arch)

## Split modified WHD data into test/train with 20/80 ratio
train_mod <- whd_arch[split <= 0.8,]
test_mod  <- whd_arch[split > 0.8,]

print("Build a DL model on modified WHD data to predict repeat violators")
myX <- setdiff(5:ncol(train_mod), which(colnames(train_mod) == myY))
mod_time <- system.time(dl_mod <- h2o.deeplearning(x = myX, y = myY, training_frame = train_mod, epochs = 0.1,
                                                   validation_frame = test_mod, distribution = "multinomial"))

print("Performance comparison:")
data.frame(original = c(orig_time[3], h2o.mse(dl_orig, train = TRUE), h2o.mse(dl_orig, valid = TRUE)),
           reduced  = c(mod_time[3], h2o.mse(dl_mod, train = TRUE), h2o.mse(dl_mod, valid = TRUE)),
           row.names = c("runtime", "train_mse", "test_mse"))
