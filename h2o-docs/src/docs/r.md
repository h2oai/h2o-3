# Using H2O in R

The purpose of this tutorial is to walk the new user through
examples demonstrating the use of H2O through R.  The objective is to
learn the basic syntax of H2O, including importing and
parsing files, specifying a model, and obtaining model output.

Those who have never used H2O before should see the quick
start guide for additional instructions on how to run H2O.
Additionally, users who are using H2O through R for the
first time will need to install the R package, available in our
download package at: http://0xdata.com/downloadtable/.

It is highly recommended that users review the getting started section
before proceeding to other sections for examples. At a minimum users
should run the following commands before running any other examples,
as the H2O library and H2O object (named `localH2O` in examples) is
required in R for most examples to work.

```r
library(h2o)
localH2O = h2o.init(ip = "localhost", port = 54321, startH2O = TRUE)
```

In this tutorial you can find information on:

- [Installing from CRAN](install-cran)
- [Installing a specific version](install-version)
- [Installing from source](install-source)
- [Starting H2O](start)
- [Importing Data](import)
- [Data Manipulation](data)
- [Modeling](model)
- [Predicting](predict)
- [Other useful functions](general)
- [R Package Documentation](package)

