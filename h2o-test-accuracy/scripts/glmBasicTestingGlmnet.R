#######################################
#define constant variable
#######################################
# testcase file information
pathFile <- getwd()
testcaseFileName <- paste(getwd(),"../src/test/resources/glmCases.csv",sep="/")

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
datasetPath <-  paste(getwd(),"../../",sep="/")

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
standardize <- 22

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
		return (ds[[columnName]][i])
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

# others function
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

	result <- c()
	for (v in names(contentTrainFile)){
		if(v != input[response_column]){
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

basicTestingGlmnet <- function(input){
	writeLog(paste("run testcase:",input[testcase_id]))
	writeLog(input)

	if(getValueDS(input[train_dataset_id],ds_dir) == "bigdata"){
		datasetFilePath <- paste(datasetPath,"bigdata/laptop/testng/",sep="")
	}
	else{
		datasetFilePath <- paste(datasetPath,"smalldata/testng/",sep="")
	}
	
	trainDatasetFullPath <- paste(datasetFilePath, getValueDS(input[train_dataset_id],ds_file_name), sep="")

	# Set parameter
	cAlpha <- NULL
	if(input[alpha] != ""){
		cAlpha = as.numeric(input[alpha])
	}
	cLambda <- NULL
	if(input[lambda] != ""){
		cLambda = as.numeric(input[lambda])
	}
	cStandardize <- parseBoolean(input[standardize])
	cResponseColumn <- getValueDS(input[train_dataset_id],ds_response_column)

	print(paste("Alpha:",cAlpha))
	print(paste("Lambda:",cLambda))
	print(paste("Standardize:",cStandardize))
	print(paste("ResponseColumn:",cResponseColumn))

	# 1. Get the dataset
	train <- h2o.importFile(trainDatasetFullPath)

	# 2. Convert the dataset into an R object
	trainFrame <- as.data.frame(train)

	# 3. Add intercept column to the dataset (simply a column of zeros). No need to do this in h2o.
	intercept <- rep(0, times = nrow(train))
	trainFrameIntercept <- cbind(trainFrame,intercept)

	# 4. Convert the dataset into an object that glmnet can use
	glmnet <- as.matrix(trainFrameIntercept)

	result = tryCatch({

		# 5. Build the glmnet model
		glmnetModel <- glmnet(x = glmnet, alpha = cAlpha, lambda = cLambda, standardize = cStandardize, y = trainFrame[,cResponseColumn],
				       family = getFamily(input), lower.limits = -0.5, upper.limits = 0.5)

		# 6. Evaluate how good the model is by how well it makes predictions on the validation dataset (Here, we will use
		# the same dataset that we training, on, but you would use the training/validation datasets specified in each test
		# case)
		# We'll compute the MSE (Mean Square Error)
		glmnetPredictions <- predict(glmnetModel,as.matrix(trainFrameIntercept),type="response")
		glmnetMSE <- mean(abs(glmnetPredictions-trainFrame[[cResponseColumn]])**2)

		writeLog(paste(input[testcase_id], "glmnet's MSE: ",glmnetMSE))
		return (glmnetMSE)
		
	}, warning = function(war) {
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
library(glmnet)
library(h2o)
localH2O <- h2o.init()

initOutputFile()
initLogFile()
contentFile  <- file(testcaseFileName, open = "r")

#remove header
outBuffTemp <- readLines(contentFile, n = firstRow)
writeStringToOutput(outBuffTemp)
writeLog("write header into output file")

while (length(oneLine <- readLines(contentFile, n = 1)) > 0) {

	arrLine <- unlist(strsplit(oneLine, ","))
	if(validate(arrLine)){
		arrLine[validate_R_AUC] <- basicTestingGlmnet(arrLine)
	}
	else{
		arrLine[validate_R_AUC] <- "NA"
	}

	writeArrayToOutput(arrLine)

	writeLog(arrLine)
	writeLog(paste("The result of testcase",arrLine[testcase_id],"is run by glmnet:",arrLine[validate_R_AUC]))
	#TODO:break here because I want only run 1 testcase for debug
	#break()

} 
close(contentFile)
h2o.shutdown(localH2O)

