Hierarchical Generalized Linear Model (HGLM)
--------------------------------------------

Introduction
~~~~~~~~~~~~

Generalized Linear Models (GLM) estimate regression models for outcomes following exponential distributions. Hierarchical GLM (HGLM) fits generalized linear models with random effects, where the random effect can come from a conjugate exponential-family distribution (for example, Gaussian). HGLM allows you to specify both fixed and random effects, which allows fitting correlated to randome effects as we as random regression modles. Fixed effects can also be omdeled in the dissperssion parameter. 

HGLM produces estimates for fixed effects, random effects, variance components and their standard errors. It also produces diagnostics, such as variances and leverages. HGLM can be used for linear mixed models and for generalized linear mixed models with random effects for a variety of links and a variety of distributions for both the outcomes and the random effects. 

**Note**: This initial release of HGLM supports only the Gaussian family and random family.

Defining an HGLM Model
~~~~~~~~~~~~~~~~~~~~~~








References
~~~~~~~~~~

Rönnegård, Lars, Shen, Xia, and Moudud, Alam. *The hglm Package (Version 2.0).* https://cran.r-project.org/package=hglm.
