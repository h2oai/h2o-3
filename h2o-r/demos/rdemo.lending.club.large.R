library(h2o)
h2o.init()

# Set this to True if you want to fetch the data directly from S3.
# This is useful if your cluster is running in EC2.
data_source_is_s3 <- FALSE

locate_source <- function(s) {
  if (data_source_is_s3)
    myPath <- paste0("s3n://h2o-public-test-data/", s)
  else
    myPath <- h2o:::.h2o.locate(s)
}

plot_scoring <- function(model) {
  sh <- h2o.scoreHistory(object = model)
  par(mfrow=c(1,2))

  if(model@algorithm == "gbm" | model@algorithm == "drf"){
    min <- min(range(sh$training_MSE), range(sh$validation_MSE))
    max <- max(range(sh$training_MSE), range(sh$validation_MSE))
    plot(x = sh$number_of_trees, y = sh$validation_MSE, col = "orange", main = model@model_id, ylim = c(min,max))
    points(x = sh$number_of_trees, y = sh$training_MSE, col = "blue")
    min <- min(range(sh$training_AUC), range(sh$validation_AUC))
    max <- max(range(sh$training_AUC), range(sh$validation_AUC))
    plot(x = sh$number_of_trees, y = sh$validation_AUC, col = "orange", main = model@model_id, ylim = c(min,max))
    points(x = sh$number_of_trees, y = sh$training_AUC, col = "blue")
    return(data.frame(number_of_trees = sh$number_of_trees, validation_auc = sh$validation_AUC, validation_mse = sh$validation_MSE))
  }
  if(model@algorithm == "deeplearning"){
    plot(x = sh$epochs, y = sh$validation_MSE, col = "orange", main = model@model_id)
    plot(x = sh$epochs, y = sh$validation_AUC, col = "orange", main = model@model_id)
  }
}

# Pick either the big or the small demo.
small_test <-  locate_source("bigdata/laptop/lending-club/LoanStats3a.csv")
big_test <-  c(locate_source("bigdata/laptop/lending-club/LoanStats3a.csv"),
             locate_source("bigdata/laptop/lending-club/LoanStats3b.csv"),
             locate_source("bigdata/laptop/lending-club/LoanStats3c.csv"),
             locate_source("bigdata/laptop/lending-club/LoanStats3d.csv"))

print("Import approved loan requests for Lending Club...")
loanStats <- h2o.importFile(path = big_test, parse = F)
col_types <- c('numeric', 'numeric', 'numeric', 'numeric', 'numeric', 'enum', 'string', 'numeric',
               'enum', 'enum', 'enum', 'string', 'enum', 'numeric', 'enum', 'enum', 'enum', 'enum',
               'string', 'enum', 'enum', 'enum', 'enum', 'enum', 'numeric', 'numeric', 'enum',
               'numeric', 'numeric', 'numeric', 'numeric', 'numeric', 'numeric', 'string', 'numeric',
               'enum', 'numeric', 'numeric', 'numeric', 'numeric', 'numeric', 'numeric', 'numeric',
               'numeric', 'numeric', 'enum', 'numeric', 'enum', 'enum', 'numeric', 'enum', 'numeric')
loanStats <- h2o.parseRaw(data = loanStats, destination_frame = "loanStats", col.types = col_types)

print("Create bad loan label, this will include charged off, defaulted, and late repayments on loans...")
loanStats <- loanStats[!(loanStats$loan_status %in% c("Current", "In Grace Period", "Late (16-30 days)", "Late (31-120 days)")), ]
loanStats <- loanStats[!is.na(loanStats$id),]
loanStats$bad_loan <- loanStats$loan_status %in% c("Charged Off", "Default", "Does not meet the credit policy.  Status:Charged Off")
loanStats$bad_loan <- as.factor(loanStats$bad_loan)
print(paste(nrow(loanStats), "of 550573 loans have either been paid off or defaulted..."))

