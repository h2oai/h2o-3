setClass("FeatureDataSet",
         slots=c(
               dataSetId="integer",
               uri="character",
               key="character"
               )
)

FeatureDataSet<-
function(dataSetId, uri) {
    if (missing(dataSetId)) {
        stop("Missing `dataSetId`, which is required for proper FeatureDataSet object construction.")
    } else {
        if (!is.integer(dataSetId) || dataSetId < 0) {
            stop("FeatureDataSet slot `dataSetId` must be an integer >= 0.")
        }
    }

    if (missing(uri)) {
        stop("Missing `uri`, which is required for proper FeatureDataSet object construction.")
    } else {
        if (!is.character(uri) || !nzchar(uri)) {
            stop("FeatureDataSet slot `uri` must be a non-empty character vector.")
        }
    }

    new("FeatureDataSet", dataSetId=dataSetId, uri=uri)
}

setClassUnion("FeatureDataSetOrNULL", c("FeatureDataSet", "NULL"))
setClass("FeatureTestCase",
         slots=c(
               id="integer",
               feature="character",
               featureParams="character",
               dataSets="list",
               validationMethod="character",
               validationDataSet="FeatureDataSetOrNULL"
               ),
         prototype=list(validationDataSet=NULL)
)

FeatureTestCase<-
function(id, feature, featureParams, dataSets, validationMethod, validationDataSet) {

    if (missing(id)) {
        stop("Missing `id`, which is required for proper FeatureTestCase object construction.")
    } else {
        if (!is.integer(id) || id < 0) {
            stop("FeatureTestCase slot `id` must be an integer >= 0.")
        }
    }

    if (missing(feature)) {
        stop("Missing `feature`, which is required for proper FeatureTestCase object construction.")
    } else {
        if (!is.character(feature) || !nzchar(feature)) {
            stop("FeatureTestCase slot `feature` must be a non-empty character vector.")
        }
    }

    if (missing(featureParams)) {
        stop("Missing `featureParams`, which is required for proper FeatureTestCase object construction.")
    } else {
        if (!is.character(featureParams)) {
            stop("FeatureTestCase slot `featureParams` must be a character vector.")
        }
    }

    if (missing(dataSets)) {
        stop("Missing `dataSets`, which is required for proper FeatureTestCase object construction.")
    } else {
        if (!is.list(dataSets)) {
            stop("`dataSets` FeatureTestCase slot must be a list.")
        }
    }

    if (missing(validationMethod)) {
        stop("Missing `validationMethod`, which is required for proper FeatureTestCase object construction.")
    } else {
        if (!is.character(validationMethod) || !(validationMethod == "R" || validationMethod == "H" ||
                                                 validationMethod == "O")) {
            stop("FeatureTestCase slot `validationMethod` must be a 'R', 'H', or 'O'.")

            if (validationMethod == "R") {
                if (missing(validationDataSet)) {
                    stop("Missing `validationDataSet`, which is required for validationMethod 'R'.")
                }

                if (!typeof(validationDataSet) == "FeatureDataSet") {
                    stop("FeatureTestCase slot `validationDataSet` must be a FeatureDataSet for validationMethod 'R'.")
                }
            } else if (validationMethod == "O") {
                if (!(typeof(validationDataSet) == "NULL")) {
                    stop("FeatureTestCase slot `validationDataSet` must be NULL for validationMethod 'O'.")
                }
            }
        }
    }

    return(new("FeatureTestCase", id=id, feature=feature, featureParams=featureParams, dataSets=dataSets,
                                  validationMethod=validationMethod, validationDataSet=validationDataSet))
}