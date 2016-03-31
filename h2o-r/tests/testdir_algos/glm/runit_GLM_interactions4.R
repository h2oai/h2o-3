setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source("../../../scripts/h2o-r-test-setup.R")

test.glm.interactions3 <- function() {

  interactions <- model.matrix(Petal.Width ~ Species*Sepal.Length, data=iris)[,5:6]
  interactions2 <- model.matrix(Petal.Width ~ Species*Petal.Length,data=iris)[,5:6]

  df <- data.frame(Species=iris[,5],
                   Species_Sepal.Length.versicolor = interactions[,1],
                   Species_Sepal.Length.virginica = interactions[,2],
                   Species_Petal.Length.versicolor = interactions2[,1],
                   Species_Petal.Length.virginica = interactions2[,2],
                   Sepal.Length_Petal.Length = iris[,"Sepal.Length"] * iris[,"Petal.Length"],
                   Sepal.Length=iris[,"Sepal.Length"],
                   Sepal.Width=iris[,"Sepal.Width"],
                   Petal.Length=iris[,"Petal.Length"],
                   Petal.Width=iris[,"Petal.Width"])

  df <- as.h2o(df)
  h2o_glm1 <- h2o.glm(y="Petal.Width", x=c("Species","Species_Sepal.Length.versicolor", "Species_Sepal.Length.virginica", "Species_Petal.Length.versicolor", "Species_Petal.Length.virginica", "Sepal.Length","Sepal.Width", "Petal.Length", "Sepal.Length_Petal.Length"), training_frame=df, lambda=0, standardize=TRUE)


  h2o_glm2 <- h2o.glm(y="Petal.Width", x=c("Species","Sepal.Length","Sepal.Width", "Petal.Length"), interactions=c("Species", "Sepal.Length", "Petal.Length"), lambda=0, standardize=TRUE, training_frame=as.h2o(iris))


  R_glm <- lm(Petal.Width ~ Species*Sepal.Length + Species*Petal.Length + Sepal.Width + Petal.Length + Sepal.Length*Petal.Length, data=iris)
  m_R_coefs <- coef(R_glm)

  h2o_glm1_coefs <- h2o_glm1@model$coefficients_table
  h2o_glm2_coefs <- h2o_glm2@model$coefficients_table

  for(i in 1:length(m_R_coefs)) {
    name <- names(m_R_coefs[i])
    print(name)
    if( name=="(Intercept)" )       name <- "Intercept"
    if( name=="Speciesversicolor" ) name <- "Species.versicolor"
    if( name=="Speciesvirginica"  ) name <- "Species.virginica"
    if( name=="Speciesversicolor:Sepal.Length" ) name <- "Species_Sepal.Length.versicolor"
    if( name=="Speciesvirginica:Sepal.Length"  ) name <- "Species_Sepal.Length.virginica"
    if( name=="Sepal.Length:Petal.Length"      ) name <- "Sepal.Length_Petal.Length"
    if( name=="Speciesvirginica:Petal.Length"  ) name <- "Species_Petal.Length.virginica"
    if( name=="Speciesversicolor:Petal.Length" ) name <- "Species_Petal.Length.versicolor"

    print(name)
    h2o_coef1 <- h2o_glm1_coefs[h2o_glm1_coefs$names==name,"coefficients"]
    h2o_coef2 <- h2o_glm2_coefs[h2o_glm2_coefs$names==name,"coefficients"]
    R_coef <- as.vector(m_R_coefs)[i]
    print( paste0("H2O Coeff1: ",  h2o_coef1))
    print( paste0("H2O Coeff2: ",  h2o_coef2))
    print( paste0("R   Coeff:  ",  R_coef))
    expect_true( abs( h2o_coef2 - R_coef    ) < 1e-10  )
    expect_true( abs( h2o_coef2 - h2o_coef1 ) < 1e-10  )


    h2o_norm_coef1 <- h2o_glm1_coefs[h2o_glm1_coefs$names==name,"standardized_coefficients"]
    h2o_norm_coef2 <- h2o_glm2_coefs[h2o_glm2_coefs$names==name,"standardized_coefficients"]

    print( paste0("H2O Standardized Coeff1: ",  h2o_norm_coef1))
    print( paste0("H2O Standardized Coeff2: ",  h2o_norm_coef2))
    expect_true( abs( h2o_coef2 - h2o_coef1 ) < 1e-11  )
  }

}

doTest("Testing model accessors for GLM", test.glm.interactions3)
