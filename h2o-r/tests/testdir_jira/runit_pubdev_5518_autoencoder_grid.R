setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../scripts/h2o-r-test-setup.R")

# Test derived from Nidhi Mehta.  Thanks.
test.pubdev.5518 <- function() {
    N=1000
    set.seed(5)
    color = sample(c("D","E","I","F","M"),size=N,replace=TRUE)
    num = rnorm(N,mean = 12,sd = 21212)
    sex = sample(c("male","female"),size=N,replace=TRUE)
    sex = as.factor(sex)
    color = as.factor(color)
    data = sample(c(0,1),size = N,replace = TRUE)
    fdata = factor(data)
    table(fdata)
    dd = data.frame(color,sex,num,fdata)
    data = as.h2o(dd)

    dl_hyper_params <- list(activation = c("Tanh", "RectifierWithDropout", "TanhWithDropout"))
    # will fail if any error function is caught.  Should be good now.
    e<-tryCatch({dl_grid <- h2o.grid("deeplearning", x = 1:4, grid_id = "ae_grid", training_frame = data, epochs = 2,
                                     autoencoder = TRUE, reproducible = TRUE, max_runtime_secs = 20, hyper_params = dl_hyper_params)},
                error=function(error_message) {
                  FAIL(error_message)
                })
    
}

doTest("PUBDEV-5518 Autoencoder grid get needs response column", test.pubdev.5518)
