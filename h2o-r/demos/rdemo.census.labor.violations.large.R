library(h2o)
h2o.init(nthreads = -1, max_mem_size = "2G")

## Find and import data into H2O
locate <- h2o:::.h2o.locate
pathToACSData <- locate("bigdata/laptop/census/ACS_13_5YR_DP02_cleaned.zip")
pathToWHDData <- locate("bigdata/laptop/census/whd_zcta_cleaned.zip")

print("Importing ACS 2013 5-year DP02 demographic dataset into H2O...")
acs_orig <- h2o.importFile(pathToACSData, col.types = c("enum", rep("numeric", 149)))

## Save and drop zip code column from training frame
acs_zcta_col <- acs_orig$ZCTA5
acs_full <- acs_orig[,-which(colnames(acs_orig) == "ZCTA5")]

## Grab a summary of ACS frame
dim(acs_full)
summary(acs_full)

print("Importing WHD 2014-2015 labor violations dataset into H2O...")
whd_zcta <- h2o.importFile(pathToWHDData, col.types = c(rep("enum", 7), rep("numeric", 97)))

## Grab a summary of WHD frame
dim(whd_zcta)
summary(whd_zcta)

print("Run GLRM to reduce ZCTA demographics to k = 10 archetypes")
acs_model <- h2o.glrm(training_frame = acs_full, k = 10, transform = "STANDARDIZE", 
                      loss = "Quadratic", regularization_x = "Quadratic", 
                      regularization_y = "L1", max_iterations = 100, gamma_x = 0.25, gamma_y = 0.5)
acs_model

print("Plot objective function value each iteration")
plot(acs_model)

## Embedding of ZCTAs into archetypes (X)
zcta_arch_x <- h2o.getFrame(acs_model@model$representation_name)
head(zcta_arch_x)

print("Plot a few ZCTAs on the first two archetypes")
idx <- ((acs_zcta_col == "10065") |   # Manhattan, NY (Upper East Side)
        (acs_zcta_col == "11219") |   # Manhattan, NY (East Harlem)
        (acs_zcta_col == "66753") |   # McCune, KS
        (acs_zcta_col == "84104") |   # Salt Lake City, UT
        (acs_zcta_col == "94086") |   # Sunnyvale, CA
        (acs_zcta_col == "95014"))    # Cupertino, CA

city_arch <- as.data.frame(zcta_arch_x[idx,1:2])
xeps <- (max(city_arch[,1]) - min(city_arch[,1])) / 10
yeps <- (max(city_arch[,2]) - min(city_arch[,2])) / 10
xlims <- c(min(city_arch[,1]) - xeps, max(city_arch[,1]) + xeps)
ylims <- c(min(city_arch[,2]) - yeps, max(city_arch[,2]) + yeps)
plot(city_arch[,1], city_arch[,2], xlim = xlims, ylim = ylims, xlab = "First Archetype", ylab = "Second Archetype", main = "Archetype Representation of Zip Code Tabulation Areas")
text(city_arch[,1], city_arch[,2], labels = c("Upper East Side", "East Harlem", "McCune", "Salt Lake City", "Sunnyvale", "Cupertino"), pos = 1)

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
orig_time <- system.time(dl_orig <- h2o.deeplearning(x = myX, y = myY, training_frame = train, 
                                                     validation_frame = test, distribution = "multinomial",
                                                     epochs = 0.1, hidden = c(50,50,50)))

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
mod_time <- system.time(dl_mod <- h2o.deeplearning(x = myX, y = myY, training_frame = train_mod, 
                                                   validation_frame = test_mod, distribution = "multinomial",
                                                   epochs = 0.1, hidden = c(50,50,50)))

print("Replace ZCTA5 column in WHD data with all ACS data")
colnames(acs_orig)[1] <- "zcta5_cd"
whd_acs <- h2o.merge(whd_zcta, acs_orig, all.x = TRUE, all.y = FALSE)
whd_acs$zcta5_cd <- NULL
summary(whd_acs)

## Split combined WHD-ACS data into test/train with 20/80 ratio
train_comb <- whd_acs[split <= 0.8,]
test_comb <- whd_acs[split > 0.8,]

print("Build a DL model on combined WHD-ACS data to predict repeat violators")
myX <- setdiff(5:ncol(train_comb), which(colnames(train_comb) == myY))
comb_time <- system.time(dl_comb <- h2o.deeplearning(x = myX, y = myY, training_frame = train_comb,
                                                     validation_frame = test_comb, distribution = "multinomial",
                                                     epochs = 0.1, hidden = c(50,50,50)))

print("Performance comparison:")
data.frame(original = c(orig_time[3], h2o.logloss(dl_orig, train = TRUE), h2o.logloss(dl_orig, valid = TRUE)),
           reduced  = c(mod_time[3], h2o.logloss(dl_mod, train = TRUE), h2o.logloss(dl_mod, valid = TRUE)),
           combined = c(comb_time[3], h2o.logloss(dl_comb, train = TRUE), h2o.logloss(dl_comb, valid = TRUE)),
           row.names = c("runtime", "train_logloss", "test_logloss"))