print("Turn string interest rate and revoling util columns into numeric columns...")
loanStats$int_rate <- h2o.strsplit(loanStats$int_rate, split = "%")
loanStats$int_rate <- h2o.trim(loanStats$int_rate)
loanStats$int_rate <- as.h2o(as.numeric(as.matrix(loanStats$int_rate)))
# loanStats$int_rate <- as.numeric(loanStats$int_rate)
loanStats$revol_util <- h2o.strsplit(loanStats$revol_util, split = "%")
loanStats$revol_util <- h2o.trim(loanStats$revol_util)
loanStats$revol_util <- as.h2o(as.numeric(as.matrix(loanStats$revol_util)))
# loanStats$revol_util <- as.numeric(loanStats$revol_util)

print("Calculate the longest credit length in years...")
time1 <- as.Date(h2o.strsplit(x = loanStats$earliest_cr_line, split = "-")[,2], format = "%Y") 
time2 <- as.Date(h2o.strsplit(x = loanStats$issue_d, split = "-")[,2], format = "%Y")
loanStats$credit_length_in_years <- year(time2) - year(time1)
## Ideally you can parse the column as a Date column immediately
## loanStats$earliest_cr_line <- as.Date(x = loanStats$earliest_cr_line, format = "%b-%Y")
## loanStats$issue_d          <- as.Date(x = loanStats$issue_d, format = "%b-%Y")
## loanStats$credit_length_in_years <- year(loanStats$earliest_cr_line) - year(loanStats$issue_d)


print("Convert emp_length column into numeric...")
## remove " year" and " years", also translate n/a to ""
loanStats$emp_length <- h2o.sub(x = loanStats$emp_length, pattern = "([ ]*+[a-zA-Z].*)|(n/a)", replacement = "")
loanStats$emp_length <- h2o.trim(loanStats$emp_length)
loanStats$emp_length <- h2o.sub(x = loanStats$emp_length, pattern = "< 1", replacement = "0")
loanStats$emp_length <- h2o.sub(x = loanStats$emp_length, pattern = "10\\+", replacement = "10")
loanStats$emp_length <- as.h2o(as.numeric(as.matrix(loanStats$emp_length)))
# loanStats$emp_length <- as.numeric(loanStats$emp_length)

print("Map multiple levels into one factor level for verification_status...")
loanStats$verification_status <- h2o.sub(x = loanStats$verification_status, pattern = "VERIFIED - income source", replacement = "verified")
loanStats$verification_status <- h2o.sub(x = loanStats$verification_status, pattern = "VERIFIED - income", replacement = "verified")
loanStats$verification_status <- as.h2o(as.matrix(loanStats$verification_status))
#h2o.setLevels(x = loanStats$verification_status, levels = c("not verified", "verified", ""))

## Check to make sure all the string/enum to numeric conversion completed correctly
x <- c("int_rate", "revol_util", "credit_length_in_years", "emp_length", "verification_status")
c1 <- as.data.frame(loanStats[1,x])
c2 <- data.frame(int_rate = 10.65, revol_util = 83.7, credit_length_in_years = 26,
                 emp_length = 10, verification_status = "verified")
if(!all(c1 == c2)) {
  print(c1)
  print(c2)
  stop("Conversion column(s) did not run correctly.")
  }

print("Calculate the total amount of money earned or lost per loan...")
loanStats$earned <- loanStats$total_pymnt - loanStats$loan_amnt

print("Set variables to predict bad loans...")
myY <- "bad_loan"
myX <-  c("loan_amnt", "term", "home_ownership", "annual_inc", "verification_status", "purpose",
          "addr_state", "dti", "delinq_2yrs", "open_acc", "pub_rec", "revol_bal", "total_acc",
          "emp_length", "collections_12_mths_ex_med", "credit_length_in_years", "inq_last_6mths", "revol_util")

