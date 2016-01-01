setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")



# Constants and setters
bools <- c(TRUE, FALSE)
set_x <- function(cols) {
  if(sample(bools,1)) {
    while (TRUE){
      myX <- cols
      for(i in 1:length(cols))
        if (sample(bools, 1))
          myX <- myX[-i]
      if(length(myX) > 0)
        break
    }
    return(myX)
  } else
    cols
}
set_y <- function(col) return(col)
set_training_frame <- function(frame) return(frame)
set_validation_frame <- function(frame) return(frame)
set_max_iterations <- function() sample.int(50,1)
set_beta_epsilon <- function() runif(1)
set_solver <- function() sample(c("AUTO", "IRLSM", "L_BFGS", "COORDINATE_DESCENT_NAIVE", "COORDINATE_DESCENT"),1)
set_standardize <- function() sample(bools,1)
set_family <- function(family) return(family)
set_link <- function(family) {
  if(identical(family, "gaussian"))
    return(sample(c("identity", "log", "inverse"), 1))
  else if (identical(family, "binomial"))
    return("logit")
  else if (identical(family, "poisson"))
    return(sample(c("log", "identity"),1 ))
  else if (identical(family, "gamma"))
    return(sample(c("identity", "log", "inverse"),1))
}
set_tweedie_variance_power <- function() {}
set_tweedie_link_power <- function() {}
set_alpha <- function() runif(1)
set_prior <- function() runif(1)
set_lambda <- function() {}
set_lambda_search <- function() sample(bools,1)
set_nlambdas <- function() sample(2:10,1)
set_lambda_min_ratio <- function() {}
set_beta_constraints <- function(standardize, cols, frame, ignored) {
  name <- list()
    lower_bound <- list()
    upper_bound <- list()
    if (!is.null(ignored) && any(colnames(frame)[cols] %in% ignored))
      cols <- cols[-which(colnames(frame)[cols] %in% ignored)]
    for (n in cols) {
      # If enum column => create Colname.Class
      if (is.factor(frame[,n])) {
        # (standardize == T) => (use_all_factor_levels == T) => all factors acceptable
        if(is.null(standardize) || standardize)
          enums <- paste(names(frame)[n],h2o.levels(frame, n), sep = ".")
        # (standardize == F) => (use_all_factor_levels == F) => first factor dropped
        else
          enums <- paste(names(frame)[n],h2o.levels(frame, n), sep = ".")[-1]
        name <- c(name, enums)
        for(e in enums) {
          l <- runif(1,-1,1)
          u <- runif(1) + l
          lower_bound <- c(lower_bound, l)
          upper_bound <- c(upper_bound, u)
        }
      } else {
        name <- c(name, names(frame)[n])
        l <- runif(1,-1,1)
        u <- runif(1) + l
        lower_bound <- c(lower_bound, l)
        upper_bound <- c(upper_bound, u)
      }
    }
    return(data.frame(names = unlist(name),
               lower_bounds = unlist(lower_bound),
               upper_bounds = unlist(upper_bound)))
}
set_offset_column <- function(cols, frame)
  while(1) {
    val <- sample(names(frame)[cols], 1)
    if(!is.factor(frame[,val]))
      return(val)
  }
set_weights_column <- function(col) return("weights")

randomParams <- function(family, train, test, x, y) {
  parms <- list()

  parm_set <- function(parm, required = FALSE, dep = TRUE, ...) {
    if (!dep)
      return(NULL)
    if (required || sample(bools,1)) {
      val <- do.call(paste0("set_", parm), list(...))
      if (!is.null(val))
        if (identical(val, "weights")) {
          h2oTest.logInfo(paste0(sub("_", " ", parm), ":"))
          print(weights.train)
        } else if (is.vector(val)) {
          h2oTest.logInfo(paste0(sub("_", " ", parm), ": ",val))
        } else if (class(val) == "H2OFrame") {
          h2oTest.logInfo(paste0(sub("_", " ", parm), ": "))
        } else if (inherits(val, "data.frame")) {
          h2oTest.logInfo(paste0(sub("_", " ", parm), ": "))
          print(val)
        } else {
          h2oTest.logInfo(paste0(sub("_", " ", parm), ": ",val)) }
      return(val)
    }
    return(NULL)
  }

  weights.train <- runif(nrow(train), min = 0, max = 10)
  weights.test <- runif(nrow(test), min = 0, max = 10)
  train$weights <- as.h2o(weights.train)
  test$weights <- as.h2o(weights.test)

  parms$x <- parm_set("x", required = TRUE, cols = x)
  parms$y <- parm_set("y", required = TRUE, col = y)
  parms$family <- parm_set("family", family = family, required = TRUE)
  parms$training_frame <- parm_set("training_frame", required = TRUE, frame = train)
  parms$validation_frame <- parm_set("validation_frame", frame = test)
  parms$max_iterations <- parm_set("max_iterations")
  parms$beta_epsilon <- parm_set("beta_epsilon")
  parms$solver <- parm_set("solver")
  parms$standardize <- parm_set("standardize")
  parms$link <- parm_set("link", family = family)
  # parms$tweedie_variance_power <- parm_set("tweedie_variance_power")
  # parms$tweedie_link_power <- parm_set("tweedie_link_power")
  parms$alpha <- parm_set("alpha")
  parms$prior <- parm_set("prior", dep = identical(family, "binomial"))
  # parms$lambda <- parm_set("lambda")
  parms$lambda_search <- parm_set("lambda_search")
  parms$nlambdas <- parm_set("nlambdas", dep = !is.null(parms$lambda_search) && parms$lambda_search)
  # parms$lambda_min_ratio <- parm_set("lambda_min_ratio")
  #parms$offset_column <- parm_set("offset_column", cols = x, frame = train)
  #parms$weights_column <- parm_set("weights_column")
  parms$beta_constraints <- parm_set("beta_constraints", standardize = parms$standardize,
    cols = parms$x, frame = train, ignored = parms$offset_column)

  t <- system.time(hh <- do.call("h2o.glm", parms))
  print(hh)

  print("#########################################################################################")
  print("")
  print(t)
  print("")
}

test.glm.rand_attk_forloop <- function() {

  h2oTest.logInfo("Import and data munging...")
  pros.hex <- h2o.uploadFile(h2oTest.locate("smalldata/prostate/prostate.csv"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])
  p.sid <- h2o.runif(pros.hex)
  pros.train <- h2o.assign(pros.hex[p.sid > .2, ], "pros.train")
  pros.test <- h2o.assign(pros.hex[p.sid <= .2, ], "pros.test")

  cars.hex <- h2o.uploadFile(h2oTest.locate("smalldata/junit/cars.csv"))
  cars.hex[,3] <- as.factor(cars.hex[,3])
  c.sid <- h2o.runif(cars.hex)
  cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")

  h2oTest.logInfo("### Binomial ###")
  for(i in 1:10)
    randomParams("binomial", pros.train, pros.test, 3:9, 2)
  h2oTest.logInfo("### Gaussian ###")
  for(i in 1:10)
    randomParams("gaussian", cars.train, cars.test, 3:7, 2)
  h2oTest.logInfo("### Poisson ###")
  for(i in 1:10)
    randomParams("poisson", cars.train, cars.test, 3:7, 2)
  h2oTest.logInfo("### Gamma ###")
  for(i in 1:10)
    randomParams("gamma", cars.train, cars.test, 3:7, 2)

}

h2oTest.doTest("Checking GLM in Random Attack For Loops", test.glm.rand_attk_forloop)
