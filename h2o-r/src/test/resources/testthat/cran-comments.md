This is a minor release that bumps required version of R to 3.1.0. Previous notes below.

--------------------------------------------------------------------------------

The following notes were generated across my local OS X install, ubuntu running on travis-ci and win builder (devel and release):

* Maintainer change: I've changed my email address to hadley@rstudio.com

I have also run R CMD check on the 339 downstream dependencies of testthat. Results for R release are available at: https://github.com/wch/checkresults/blob/master/testthat/r-release/00check-summary.txt. I'm reasonably certain that all failures are independent of changes to testthat:

* datacheck: failure occurs in vignette

* dbarts, RAM, RAppArmor, rsig, sdcTable, wgsea: couldn't install

* dplyr: due to backward incompatible changes to Lahman (will get fix out as
  soon as possible)

* ecoengine: this appears to be a failure related to config on that machine

* FastImputation, nscancor, Rssa, surveillance: failure occurs in examples

* llama: looks like failure to find java class definition, not related
  to testthat
  
* optiRum: appears to be pdf rendering problem

* plyr: parallel test fails because test machine doesn't have multicores
  available
  
There are more errors with on R-devel (2014-09-17 r66626, https://github.com/wch/checkresults/tree/master/testthat/r-devel), but these are due to the changes to `all.equal()` when applied to environments. The version of R-devel on winbuilder appears to be old, and I can't recreate the "node stack overflow" error locally.
