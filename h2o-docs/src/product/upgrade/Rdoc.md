#Intro to using H2O-Dev from R with data munging (for PUBDEV-562)  

We have the reference doc for the H2O R binding, but we regularly get questions from new users asking about which parts of R are supported, in particular regarding data munging.  A 15-20 page intro doc would be really useful.  Perhaps this should be a new booklet in the small yellow book series.

It should give an overview of:

0.  how the big data is kept in the cluster and manipulated from R via references,

1. how to move data back and forth between data in R ,

2. what operations are implemented in the H2O back end, 

3. example scripts which include simple data munging (frame manipulation via R expressions and ddply), perhaps based on the CityBike example (sans weather join) and Alex's examples.

Per Ray, this doc should also include:

slicing
creating new columns
tutorials/demos
explanation of how it works
standard data prep


#What is H2O?
 
H2O is fast, scalable, open-source machine learning and deep learning for Smarter Applications. With H2O enterprises like PayPal, Nielsen, Cisco, and others can use all of their data without sampling and get accurate predictions faster. Advanced algorithms, like Deep Learning, Boosting, and Bagging Ensembles are readily available for application designers to build smarter applications through elegant APIs. Some of our earliest customers have built powerful domain-specific predictive engines for Recommendations, Customer Churn, Propensity to Buy, Dynamic Pricing, and Fraud Detection for the Insurance, Healthcare, Telecommunications, AdTech, Retail, and Payment Systems.

Using in-memory compression techniques, H2O can handle billions of data rows in-memory, even with a fairly small cluster. The platform includes interfaces for R, Python, Scala, Java, JSON and Coffeescript/JavaScript, along with its built-in Flow web interface that makes it easier for non-engineers to stitch together complete analytic workflows. The platform is built alongside (and on top of) both Hadoop and Spark Clusters and typically deploys within minutes.

H2O implements almost all common machine learning algorithms, such as generalized linear modeling (linear regression, logistic regression, etc.), Naïve Bayes, time series, k-means clustering, and others. H2O also implements best-in-class algorithms such as Random Forest, Gradient Boosting Machine, and Deep Learning at scale. Customers can build thousands of models and compare them to get the best prediction results.

H2O is nurturing a grassroots movement of physicists, mathematicians, computer and data scientists to herald the new wave of discovery with data science. Academic researchers and Industrial data scientists collaborate closely with our team to make this possible. Stanford university giants Stephen Boyd, Trevor Hastie, and Rob Tibshirani advise the H2O team to build scalable machine learning algorithms. With hundreds of meetups over the past two years, H2O has become a growing word-of-mouth phenomenon amongst the data community, now implemented by 12,000+ users and deployed in 2000+ corporations using R, Python, Hadoop and Spark.

#Intro

how the big data is kept in the cluster and manipulated from R via references
what operations are implemented in the H2O back end

#Installation 

###Installing R or R Studio

To download R:

