# This is a demo of H2O's GLM function
# It imports a data set, parses it, and prints a summary
# Then, it runs GLM with a binomial link function using 10-fold cross-validation
library(h2o)
myIP = readline("Enter IP address of H2O server: ")
myPort = readline("Enter port number of H2O server: ")
remoteH2O = h2o.init(ip = myIP, port = as.numeric(myPort), startH2O = FALSE)

prostate.hex = h2o.uploadFile(remoteH2O, path = system.file("extdata", "prostate.csv", package="h2o"), destination_frame = "prostate.hex")
summary(prostate.hex)
prostate.glm = h2o.glm(x = c("AGE","RACE","PSA","DCAPS"), y = "CAPSULE", training_frame = prostate.hex, family = "binomial", alpha = 0.5)
print(prostate.glm)

myLabels = c(prostate.glm@model$x, "Intercept")
plot(prostate.glm@model$coefficients, xaxt = "n", xlab = "Coefficients", ylab = "Values")
axis(1, at = 1:length(myLabels), labels = myLabels)
abline(h = 0, col = 2, lty = 2)
title("Coefficients from Logistic Regression\n of Prostate Cancer Data")

barplot(prostate.glm@model$coefficients, main = "Coefficients from Logistic Regression\n of Prostate Cancer Data")