loanStats$inq_last_6mths <- as.factor(loanStats$inq_last_6mths)
loanStats$collections_12_mths_ex_med <- as.factor(loanStats$collections_12_mths_ex_med)
loanStats$pub_rec <- as.factor(loanStats$pub_rec)

data  <- loanStats
rand  <- h2o.runif(data)
train <- data[rand$rnd <= 0.8, ]
valid <- data[rand$rnd > 0.8, ]

models <- c()
for(i in 4:5){
start     <- Sys.time()
gbm_model <- h2o.gbm(x = myX, y = myY, training_frame = train, validation_frame = valid, balance_classes = T,
                     learn_rate = 0.05, score_each_iteration = T, ntrees = 100, max_depth = i)
end       <- Sys.time()
gbmBuild  <- end - start
print(paste("Took", gbmBuild, units(gbmBuild), "to build a GBM Model with 100 trees and a AUC of :",
            h2o.auc(gbm_model) , "on the training set and",
            h2o.auc(gbm_model, valid = T), "on the validation set."))
gbm_score <- plot_scoring(model = gbm_model)
models <- c(models, gbm_model)
}

##### Validate Results
max_auc_on_valid <- c()
for(model in models) {
sh <- h2o.scoreHistory(model)
best_model <- sh[sh$validation_AUC == max(sh$validation_AUC),]
max_auc_on_valid <- rbind(max_auc_on_valid, best_model)
}

best_model = which(max_auc_on_valid$validation_AUC == max(max_auc_on_valid$validation_AUC))
gbm_model = models [[best_model]]

print("The variable importance for the GBM model...")
print(h2o.varimp(gbm_model))
print("The confusion matrix for the GBM model...")
print(h2o.confusionMatrix(gbm_model, valid = T))
h2o.auc(gbm_model)
h2o.auc(gbm_model, valid = T)

## Do a post - analysis of how much money we would've saved with this model...
printMoney <- function(x){
  x <- round(abs(x),2)
  format(x, digits=10, nsmall=2, decimal.mark=".", big.mark=",")
  }

## Calculate how much money will be lost to false negative, vs how much will be saved due to true positives
loanStats$pred <- h2o.predict(gbm_model, loanStats)[,1]
net <- as.data.frame(h2o.group_by(data = loanStats, by = c("bad_loan", "pred"), sum("earned")))
n1  <- net[ net$bad_loan == 0 & net$pred == 0, 3]
n2  <- net[ net$bad_loan == 0 & net$pred == 1, 3]
n3  <- net[ net$bad_loan == 1 & net$pred == 1, 3]
n4  <- net[ net$bad_loan == 1 & net$pred == 0, 3]

## Calculate the amount of earned
print(paste0("Total amount of profit still earned using the model : $", printMoney(n1) , ""))
print(paste0("Total amount of profit forfeitted using the model : $", printMoney(n2) , ""))
print(paste0("Total amount of loss that could have been prevented : $", printMoney(n3) , ""))
print(paste0("Total amount of loss that still would've accrued : $", printMoney(n4) , ""))

## Value of the GBM Model
diff <- n3 + n2
print(paste0("Total immediate gain the implementation of the model would've had on completed approved loans : $",printMoney(diff),""))

## Run prediction of two similar applicants
a1 <- as.h2o(data.frame(loan_amnt = 25000, term = "36 months", home_ownership = "RENT", annual_inc = 70000, purpose = "credit card"))
a2 <- as.h2o(data.frame(loan_amnt = 25000, term = "36 months", home_ownership = "RENT", annual_inc = 70000, purpose = "medical"))

p1 <- h2o.predict(object = gbm_model, newdata = a1)
p2 <- h2o.predict(object = gbm_model, newdata = a2)

if(sum(p1$predict == 1)) stop("Loan for credit card debt should be approved")
if(sum(p2$predict == 0)) stop("Loan for medical bills should not be approved")

