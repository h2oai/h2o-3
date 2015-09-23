## Set your working directory
setwd("~/Desktop/lending_club/")

## Function for ploting the scoring history
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

clean_up <- function() {
  keys <- h2o.ls()$key
  id   <- grep("subset|nary|group|rapids|file", keys)
  id_i <- grep("modelmetrics", keys)
  h2o.rm(ids = as.character(keys[setdiff(id,id_i)]))
}


run_gbm <- function(data, x, y, model_id) {
  rand  <- h2o.runif(data)
  train <- data[rand$rnd <= 0.8, ]
  valid <- data[rand$rnd > 0.8, ]
  
  gbm_model <- h2o.gbm(x = x, y = y, training_frame = train, validation_frame = valid,
                       score_each_iteration = T,
                       model_id = model_id, ntrees = 200, max_depth = 5)
  return(gbm_model)
}

print("Load H2O library and create connection to H2O...")
library(h2o)
conn <- h2o.init(ip = "localhost", nthreads = -1)

print("Import approved and rejected loan requests from Lending Club...")
locate <- h2o:::.h2o.locate
pathToLoanData <- normalizePath(locate("loanStats"))
loanStats      <- h2o.importFile(path = pathToLoanData, destination_frame = "LoanStats")


print("Create bad loan label, this will include charged off, defaulted, and late repayments on loans...")
loanStats$complete <- !(loanStats$loan_status %in% c("Current", "In Grace Period", "Late (16-30 days)", "Late (31-120 days)"))
loanStats$bad_loan <-   loanStats$loan_status %in% c("Charged Off", "Default", "Does not meet the credit policy.  Status:Charged Off")
loanStats$complete <- as.factor(loanStats$complete)  
loanStats$bad_loan <- as.factor(loanStats$bad_loan)

print("Turn string interest rate column into numeric...")
int_rate.hex <- loanStats$int_rate
int_rate.hex$int_rate <- h2o.strsplit(x = int_rate.hex$int_rate, split = "%")
r_frame   <- as.data.frame(int_rate.hex)
hex_frame <- as.h2o(r_frame)
loanStats$int_rate2 <- hex_frame$int_rate 

earliest_cr_line.hex <- loanStats$earliest_cr_line
earliest_cr_line.hex <- h2o.strsplit(x = earliest_cr_line.hex$earliest_cr_line, split = "-")
r_frame   <- as.data.frame(earliest_cr_line.hex)
hex_frame <- as.h2o(r_frame)
names(hex_frame) <- c("earliest_cr_line_Month", "earliest_cr_line_Year")
loanStats  <- h2o.cbind(loanStats, hex_frame)
loanStats  <- h2o.assign(data = loanStats, key = "LoanStats")

issue_date.hex <- loanStats$issue_d
issue_date.hex <- h2o.strsplit(x = issue_date.hex$issue_d, split = "-")
r_frame   <- as.data.frame(issue_date.hex)
hex_frame <- as.h2o(r_frame)
names(hex_frame) <- c("issue_d_Month", "issue_d_Year")
loanStats  <- h2o.cbind(loanStats, hex_frame)
loanStats  <- h2o.assign(data = loanStats, key = "LoanStats")

revol_util.hex <- loanStats$revol_util
revol_util.hex <- h2o.strsplit(x = revol_util.hex$revol_util, split = "%")
r_frame   <- as.data.frame(revol_util.hex)
hex_frame <- as.h2o(r_frame)
names(hex_frame) <- c("revol_util2")
loanStats  <- h2o.cbind(loanStats, hex_frame)
loanStats  <- h2o.assign(data = loanStats, key = "LoanStats")

emp_length.hex <- loanStats$emp_length
r_frame <- as.data.frame(emp_length.hex)
r_frame <- gsub(pattern = " years", replacement = "", x = r_frame$emp_length)
r_frame <- gsub(pattern = " year", replacement = "", x = r_frame)
r_frame <- gsub(pattern = "< 1", replacement = "0.5", x = r_frame)
# Does not work
# r_frame <- gsub(pattern = "10+", replacement = "10", x = r_frame)
r_frame[which(r_frame == "10+")] = "10"
r_frame[which(r_frame == "n/a")] = NA
r_frame[which(r_frame == "")] = NA
r_frame   <- as.numeric(r_frame)
hex_frame <- as.h2o(r_frame)
names(hex_frame) = "emp_length2"
loanStats  <- h2o.cbind(loanStats, hex_frame)
loanStats  <- h2o.assign(data = loanStats, key = "LoanStats")

