library("jsonlite")

h2o.predict_java <- "java"
h2o.predict_javaopts <- "-Xmx4g"
h2o.predict_javapath <- c( c(".", "h2o-genmodel.jar"),  sapply(.libPaths(), paste0, "/h2o/java/h2o.jar", USE.NAMES=FALSE) )

h2o.predict_iswindows <- Sys.info()[["sysname"]] == "Windows"
if (h2o.predict_iswindows) { h2o.predict_separator <- ";" } else { h2o.predict_separator <- ":" }
h2o.predict_classpath <- paste(h2o.predict_javapath, sep="", collapse=h2o.predict_separator)
h2o.predict_javaargs <- paste(" ", h2o.predict_javaopts, " -cp ", h2o.predict_classpath, " water.util.H2OPredictor", sep="")

h2o.predict <- function(model, json) {
	if (h2o.predict_iswindows) { jsonq <- paste('"', json, '"', sep="") } else { jsonq <- paste("'", json, "'", sep="") } 
	args <- paste(h2o.predict_javaargs, model, jsonq, sep=" ")
	res <- system2(h2o.predict_java, args, stdout=TRUE, stderr=TRUE)
	if (substring(res, 1, 1) == '{' || substring(res, 1, 1) == '[') {
	   	json <- fromJSON(paste0(res, collapse=""))
		return(json)
	}
	print(res)
}

