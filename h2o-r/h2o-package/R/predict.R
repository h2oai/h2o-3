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
		res <- paste0("{ \"error\": ", toJSON(res), " }")
	}
    json <- fromJSON(paste0(res, collapse=""))
    return(json)
}

#'
#' H2O Prediction from R without having H2O running
#'
#' Provides the method h2o.mojo_predict_csv with which you can predict a MOJO model from R.
#'
#' @param input_csv_path  Path to input CSV file.
#' @param mojo_zip_path  Path to MOJO zip downloaded from H2O.
#' @param output_csv_path  Optional, path to the output CSV file with computed predictions. If NULL (default), then predictions will be saved as prediction.csv in the same folder as the MOJO zip.
#' @param genmodel_jar_path  Optional, path to genmodel jar file. If NULL (default) then the h2o-genmodel.jar in the same folder as the MOJO zip will be used.
#' @param classpath  Optional, specifies custom user defined classpath which will be used when scoring. If NULL (default) then the default classpath for this MOJO model will be used.
#' @param java_options  Optional, custom user defined options for Java. By default '-Xmx4g -XX:ReservedCodeCacheSize=256m' is used.
#' @param verbose  Optional, if TRUE, then additional debug information will be printed. FALSE by default.
#' @return Returns a data.frame containing computed predictions
#' @export
h2o.mojo_predict_csv <- function(input_csv_path, mojo_zip_path, output_csv_path=NULL, genmodel_jar_path=NULL, classpath=NULL, java_options=NULL, verbose=F) {
	default_java_options <- '-Xmx4g -XX:ReservedCodeCacheSize=256m'
	prediction_output_file <- 'prediction.csv'

	if (verbose) {
		cat(sprintf("input_csv:\t%s", input_csv_path), '\n')
	}
	if (!file.exists(input_csv_path)) {
		stop(cat(sprintf("Input csv cannot be found at %s", input_csv_path), '\n'))
	}

	# Ensure mojo_zip exists
	mojo_zip_path <- normalizePath(mojo_zip_path)
	if (verbose) {
		cat(sprintf("mojo_zip:\t%s", mojo_zip_path), '\n')
	}
	if (!file.exists((mojo_zip_path))) {
		stop(cat(sprintf("MOJO zip cannot be found at %s", mojo_zip_path), '\n'))
	}

	parent_dir <- dirname(mojo_zip_path)

	# Set output_csv if necessary
	if (is.null(output_csv_path)) {
		output_csv_path <- file.path(parent_dir, prediction_output_file)
	}

	# Set path to h2o-genmodel.jar if necessary and check it's valid
	if (is.null(genmodel_jar_path)) {
		genmodel_jar_path <- file.path(parent_dir, 'h2o-genmodel.jar')
	}
	if (verbose) {
		cat(sprintf("genmodel_jar:\t%s", genmodel_jar_path), '\n')
	}
	if (!file.exists(genmodel_jar_path)) {
		stop(cat(sprintf("Genmodel jar cannot be found at %s", genmodel_jar_path), '\n'))
	}

	if (verbose && !is.null(output_csv_path)) {
		cat(sprintf("output_csv:\t%s", output_csv_path), '\n')
	}

	# Set classpath if necessary
	if (is.null(classpath)) {
		classpath <- genmodel_jar_path
	}
	if (verbose) {
		cat(sprintf("classpath:\t%s", classpath), '\n')
	}

	# Set java_options if necessary
	if (is.null(java_options)) {
		java_options <- default_java_options
	}
	if (verbose) {
		cat(sprintf("java_options:\t%s", java_options), '\n')
	}

	# Construct command to invoke java
	cmd <- c('java')
	java_options_list <- strsplit(java_options, ' ')
	for (i in 1:length(java_options_list)) {
		cmd <- c(cmd, java_options_list[[i]])
	}
	cmd <- c(cmd, "-cp", classpath, 'hex.genmodel.tools.PredictCsv', "--mojo", mojo_zip_path, "--input", input_csv_path,
	'--output', output_csv_path, '--decimal')
	cmd_str <- paste(cmd, collapse=' ')
	if (verbose) {
		cat(sprintf("java cmd:\t%s", cmd_str), '\n')
	}

	# invoke the command
	res <- system(cmd_str)
	if (res != 0) {
		msg <- sprintf("SYSTEM COMMAND FAILED (exit status %d)", res)
		stop(msg)
	}

	# load predictions
	result <- read.csv(output_csv_path)
	return(result)
}

#'
#' H2O Prediction from R without having H2O running
#'
#' Provides the method h2o.mojo_predict_df with which you can predict a MOJO model from R.
#'
#' @param frame  data.frame to score.
#' @param mojo_zip_path  Path to MOJO zip downloaded from H2O.
#' @param genmodel_jar_path  Optional, path to genmodel jar file. If NULL (default) then the h2o-genmodel.jar in the same folder as the MOJO zip will be used.
#' @param classpath  Optional, specifies custom user defined classpath which will be used when scoring. If NULL (default) then the default classpath for this MOJO model will be used.
#' @param java_options  Optional, custom user defined options for Java. By default '-Xmx4g -XX:ReservedCodeCacheSize=256m' is used.
#' @param verbose  Optional, if TRUE, then additional debug information will be printed. FALSE by default.
#' @return Returns a data.frame containing computed predictions
#' @export
h2o.mojo_predict_df <- function(frame, mojo_zip_path, genmodel_jar_path=NULL, classpath=NULL, java_options=NULL, verbose=F) {
	tmp_dir <- tempdir()
	dir.create(tmp_dir)
	tryCatch(
		{
			input_csv_path <- file.path(tmp_dir, 'input.csv')
			prediction_csv_path <- file.path(tmp_dir, 'prediction.csv')
			write.csv(frame, file=input_csv_path, row.names=F)
			return(h2o.mojo_predict_csv(input_csv_path=input_csv_path, mojo_zip_path=mojo_zip_path, output_csv_path=prediction_csv_path, genmodel_jar_path=genmodel_jar_path, classpath=classpath, java_options=java_options, verbose=verbose))
		},
		finally={
			unlink(tmp_dir, recursive=T)
		}
	)
}

# These are defined so that you can use the same names in Python and allows us to change the backing method

h2o.to_json <- function(object) {
	return(toJSON(object))
}

h2o.from_json <- function(object) {
	return(fromJSON(object))
}
