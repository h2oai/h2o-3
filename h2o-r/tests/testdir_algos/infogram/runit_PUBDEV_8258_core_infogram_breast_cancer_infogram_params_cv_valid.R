setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

# test infogram works with validation dataset and cross-validation
infogramBCVCV <- function() {
    bhexFV <- h2o.importFile(locate("smalldata/admissibleml_test/wdbc_changed.csv"))
    bhexFV["diagnosis"]<- h2o.asfactor(bhexFV["diagnosis"])
    Y <- "diagnosis"
    X <- c("radius_mean", "texture_mean", "perimeter_mean", "area_mean",
           "smoothness_mean", "compactness_mean", "concavity_mean", "concave_points_mean", "symmetry_mean",
           "fractal_dimension_mean", "radius_se", "texture_se", "perimeter_se", "area_se", "smoothness_se",
           "compactness_se", "concavity_se", "concave_points_se", "symmetry_se", "fractal_dimension_se",
           "radius_worst", "texture_worst", "perimeter_worst", "area_worst", "smoothness_worst", "compactness_worst",
           "concavity_worst", "concave_points_worst", "symmetry_worst", "fractal_dimension_worst")

    Log.info("Building the model")
    split = h2o.splitFrame(data=bhexFV,ratios=.8)
    train = h2o.assign(split[[1]],key="train")
    test = h2o.assign(split[[2]],key="test")

    infogramModel <- h2o.infogram(y=Y, x=X, training_frame=train,  seed=12345, top_n_features=50) # model with training dataset
    infogramModelV <- h2o.infogram(y=Y, x=X, training_frame=train,  validation_frame=test, seed=12345, top_n_features=50) # model with training, validation datasets
    infogramModelCV <- h2o.infogram(y=Y, x=X, training_frame=train,  nfolds=2, seed=12345, top_n_features=50) # model with training, CV
    infogramModelVCV <- h2o.infogram(y=Y, x=X, training_frame=train,  validation_frame=test, nfolds=2, seed=12345, top_n_features=50) # model with training, validation datasets and CV
    Log.info("comparing infogram info from training dataset")
    relCMITrain <- infogramModel@admissible_score
    relCMITrainV <- infogramModelV@admissible_score
    relCMITrainCV <- infogramModelCV@admissible_score
    relCMITrainVCV <- infogramModelVCV@admissible_score
    compareFrames(relCMITrain, relCMITrainV, prob=1.0)
    compareFrames( relCMITrainCV, relCMITrainVCV, prob=1.0)
    compareFrames( relCMITrain, relCMITrainVCV, prob=1.0)
    
    Log.info("comparing infogram info from validation dataset")
    relCMIValidV <- infogramModelV@admissible_score_valid
    relCMIValidVCV <- infogramModelVCV@admissible_score_valid
    compareFrames(relCMIValidV, relCMIValidVCV, prob=1.0)
    
    Log.info("comparing infogram info from cross-validation hold out")
    relCMICVCV <- infogramModelCV@admissible_score_xval
    relCMICVVCV <- infogramModelVCV@admissible_score_xval
    compareFrames(relCMICVCV, relCMICVVCV, prob=1.0)
}

doTest("Infogram: Breast cancer core infogram with validation dataset, cross-validation", infogramBCVCV)
