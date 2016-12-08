#'
#' H2O Prediction from R
#'
#' Provides the method h2o.predict with which you can predict a MOJO or POJO Jar model
#' from R.
#'



#' @param model  String with file name of MOJO or POJO Jar
#' @param json  JSON String with inputs to model
#' @param classpath  (Optional) Extra items for the class path of where to look for Java classes, e.g., h2o-genmodel.jar
#' @return Returns an object with the prediction result
#' @importFrom jsonlite fromJSON
#' @examples
#' \donttest{
#' library(h2o)
#' h2o.predict_json('~/GBM_model_python_1473313897851_6.zip', '{"C7":1}')
#' h2o.predict_json('~/GBM_model_python_1473313897851_6.zip', '{"C7":1}', c(".", "lib"))
#' }
#' @usage
#' h2o.predict_json(model, json, classpath)
#' @name h2o.predict_json

h2o_predict_json_java <- "java"
h2o_predict_json_javaopts <- "-Xmx4g"
h2o_predict_json_javapath <- c( c(".", "h2o-genmodel.jar"),  sapply(.libPaths(), paste0, "/h2o/java/h2o.jar", USE.NAMES=FALSE) )
h2o_predict_json_iswindows <- Sys.info()[["sysname"]] == "Windows"
if (h2o_predict_json_iswindows) { h2o_predict_json_separator <- ";" } else { h2o_predict_json_separator <- ":" }
h2o_predict_json_classpath <- paste(h2o_predict_json_javapath, sep="", collapse=h2o_predict_json_separator)
h2o_predict_json_javaargs <- paste(" ", h2o_predict_json_javaopts, " -cp ", h2o_predict_json_classpath, " water.util.H2OPredictor", sep="")

#' @name h2o.predict_json
#' @export
h2o.predict_json <- function(model, json, classpath) {
	if (!missing(classpath)) {
	   h2o_predict_json_javapath <- c( classpath, h2o_predict_json_javapath )
	   h2o_predict_json_classpath <- paste(h2o_predict_json_javapath, sep="", collapse=h2o_predict_json_separator)
	   h2o_predict_json_javaargs <- paste(" ", h2o_predict_json_javaopts, " -cp ", h2o_predict_json_classpath, " water.util.H2OPredictor", sep="")
        }
	if (h2o_predict_json_iswindows) { jsonq <- paste('"', json, '"', sep="") } else { jsonq <- paste("'", json, "'", sep="") } 
	args <- paste(h2o_predict_json_javaargs, model, jsonq, sep=" ")
	res <- system2(h2o_predict_json_java, args, stdout=TRUE, stderr=TRUE)
	if (substring(res, 1, 1) == '{' || substring(res, 1, 1) == '[') {
	   	json <- fromJSON(paste0(res, collapse=""))
		return(json)
	}
	print(res)
}
