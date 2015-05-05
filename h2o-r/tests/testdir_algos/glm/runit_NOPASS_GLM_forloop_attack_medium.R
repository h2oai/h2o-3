setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../../h2o-runit.R')

randomParams <- function(family, train, test, x, y) {
  parms <- list()
  # Constants
  bools <- c(TRUE, FALSE)
  if(identical(family, "gaussian"))
    family.links <- c("identity", "log", "inverse")
  else if (identical(family, "binomial"))
    family.links <- c("logit", "log")
  else if (identical(family, "poisson"))
    family.links <- c("log", "identity")
  else if (identical(family, "gamma"))
    family.links <- c("identity", "log", "inverse")
  plist <- c("max_iterations", "beta_epsilon", "solver", "standardize", "link", "alpha",
    "prior", "lambda_search", "nlambdas", "beta_constraints")

  # Required params
  # Set x
  # Remove some cols, or not
  if(sample(bools,1)) {
    while (TRUE){
      myX <- x
      for(i in 1:length(x))
        if (sample(bools, 1))
          myX <- myX[-i]
      if(length(myX) > 0)
        break
    }
    parms$x <- myX
  } else
    parms$x <- x
  Log.info(paste("x:", paste(parms$x, collapse = ", ")))
  # Set y
  parms$y <- y
  Log.info(paste("y:", parms$y))
  # Set training_frame
  parms$training_frame <- train
  Log.info(paste("Using training_frame:", deparse(substitute(train))))
  # Set validation_frame (maybe)
  if (sample(bools,1)){
    parms$validation_frame <- test
    Log.info(paste("validation_frame:", deparse(substitute(test))))
  }
  # Set family
  parms$family <- family
  Log.info(paste("family:", parms$family))

  # Non-default, non-required parameter setters
  # {} implies the parameter isn't being used
  set_max_iterations <-         function() sample.int(50,1)
  set_beta_epsilon <-           function() runif(1)
  set_solver <-                 function() sample(c("IRLSM", "L_BFGS"),1)
  set_standardize <-            function() sample(bools,1)
  set_family <-                 function() {}
  set_link <-                   function() sample(family.links,1)
  set_tweedie_variance_power <- function() {}
  set_tweedie_link_power <-     function() {}
  set_alpha <-                  function() runif(1)
  set_prior <-                  function() runif(1)
  set_lambda <-                 function() {}
  set_lambda_search <-          function() sample(bools,1)
  set_nlambdas <-               function() sample(2:10,1)
  set_lambda_min_ratio <-       function() {}
  set_use_all_factor_levels <-  function() {if (parms$standardize)
      sample(bools,1)
    else FALSE}
  set_nfolds <-                 function() {}
  set_beta_constraints <-       function() {
    if (identical(parms$solver, "L_BFGS"))  # TODO: HACK to skip beta-constraints+LBFGS bug
      return(NULL)
    name <- list()
    lower_bound <- list()
    upper_bound <- list()
    for (n in parms$x) {
      # If enum column => create Colname.Class
      if (is.factor(train[,n])) {
        # use_all_factor_levels == T => all factors acceptable
        if(!is.null(parms$use_all_factor_levels) && parms$use_all_factor_levels)
          enums <- paste(names(train)[n],h2o.levels(train, n), sep = ".")
        # use_all_factor_levels == F => first factor dropped
        else
          enums <- paste(names(train)[n],h2o.levels(train, n), sep = ".")[-1]
        name <- c(name, enums)
        for(e in enums) {
          l <- runif(1,-1,1)
          u <- runif(1) + l
          lower_bound <- c(lower_bound, l)
          upper_bound <- c(upper_bound, u)
        }
      } else {
        name <- c(name, names(train)[n])
        l <- runif(1,-1,1)
        u <- runif(1) + l
        lower_bound <- c(lower_bound, l)
        upper_bound <- c(upper_bound, u)
      }
    }
    parms$beta_constraints <- data.frame(names = unlist(name),
                                         lower_bounds = unlist(lower_bound),
                                         upper_bounds = unlist(upper_bound))
  }

  for (p in plist)
    if (sample(bools,1)) {
      parms[[p]] <- do.call(paste0("set_",p), list())
      if (inherits(parms[[p]], "data.frame")) {
        Log.info(paste0(sub("_", " ", p), ":"))
        print(parms[[p]])
      } else
        Log.info(paste0(sub("_", " ", p), ": ", parms[[p]]))
    } else
      Log.info(paste0(sub("_", " ", p), ": DEFAULT"))

  t <- system.time(hh <- do.call("h2o.glm", parms))
  print(hh)

  h2o.rm(hh@model_id)
  print("#########################################################################################")
  print("\n\n")
  print(t)
  print("\n\n")
}


test.glm.rand_attk_forloop <- function(conn) {
  Log.info("Import and data munging...")
  pros.hex <- h2o.uploadFile(conn, locate("smalldata/prostate/prostate.csv.zip"))
  pros.hex[,2] <- as.factor(pros.hex[,2])
  pros.hex[,4] <- as.factor(pros.hex[,4])
  pros.hex[,5] <- as.factor(pros.hex[,5])
  pros.hex[,6] <- as.factor(pros.hex[,6])
  pros.hex[,9] <- as.factor(pros.hex[,9])
  p.sid <- h2o.runif(pros.hex)
  pros.train <- h2o.assign(pros.hex[p.sid > .2, ], "pros.train")
  pros.test <- h2o.assign(pros.hex[p.sid <= .2, ], "pros.test")

  cars.hex <- h2o.uploadFile(conn, locate("smalldata/junit/cars.csv"))
  cars.hex[,3] <- as.factor(cars.hex[,3])
  c.sid <- h2o.runif(cars.hex)
  cars.train <- h2o.assign(cars.hex[c.sid > .2, ], "cars.train")
  cars.test <- h2o.assign(cars.hex[c.sid <= .2, ], "cars.test")

  Log.info("### Binomial ###")
  for(i in 1:10)
    randomParams("binomial", pros.train, pros.test, 3:9, 2)
  Log.info("### Gaussian ###")
  for(i in 1:10)
    randomParams("gaussian", cars.train, cars.test, 3:7, 2)
  Log.info("### Poisson ###")
  for(i in 1:10)
    randomParams("poisson", cars.train, cars.test, 3:7, 2)
  Log.info("### Gamma ###")
  for(i in 1:10)
    randomParams("gamma", cars.train, cars.test, 3:7, 2)

  testEnd()
}

doTest("Checking GLM in Random Attack For Loops", test.glm.rand_attk_forloop)
