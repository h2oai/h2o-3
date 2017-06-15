#'
#' H2O Prediction from R without having H2O running
#'
#' Provides the method h2o.predict with which you can predict a MOJO or POJO Jar model
#' from R.
#'
#' @param model  String with file name of MOJO or POJO Jar
#' @param json  JSON String with inputs to model
#' @param genmodelpath  (Optional) path name to h2o-genmodel.jar, if not set defaults to same dir as MOJO
#' @param labels  (Optional) if TRUE then show output labels in result
#' @param classpath  (Optional) Extra items for the class path of where to look for Java classes, e.g., h2o-genmodel.jar
#' @param javaoptions  (Optional) Java options string, default if "-Xmx4g"
#' @return Returns an object with the prediction result
#' @importFrom jsonlite fromJSON
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.predict_json('~/GBM_model_python_1473313897851_6.zip', '{"C7":1}')
#' h2o.predict_json('~/GBM_model_python_1473313897851_6.zip', '{"C7":1}', c(".", "lib"))
#' }
#' @usage
#' h2o.predict_json(model, json, genmodelpath, labels, classpath, javaoptions)
#' @name h2o.predict_json
#' @export
h2o.predict_json <- function(model, json, genmodelpath, labels, classpath, javaoptions) {
	java <- "java"
    # Windows require different Java classpath separator and quoting
    iswindows <- .Platform$OS.type == "windows"
    separator <- if (iswindows) ";" else ":"
	fileseparator <- if (iswindows) "\\" else "/"
	# for now gson lib is the large h2o-genmodel-all.jar lib but should be moved to the small one
	# default to genmodel being in the same dir as mojo
	genmpath <- c(paste0(c(dirname(model), "h2o-genmodel.jar"), collapse=fileseparator), paste0(c(dirname(model), "genmodel.jar")), collapse=fileseparator)
	if (!missing(genmodelpath)) {
		genmpath <- genmodelpath
	}
	javapath <- c(".", genmpath)
	if (!missing(classpath)) {
		# prepend optional path
	   javapath <- c( classpath, javapath )
	}
	showlabels <- if (!missing(labels) && labels == TRUE) "-l" else ""
	javaopts <- if (!missing(javaoptions)) javaoptions else "-Xmx4g"

	jsonq <- if (iswindows) paste('"', json, '"', sep="") else paste("'", json, "'", sep="")
	classpath <- paste0(javapath, collapse=separator)
	javaargs <- paste(" ", javaopts, " -cp ", classpath, " water.util.H2OPredictor", sep="")
	args <- paste(javaargs, showlabels, model, jsonq, sep=" ")
	# run the Java method H2OPredictor, which will return JSON or an error message
	res <- system2(java, args, stdout=TRUE, stderr=TRUE)
	res <- paste0(res, collapse="")
	# check the returned for start of JSON, if json then decode and return, otherwise print the error
	first_char <- substring(res, 1, 1)
	if (first_char == '{' || first_char == '[') {
		# JSON returned -- it must start with { or [
	} else  {
	    # An error message was returned: make json
		res = paste0("{ \"error\": ", toJSON(res), " }")
	}
    json <- fromJSON(paste0(res, collapse=""))
    return(json)
}

# These are defined so that you can use the same names in Python and allows us to change the backing method

h2o.to_json <- function(object) {
	return(toJSON(object))
}

h2o.from_json <- function(object) {
	return(fromJSON(object))
}
