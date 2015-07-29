## Function for ploting the scoring history
plot_scoring <- function(model) {
  sh <- h2o.scoreHistory(object = model)
  par(mfrow=c(1,2))
  
  if(model@algorithm == "gbm"){
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


print("Load H2O library and create connection to H2O...")
library(h2o)
conn <- h2o.init(nthreads = -1)

print("Import approved and rejected loan requests from Lending Tree...")
pathToLoanData <- "/Users/amy/Desktop/lending_club/loanStats/"
pathToRejected <- "/Users/amy/Desktop/lending_club/declined_data/"
loanStats     <- h2o.importFile(path = pathToLoanData, destination_frame = "LoanStats")
rejectedStats <- h2o.importFile(path = pathToDeclinedData, destination_frame = "RejectedStats")


print("Create bad loan label, this will include charged off, defaulted, and late repayments on loans...")
loanStats$bad_loan <- ifelse(loanStats$loan_status == "Charged Off" | 
                               loanStats$loan_status == "Default" | 
                               loanStats$loan_status == "Does not meet the credit policy.  Status:Charged Off" | 
                               loanStats$loan_status == "Late (16-30 days)", 1, 0)
loanStats$bad_loan <- as.factor(loanStats$bad_loan)
print("Create the applicant's risk score which is their FICO credit score, if the score is 0 make it an NA...")
loanStats$risk_score <- ifelse(loanStats$last_fico_range_low == 0, NA,
                               (loanStats$last_fico_range_high + loanStats$last_fico_range_low)/2)


print("Subset the loan dataset to the variables available in the rejected dataset...")
colnames <- c("loan_amnt", "risk_score", "dti", "zip_code", "addr_state",
             "emp_length", "policy_code", "delinq_2yrs", "bad_loan")
data     <- loanStats[,colnames]


print("Rename column names in rejected dataset to match the loan dataset...")
names(rejectedStats) <- c("loan_amnt", "app_date", "loan_title", "risk_score", "dti", 
                          "zip_code", "addr_state", "emp_length", "policy_code")
print("Change a column from factor to numerics, the data will need to be brought into R...")
rejectedStats$dti  <- h2o.strsplit(x = rejectedStats$dti, split = "%")
dti_data_frame.R   <- as.data.frame(rejectedStats$dti)
dti_data_frame.hex <- as.h2o(dti_data_frame.R)
rejectedStats$dti  <- dti_data_frame.hex$C1

print("Set variables to predict bad loans...")
myY <- "bad_loan"
myX <- setdiff(colnames, myY)
# myX <- setdiff(names(loanStats), c(myY, "id", "member_id", "loan_status", "recoveries", "next_pymnt_d",
#                                   "out_prncp", "collection_recovery_fee", "last_pymnt_amnt", "out_prncp_inv",
#                                   "total_rec_prncp", "last_credit_pull_d", "last_pymnt_d", "earliest_cr_line",
#                                   "total_pymnt", "total_pymnt_inv", "total_rec_late_fee", "total_rec_int",
#                                   "last_fico_range_low", "last_fico_range_high", "fico_range_low", "fico_range_high",
#                                   "int_rate", "sub_grade", "funded_amnt_inv", "funded_amnt", "grade", "mths_since_last_delinq",
#                                   "mths_since_last_record", "revol_bal", "revol_util", "initial_list_status", "collections_12_mths_ex_med",
#                                   "mths_since_last_major_derog", "issue_d", "desc", "title"))

run_gbm <- function(data, x, y, model_id) {
  rand  <- h2o.runif(data)
  train <- data[rand$rnd <= 0.8, ]
  valid <- data[rand$rnd > 0.8, ]
  
  gbm_model <- h2o.gbm(x = x, y = y, training_frame = train, validation_frame = valid,
                       score_each_iteration = T,
                       model_id = model_id, ntrees = 100, max_depth = 5)
  return(gbm_model)
}

start     <- Sys.time()
gbm_model <- run_gbm(data, myX, myY, "gbm_model")
end       <- Sys.time()
gbmBuild  <- end -start
print(paste("Took", gbmBuild, units(gbmBuild), "to build a GBM Model with 100 trees and a AUC of :",
            gbm_model@model$validation_metrics@metrics$AUC , "on the validation set and",
            gbm_model@model$training_metrics@metrics$AUC, "on the training set."))
gbm_score <- plot_scoring(model = gbm_limited)
print("Number of trees that produced the best AUC value on the validations set : ")
print(gbm_score[gbm_score$validation_auc == max(gbm_score$validation_auc), ])

print("The variable importance for the GBM model...")
print(gbm_model@model$variable_importances)
print("The confusion matrix for the GBM model...")
print(h2o.confusionMatrix(object = gbm_model))

## Do a post - analysis of how much money we would've saved with this model...
pred <- h2o.predict(gbm_model, data)
loanStats_w_pred <- h2o.cbind(loanStats, pred)
names <- c("loan_amnt", "funded_amnt", "int_rate", "grade", "sub_grade", "predict","p0", "loan_status", "risk_score", "total_pymnt", "bad_loan")
loanStats_w_pred <- loanStats_w_pred[,names]
loanStats_w_pred$earned <- loanStats_w_pred$total_pymnt - loanStats_w_pred$loan_amnt
loanStats_w_pred_bad  <- loanStats_w_pred[loanStats_w_pred$bad_loan == "1", ]
loan_fully_paid <- loanStats_w_pred[loanStats_w_pred$loan_status == "Does not meet the credit policy.  Status:Fully Paid" |
                                      loanStats_w_pred$loan_status == "Fully Paid", ]

## Calculate how much money will be lost to false negative, vs how much will be saved due to true positives
printMoney <- function(x){
  x <- round(abs(x),2)
  format(x, digits=10, nsmall=2, decimal.mark=".", big.mark=",")
}
printMoney(sum_loss$sum_earned[1])
sum_loss <- as.data.frame(h2o.group_by(data = loanStats_w_pred_bad, by = c("bad_loan", "predict"), sum("earned")))
print(paste0("Total amount of loss that could have been prevented : $", printMoney(sum_loss$sum_earned[1]) , ""))
print(paste0("Total amount of loss that still would've accrued : $", printMoney(sum_loss$sum_earned[2]) , ""))

## Calculate the amount of earned
sum_gain <- as.data.frame(h2o.group_by(data = loan_fully_paid, by = c("bad_loan", "predict"), sum("earned")))
print(paste0("Total amount of profit still earned using the model : $", printMoney(sum_gain$sum_earned[1]) , ""))
print(paste0("Total amount of profit forfeitted using the model : $", printMoney(sum_gain$sum_earned[2]) , ""))

## Value of the GBM Model
diff <- - sum_loss$sum_earned[1] - sum_gain$sum_earned[2]
print(paste0("Total immediate gain the implementation of the model would've had on existing approved loans : $",printMoney(diff),""))
