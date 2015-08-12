#######################################
#define constant variable
#######################################
# testcase file information
pathFile <- getwd()
testcaseFileName <- "../src/test/resources/glmCases.csv"

outputDir <- "output"
outputFileName <- paste(outputDir,"glmnet_glmCases.csv",sep="/")
logFileName <- paste(outputDir,"glmnet_glmCases.log",sep="/")

#data set characteristic information
ds <- read.csv("../src/test/resources/datasetCharacteristics.csv", header = TRUE)
ds_id <- "data_set_id"
ds_dir <- "dataset_directory"
ds_file_name <- "file_name"
ds_response_column <- "target"
ds_column_names <- "column_names"
ds_column_types <- "column_types"

# dataset information
datasetPath <- file.path(getwd(),"../../")

firstRow <- 4

# define index column
testcase_id <- 3
gaussian <- 6
binomial <- 7
poisson <- 8
gamma <- 9
tweedie <- 10
alpha <- 19
lambda <- 20

train_dataset_id <- 44
validate_dataset_id <- 45

validate_R_AUC <- 49

#######################################
#all function
#######################################
# write file
initFile <- function(fileName){
	if(!dir.exists(file.path(getwd(), outputDir))){
		dir.create(file.path(getwd(), outputDir), showWarnings = FALSE)
	}
	cat("",file=fileName)
}

writeLineToFile <- function(aLine, fileName){
	cat(aLine,file=fileName,sep="\n",append=TRUE)
}

# write log file
initLogFile <- function(){
	initFile(logFileName)
}

writeLog <- function(aLine){
	sink(logFileName, append=TRUE)
	print(aLine)
	sink()
	print(aLine)
	#writeLineToFile(aLine, logFileName)
}

# write output file
initOutputFile <- function(){
	initFile(outputFileName)
}

writeStringToOutput <- function(stringLine){
	writeLineToFile(stringLine, outputFileName)
}

writeArrayToOutput <- function(arrayLine){
	writeStringToOutput(paste(arrayLine,collapse=","))
}

#dataset characteristic
getIndexDS <- function(datasetId){
	result <- 0
	for (i in 1:nrow(ds)){
		if(ds[[ds_id]][i] == datasetId){
			result <- i
			break()
		}
	}
	return (result)
}

getValueDS <- function(datasetId, columnName){
	i <- getIndexDS(datasetId)
	if(i != 0){
		return (as.character(ds[[columnName]][i]))
	}
}

validateDS <- function(datasetId){
	i <- getIndexDS(datasetId)
	for (header in names(ds)){
		if(ds[[header]][i] == ""){
			return (FALSE)
		}
	}
	return (TRUE)
}

parseBoolean <- function(value){
	if(is.null(value)){
		return (FALSE)
	}

	lowerValue <- tolower(value)
	if(lowerValue == "x" || lowerValue == "y" || lowerValue == "yes"){
		return (TRUE)
	}

	return (FALSE)
}

getFamily <- function(input){
	result <- ""
	if(parseBoolean(input[gaussian])){
		result <- "gaussian"
	}
	else if(parseBoolean(input[binomial])){
		result <- "binomial"
	}
	else if(parseBoolean(input[poisson])){
		result <- "poisson"
	}
	else if(parseBoolean(input[gamma])){
		result <- "gamma"
	}
	else if(parseBoolean(input[tweedie])){
		result <- "tweedie"
	}

	writeLog(paste("set family: ",result))
	return (result)
}

getBetaConstraintsList <- function(input,trainFileName){
	
	contentTrainFile = read.csv(trainFileName, header = TRUE)
	rsc = getValueDS(input[train_dataset_id],ds_dir)

	result <- c()
	for (v in names(contentTrainFile)){
		if(v != rsc){
			result <- c(result, v)
		}
	}
	
	writeLog("set beta constraints")
	writeLog(paste(result, collapse=","))
	return (result)
}

validate <- function(input){
	writeLog(paste("validate testcase:",input[testcase_id]))

	if(input[train_dataset_id] == ""){
		writeLog("dataset file is empty")
		return (FALSE)
	}
	if(!validateDS(input[train_dataset_id])){

		writeLog("dataset is invalid")
		return (FALSE)
	}
	#TODO: validate AUTO SET parameters

	writeLog("validate successful")
	return (TRUE)
}

# you have to install package h2o 2.8.6.2 before run this function
basicTestingH2O2 <- function(input){
	writeLog(paste("run testcase:",input[testcase_id]))
	writeLog(input)

	if(getValueDS(input[train_dataset_id],ds_dir) == "bigdata"){
		datasetFilePath <- paste(datasetPath,"bigdata/laptop/testng/",sep="")
	}
	else{
		datasetFilePath <- paste(datasetPath,"smalldata/testng/",sep="")
	}
	
	trainDatasetFullPath <- paste(datasetFilePath, getValueDS(input[train_dataset_id],ds_file_name), sep="")

	# Create beta constraints frame
	myX <-  getBetaConstraintsList(input,trainDatasetFullPath)
	lowerbound <- rep(-0.5, times = length(myX))
	upperbound <- rep(0.5, times = length(myX))
	beta_constraints <- data.frame(names = myX, lower_bounds = lowerbound, upper_bounds = upperbound)

	train <- h2o.importFile(trainDatasetFullPath)

	# Set parameter
	cAlpha <- NULL
	if(input[alpha] != ""){
		cAlpha = as.numeric(input[alpha])
	}
	cLambda <- NULL
	if(input[lambda] != ""){
		cLambda = as.numeric(input[lambda])
	}

	result <- tryCatch({
		# Build the h2o model
		writeLog(paste("build model with testcase:",input[testcase_id]))

		h2o_model <- h2o.glm(x = myX, y = getValueDS(input[train_dataset_id],ds_response_column), training_frame = train, family = getFamily(input),
					 alpha = cAlpha, lambda = cLambda, beta_constraints = beta_constraints)

		# Compute the h2o model's MSE
		h2o_mse <- h2o.mse(h2o_model)

		# Finally, let's look at glmnet's and h2o's results.
		writeLog(paste(input[testcase_id], "h2o's MSE: ",h2o_mse))

		return (h2o_mse)
		
	}, warning <- function(war) {
		#writeLog(war)
		writeLog("WARNING")
		writeLog(war)
		return ("warning")
	}, error = function(err) {
		#writeLog(err)
		writeLog("ERROR")
		writeLog(err)
		return ("error")
	}, finally = {
		writeLog(paste("end run testcase:",input[testcase_id]))
	})
	
}


#######################################
#main process
#######################################
library(statmod)
library(h2o)
localH2O <- h2o.init()

contentFile  <- file(testcaseFileName, open = "r")
initOutputFile()
initLogFile()


#remove header
outBuffTemp <- readLines(contentFile, n = firstRow)
writeStringToOutput(outBuffTemp)
writeLog("write header into output file")

while (length(oneLine <- readLines(contentFile, n = 1)) > 0) {

	arrLine <- unlist(strsplit(oneLine, ","))
	if(validate(arrLine)){
		arrLine[validate_R_AUC] <- basicTestingH2O2(arrLine)
	}
	else{
		arrLine[validate_R_AUC] <- "NA"
	}

	writeArrayToOutput(arrLine)

	writeLog(paste("The result of testcase",arrLine[testcase_id],"is run by h2o2.8.6.2:",arrLine[validate_R_AUC]))
	#TODO:break here because I want only run 1 testcase for debug
	break()

} 
close(contentFile)
h2o.shutdown(localH2O)

