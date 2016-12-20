#'
#' H2O Prediction from R
#'
#' Provides the method h2o.predict with which you can predict a MOJO or POJO Jar model
#' from R.
#'
#' @param model  String with file name of MOJO or POJO Jar
#' @param json  JSON String with inputs to model
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
#' h2o.predict_json(model, json, classpath, javaoptions)
#' @name h2o.predict_json
#' @export
h2o.predict_json <- function(model, json, classpath, javaoptions) {
	java <- "java"
	javapath <- c( c(".", "h2o-genmodel.jar"),  sapply(.libPaths(), paste0, "/h2o/java/h2o.jar", USE.NAMES=FALSE) )
	if (!missing(classpath)) {
	   javapath <- c( classpath, javapath )
	}
	javaopts <- if (!missing(javaoptions)) javaoptions else "-Xmx4g"
	iswindows <- Sys.info()[["sysname"]] == "Windows"
	separator <- if (iswindows) ";" else ":"
	classpath <- paste(javapath, sep="", collapse=separator)
	javaargs <- paste(" ", javaopts, " -cp ", classpath, " water.util.H2OPredictor", sep="")
	jsonq <- if (iswindows) paste('"', json, '"', sep="") else paste("'", json, "'", sep="")
	args <- paste(javaargs, model, jsonq, sep=" ")
	res <- system2(java, args, stdout=TRUE, stderr=TRUE)
	first <- substring(res, 1, 1)
	if (first == '{' || first == '[') {
	   	json <- fromJSON(paste0(res, collapse=""))
		return(json)
	}
	print(res)
}

h2o.to_json <- function(object) {
	return(toJSON(object))
}

h2o.from_json <- function(object) {
	return(fromJSON(object))
}