0. Go to [http://cran.r-project.org/mirrors.html](http://cran.r-project.org/mirrors.html).
0. Select your closest local mirror.
0. Select your operating system (Linux, OS X, or Windows).
0. Depending on your OS, download the appropriate file, along with any required packages.
0. When the download is complete, unzip the file and install.

To download R Studio:

0. Go to [http://www.rstudio.com/products/rstudio/](http://www.rstudio.com/products/rstudio/).
0. Select your deployment type (desktop or server).
0. Download the file.
0. When the download is complete, unzip the file and install.


#H2O Initialization 

0. Go to [h2o.ai/downloads](http://h2o.ai/downloads). 
0. Under **Download H2O**, select a build. The "bleeding edge" build contains the latest changes, while the "latest stable release" may be more reliable. 
0. Click the **Install in R** tab above the **Download H2O** button. 
0. Copy and paste the commands into R or R Studio, one line at a time. 

The lines are reproduced below; however, you should not copy and paste them, as the required version number has been replaced with asterisks (*). Refer to the [Downloads page](http://h2o.ai/downloads) for the latest version number. 

	# The following two commands remove any previously installed H2O packages for R.
	if ("package:h2o" %in% search()) { detach("package:h2o", unload=TRUE) }
	if ("h2o" %in% rownames(installed.packages())) { remove.packages("h2o") }

	# Next, we download packages that H2O depends on.
	if (! ("methods" %in% rownames(installed.packages()))) { install.packages("methods") }
	if (! ("statmod" %in% rownames(installed.packages()))) { install.packages("statmod") }
	if (! ("stats" %in% rownames(installed.packages()))) { install.packages("stats") }
	if (! ("graphics" %in% rownames(installed.packages()))) { install.packages("graphics") }
	if (! ("RCurl" %in% rownames(installed.packages()))) { install.packages("RCurl") }
	if (! ("jsonlite" %in% rownames(installed.packages()))) { install.packages("jsonlite") }
	if (! ("tools" %in% rownames(installed.packages()))) { install.packages("tools") }
	if (! ("utils" %in% rownames(installed.packages()))) { install.packages("utils") }

	# Now we download, install and initialize the H2O package for R.
	install.packages("h2o", type="source", repos=(c("http://h2o-release.s3.amazonaws.com/h2o/master/****/R")))
	library(h2o)
	localH2O = h2o.init()

	# Finally, let's run a demo to see H2O at work.
	demo(h2o.kmeans) 

You can also enter `install.packages("h2o")` in R to load the latest H2O R package from CRAN. 

###Making a Build from Source Code

The R package is build as part of the standard build process. In the top-level `h2o-3` directory, use `./gradlew build`. 

To build the R component by itself: 
`cd h2o-r`
`../gradlew build`

The build output is located a CRAN-like layout in the R directory. 


####Installation from the command line

0. Navigate to the top-level `h2o-3` directory: `cd ~/h2o-3`. 
0. Install the H2O package for R: `R CMD INSTALL h2o-r/R/src/contrib/h2o_****.tar.gz`

   **Note**: Do not copy and paste the command above. You must replace the asterisks (*) with the current H2O .tar version number. Look in the `h2o-3/h2o-r/R/src/contrib/` directory for the version number. 

###  Installation from within R

0. Detach any currently loaded H2O package for R.  
`if ("package:h2o" %in% search()) { detach("package:h2o", unload=TRUE) }`  

	```
	Removing package from ‘/Users/H2O_User/.Rlibrary’
	(as ‘lib’ is unspecified)
	```

0. Remove any previously installed H2O package for R.  
`if ("h2o" %in% rownames(installed.packages())) { remove.packages("h2o") }`


	```
	Removing package from ‘/Users/H2O_User/.Rlibrary’
	(as ‘lib’ is unspecified)
	```

0. Install the dependencies for H2O.
   
   **Note**: This list may change as new capabilities are added to H2O. The commands are reproduced below, but we strongly recommend visiting the H2O download page at [h2o.ai/download](http://h2o.ai/download) for the most up-to-date list of dependencies. 
   
	```
  	if (! ("methods" %in% rownames(installed.packages()))) { install.packages("methods") }
	if (! ("statmod" %in% rownames(installed.packages()))) { install.packages("statmod") }
	if (! ("stats" %in% rownames(installed.packages()))) { install.packages("stats") }
	if (! ("graphics" %in% rownames(installed.packages()))) { install.packages("graphics") }
	if (! ("RCurl" %in% rownames(installed.packages()))) { install.packages("RCurl") }
	if (! ("jsonlite" %in% rownames(installed.packages()))) { install.packages("jsonlite") }
	if (! ("tools" %in% rownames(installed.packages()))) { install.packages("tools") }
	if (! ("utils" %in% rownames(installed.packages()))) { install.packages("utils") }
	```

0. Install the H2O R package from your build directory.  
  `install.packages("h2o", type="source", repos=(c("http://h2o-release.s3.amazonaws.com/h2o/master/****/R")))`

   **Note**: Do not copy and paste the command above. You must replace the asterisks (*) with the current H2O build number. Refer to the H2O download page at [h2o.ai/download](http://h2o.ai/download) for latest build number. 

	```
	Installing package into ‘/Users/tomk/.Rlibrary’
	(as ‘lib’ is unspecified)
	source repository is unavailable to check versions
	
	The downloaded binary packages are in
	/var/folders/tt/g5d7cr8d3fg84jmb5jr9dlrc0000gn/T//RtmpU2C3LG/downloaded_packages
	```


###  Connect to H2O from within R

To load the H2O package in R, use `library(h2o)`  

```

----------------------------------------------------------------------

Your next step is to start H2O and get a connection object (named
'localH2O', for example):
    > localH2O = h2o.init()

For H2O package documentation, ask for help:
    > ??h2o

After starting H2O, you can use the Web UI at http://localhost:54321
For more information visit http://docs.h2o.ai

----------------------------------------------------------------------

```


To launch H2O, use `localH2O = h2o.init(nthreads = - 1)`  

**Note**: The `nthreads = -1` parameter launches H2O using all available CPUs and is only applicable if you launch H2O locally using R. If you start H2O locally outside of R or start H2O on Hadoop, the `nthreads = -1` parameter is not applicable. 


```
H2O is not running yet, starting it now...

Note:  In case of errors look at the following log files:
    /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T//RtmpKkZY3r/h2o_H2O_User_started_from_r.out
    /var/folders/yl/cq5nhky53hjcl9wrqxt39kz80000gn/T//RtmpKkZY3r/h2o_H2O_User_started_from_r.err

java version "1.8.0_25"
Java(TM) SE Runtime Environment (build 1.8.0_25-b17)
Java HotSpot(TM) 64-Bit Server VM (build 25.25-b02, mixed mode)

.Successfully connected to http://127.0.0.1:54321/ 

R is connected to H2O cluster:
    H2O cluster uptime:         1 seconds 405 milliseconds 
    H2O cluster version:        3.1.0.3031 
    H2O cluster name:           H2O_started_from_R_H2O_User_nqf165 
    H2O cluster total nodes:    1 
    H2O cluster total memory:   3.56 GB 
    H2O cluster total cores:    8 
    H2O cluster allowed cores:  2 
    H2O cluster healthy:        TRUE 

Note:  As started, H2O is limited to the CRAN default of 2 CPUs.
       Shut down and restart H2O as shown below to use all your CPUs.
           > h2o.shutdown(localH2O)
           > localH2O = h2o.init(nthreads = -1)
```

##Munging operations in R:

###Overview:

Operating on an `H2OFrame` object triggers the rollup of the expression to be executed, but the expression itself is not evaluated. Instead, an AST is built from the R expression using R's built-in parser, which handles operator precedence. In the case of assignment, the AST is stashed into the variable in the assignment. The AST is bound to an R variable as a promise to evaluate the expression on demand. When evaluation is forced, the AST is walked, converted to JSON, and shipped over to H2O. The result returned by H2O is a key pointing to the newly-created frame. Depending on the methods used, the results may not be an H2OFrame return type. Any extra preprocessing of data returned by H2O is discussed in each instance, as it varies from method to method.


###What's implemented?
Many of R's generic S3 methods can be combined with H2OFrame objects so that  the result is coerced to an object of the appropriate type (typically an H2OFrame object). To view a list of R's generic methods, use `getGenerics()`. A call to `showMethods(classes="H2OFrame")` displays a list of permissible operations with H2OFrame objects. S3 methods are divided into four groups: 

- Math
- Ops
- Complex
- Summary

With the exception of Complex, H2OFrame methods fall into these categories as well. Specifically, the group divisions follow the S4 divisions: 

- Ops
- Math
- Math2
- Summary


###List:

####Ops Group

This group includes:

- **Arith**, for performing arithmetic on numeric or complex vectors
- **Compare**, for comparing values
- **Logic**, for logical operations

| **Ops** |&nbsp; |&nbsp; |&nbsp; |
|-----|-----|----|-----|
|  **Arith**|&nbsp; |&nbsp; | &nbsp;|
|`+`  |`-` | `*`|`/`|
|`^`  | `%%`| `%/%` | &nbsp;|
|  **Compare**| &nbsp;|&nbsp; |&nbsp; |
| `==`| `!=` | `<`|&nbsp;|
|`<=` | `>=`| `>`|&nbsp; |
|  **Logic**|&nbsp; | &nbsp;| &nbsp;|
|`&`| `∣`|&nbsp;|&nbsp;|


####Math Group

This group includes:

- **Trigonometric**, for trigonometric functions
- **Hyperbolic**, for hyperbolic functions
- **Miscellaneous**, which contains the absolute value and square root functions
- **Sign**, which returns a vector with the signs of the corresponding elements of x (does not work for complex vectors)
- **Rounding**, which allows rounding of numbers
- **Logarithms/Exponentials**, which compute logarithmic and exponential functions
- **Special**, which contains gamma functions
- **Cumulative**, which returns the cumulative sums, products, minima, or maxima


| **Math** |&nbsp; |&nbsp; | 
|-----|-----|-----|
|**Miscellaneous** |&nbsp;|
| `abs` | `sqrt`|&nbsp;|
|**Rounding**|&nbsp;|
| `floor`|`ceiling`| `trunc`|
|**Log/Exp**|
|`exp`|`expm1`|`log1p`|
| **Trigonometric** |
|`cos`|`sin`|`tan`|
|`acos`|`asin`|`atan`|
|**Hyperbolic**|
|`cosh`|`sinh`|`tanh`|
|`acosh`|`asinh`|`atanh`|
|**Sign**|
|`sign`|`round`|`signif`|
|**Special**|
|`lgamma`|`gamma`|`digamma`|`trigamma`|
|**Cumulative**|&nbsp;|
|`cumsum`|`cumprod`|`cummax`|`cummin`|



####Summary Group

This group includes:

- **Maxima/Minima**, which returns the maxima and minima 
- **Range**, which returns the minimum and maximum
- **Product**, which returns the product
- **Sum**, which returns the sum
- **All**, which tells the user if all values are true
- **Any**, which tells the user if any values are true

| **Summary**| |
|-----|-----|
|`max`|`min`|
|`range`|`prod`|
|`sum`|`all`|
|`any`|

####Non-Group Generic

This group includes:

- **Logic**, for logical operations
- **Matrix Multiplication**, for multiplying two matrices 
- **Extract/Replace**, for extracting or replacing part of an object
- **Value Matching**, for returning matching vectors
- **Apply**, for returning the values resulting from a function


| **Non-Group Generic** | &nbsp;|&nbsp; |&nbsp; |
|-----|-----|-----|-----|
|`!`|
|**Extract/Replace**|||
|`[`|`[[`|`[[<-`|`[<-`
| `$<-`|
|**Matrix Multiplication**|&nbsp;|
|`%/%`| `%*%`|
|**Value Matching**|&nbsp;|
|`%in%`|
|**Apply**|&nbsp;|
|`apply`|
|`as.character`|`as.data.frame`|
|`as.environment`|`as.factor`|`as.h2o`|`as.matrix`|
|`as.numeric`|`colnames`|`colnames<-`|`cut`|
|`dim`|`head`|`h2o.anyfactor`|`h2o.cbind`|
|`h2o.ddply`|`h2o.levels`|`h2o.rbind`|`h2o.runif`|
|`h2o.setLevel`|`h2o.table`|`ifelse`|`is.factor`|
|`is.na`|`length`|`log`|`match`|
|`mean`|`median`|`names`|`names<-`|
|`ncol`|`nrow`|`pop`|`push`|
|`quantile`|`reset`|`sapply`|`scale`|
|`sd`|`show`|`subset`|`summary`|
|`t`|`tail`|`transform`|`trunc`|
|`var`|`within`|







#Data Prep in R
standard data prep


#Data Manipulation in R

how to move data back and forth between data in R 
slicing
creating new columns

#Examples/Demos

#Support 

Users of the H2O package may submit general inquiries and bug reports to the H2O.ai support address, [support@h2oai.com](mailto:support@h2oai.com). Alternatively, specific bugs or issues may be filed to the H2O JIRA, [https://0xdata.atlassian.net](https://0xdata.atlassian.net).

#References

#Appendix
(commands)


