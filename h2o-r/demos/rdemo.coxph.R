# Load the libraries used to analyze the data
library(survival)
library(MASS)
library(h2o)

# Start H2O cluster
h2o.init()

# Get path to data file
locate_source <- function(file) {
  file_path <- try(h2o:::.h2o.locate(file), silent = TRUE)
  if (inherits(file_path, "try-error")) {
    file_path <- paste0("https://s3.amazonaws.com/h2o-public-test-data/", file)
  }
  file_path
}

churn_path <- locate_source("smalldata/demos/WA_Fn-UseC_-Telco-Customer-Churn.csv")

# Read data into H2O cluster
churn_hex <- h2o.importFile(churn_path)

# Use functionality from survival and MASS to select the predictors
churn_df <- as.data.frame(churn_hex)

r_model <-
  stepAIC(
    coxph(Surv(tenure, Churn == "Yes") ~
            (I(gender == "Female") +
               SeniorCitizen +
               I(Partner == "Yes") +
               I(Dependents == "Yes") +
               (I(MultipleLines == "No") + I(MultipleLines == "Yes")) +
               I(PaperlessBilling == "Yes") +
               PaymentMethod +
               MonthlyCharges) +
            I(InternetService == "Yes") *
            (I(OnlineSecurity == "Yes") +
               I(OnlineBackup == "Yes") +
               I(DeviceProtection == "Yes") +
               I(TechSupport == "Yes") +
               I(StreamingTV == "Yes") +
               I(StreamingMovies == "Yes")) +
            strata(Contract),
          data = churn_df,
          ties = "efron"),
    k = log(sum(churn_df$Churn == "Yes"))
  )

r_model <-
  update(r_model,
         . ~ . - PaymentMethod +
           I(PaymentMethod %in% c("Bank transfer (automatic)",
                                  "Credit card (automatic)")))

# Transform data for the H2O-based model
churn_hex$HasChurned <- churn_hex$Churn == "Yes"
churn_hex$HasPartner <- h2o.asnumeric(churn_hex$Partner == "Yes")
churn_hex$HasSingleLine <- h2o.asnumeric(churn_hex$MultipleLines == "No")
churn_hex$HasMultipleLines <- h2o.asnumeric(churn_hex$MultipleLines == "Yes")
churn_hex$HasPaperlessBilling <- h2o.asnumeric(churn_hex$PaperlessBilling == "Yes")
churn_hex$HasAutomaticBilling <- h2o.asnumeric(churn_hex$PaymentMethod %in%
                                                 c("Bank transfer (automatic)",
                                                   "Credit card (automatic)"))
churn_hex$HasOnlineSecurity <- h2o.asnumeric(churn_hex$OnlineSecurity == "Yes")
churn_hex$HasOnlineBackup <- h2o.asnumeric(churn_hex$OnlineBackup == "Yes")
churn_hex$HasDeviceProtection <- h2o.asnumeric(churn_hex$DeviceProtection == "Yes")
churn_hex$HasTechSupport <- h2o.asnumeric(churn_hex$TechSupport == "Yes")
churn_hex$HasStreamingTV <- h2o.asnumeric(churn_hex$StreamingTV == "Yes")
churn_hex$HasStreamingMovies <- h2o.asnumeric(churn_hex$StreamingMovies == "Yes")

h2o.assign(churn_hex, key = "churn_hex")

# Create H2O-based model
predictors <- c("HasPartner", "HasSingleLine", "HasMultipleLines",
                "HasPaperlessBilling", "HasAutomaticBilling", "MonthlyCharges",
                "HasOnlineSecurity", "HasOnlineBackup", "HasDeviceProtection",
                "HasTechSupport", "HasStreamingTV", "HasStreamingMovies")

h2o_model <- h2o.coxph(x = predictors,
                       event_column = "HasChurned",
                       stop_column = "tenure",
                       stratify_by = "Contract",
                       training_frame = churn_hex)

print(summary(h2o_model))