loanStats$longest_credit_length <- loanStats$issue_d_Year - loanStats$earliest_cr_line_Year
loanStats$issue_d_Year_factor   <- as.factor(loanStats$issue_d_Year)
# Clean up the KV Store
clean_up()

print("Set variables to predict bad loans...")
myY <- "bad_loan"
myX <- setdiff(names(loanStats), 
              c(myY, "id", "member_id", "loan_status", "url", "policy_code", "last_fico_range_high",
                "last_fico_range_low", "recoveries", "total_rec_prncp", "last_pymnt_amnt",
                "last_credit_pull_d", "collection_recovery_fee", "total_pymnt", "last_pymnt_d",
                "total_rec_int", "funded_amnt_inv", "total_pymnt_inv", "next_pymnt_d",
                "total_rec_late_fee", "out_prncp", "out_prncp_inv", "issue_d", "int_rate","desc",
                "earliest_cr_line", "sub_grade", "grade", "issue_d_Month", "issue_d_Year", 
                "earliest_cr_line_Month", "title", "emp_length", "revol_util", "emp_title",
                "earliest_cr_line_Year", "funded_amnt", "pymnt_plan", "complete", "fico_range_low", "fico_range_high"))

# Filter out only loans that have been completed
loanStats_complete <- loanStats[loanStats$complete == "1", ]
loanStats_complete <- h2o.assign(data = loanStats_complete, key = "loanStats_complete")
loanStats_complete$earned <- loanStats_complete$total_pymnt - loanStats_complete$loan_amnt

data  <- loanStats_complete
rand  <- h2o.runif(data)
train <- data[rand$rnd <= 0.8, ]
valid <- data[rand$rnd > 0.8, ]

models <- c()
for(i in 3:5){
  start     <- Sys.time()
  gbm_model <- h2o.gbm(x = myX, y = myY, training_frame = train, validation_frame = valid, balance_classes = T,
                       learn_rate = 0.05, score_each_iteration = T, ntrees = 200, max_depth = i)
  end       <- Sys.time()
  gbmBuild  <- end - start
  print(paste("Took", gbmBuild, units(gbmBuild), "to build a GBM Model with 200 trees and a AUC of :",
              h2o.auc(gbm_model, valid = T) , "on the validation set and",
              h2o.auc(gbm_model), "on the training set."))
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



## Credit Score of bad vs good completed loans

bad_loans  <- loanStats_complete[loanStats_complete$bad_loan == "1", ]
good_loans <- loanStats_complete[loanStats_complete$bad_loan == "0", ]
bad_loans  <- h2o.assign(data = bad_loans, key = "bad_loans")
good_loans <- h2o.assign(data = good_loans, key = "good_loans")


## Do a post - analysis of how much money we would've saved with this model...
pred <- h2o.predict(gbm_model, loanStats_complete)
loanStats_w_pred <- h2o.cbind(loanStats_complete, pred)
bad_predicted  <- loanStats_w_pred[loanStats_w_pred$predict == "1", ]
good_predicted <- loanStats_w_pred[loanStats_w_pred$predict == "0", ]
bad_predicted  <- h2o.assign(data = bad_predicted, key = "bad_predicted")
good_predicted <- h2o.assign(data = good_predicted, key = "good_predicted")
 
## Calculate how much money will be lost to false negative, vs how much will be saved due to true positives
printMoney <- function(x){
  x <- round(abs(x),2)
  format(x, digits=10, nsmall=2, decimal.mark=".", big.mark=",")
}
sum_loss <- as.data.frame(h2o.group_by(data = bad_predicted, by = c("bad_loan", "predict"), sum("earned")))
sum_gain <- as.data.frame(h2o.group_by(data = good_predicted, by = c("bad_loan", "predict"), sum("earned")))

## Calculate the amount of earned
print(paste0("Total amount of loss that could have been prevented : $", printMoney(sum_loss$sum_earned[1]) , ""))
print(paste0("Total amount of loss that still would've accrued : $", printMoney(sum_gain$sum_earned[2]) , ""))
print(paste0("Total amount of profit still earned using the model : $", printMoney(sum_gain$sum_earned[1]) , ""))
print(paste0("Total amount of profit forfeitted using the model : $", printMoney(sum_loss$sum_earned[2]) , ""))

## Value of the GBM Model
diff <- - sum_loss$sum_earned[1] - sum_loss$sum_earned[2]
print(paste0("Total immediate gain the implementation of the model would've had on completed approved loans : $",printMoney(diff),""))
