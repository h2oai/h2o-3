#MOJO Quick Start


This document describes how to build and implement a MOJO (Model Object, Optimized) to use predictive scoring. Java developers should refer to the [Javadoc](http://docs.h2o.ai/h2o/latest-stable/h2o-genmodel/javadoc/index.html) for more information, including packages. 


##What is a MOJO?


A MOJO (Model Object, Optimized) is an alternative to H2O's currently available
[POJO](https://github.com/h2oai/h2o-3/blob/master/h2o-docs/src/product/howto/POJO_QuickStart.md). As with POJOs, H2O allows you to convert models that you build to MOJOs, which can then be deployed for scoring in real time.

**Note**: MOJOs are supported for GBM, DRF, and GLM models only.

##Benefit over POJOs

While POJOs continue to be supported, some customers encountered issues with large POJOs not compiling. (Note that POJOs are not supported for source files larger than 1G.) MOJOs do not have a size restriction and address the size issue by taking the tree out of the POJO and using generic tree-walker code to navigate the model. The resulting executable is much smaller and faster than a POJO.

At large scale, new models are roughly 20-25 times smaller in disk space, 2-3 times faster during "hot" scoring, and 10-40 times faster in "cold" scoring compared to POJOs. The efficiency gains are larger the bigger the size of the model.

H2O conducted in-house testing using models with 5000 trees of depth 25. At very small scale (50 trees / 5 depth), POJOs were found to perform ≈10% faster than MOJOs for binomial and regression models, but 50% slower than MOJOs for multinomial models.

##Building a MOJO


MOJOs are built in much the same way as POJOs. The example code below shows how to start H2O and then build a model using either R or Python.

###Step 1: Start H2O, then build and extract the model

The examples below describe how to start H2O and create a model using R and Python. The ``download_mojo()`` function saves the model as a zip file. You can unzip the file to view the options used to build the file along with each tree built in the model. Note that each tree file is saved as a binary file type. 

**Build and extract a model using R**

1. Open a terminal window and start r.
2. Run the following commands to build a simple GBM model. 

        library(h2o)
        h2o.init(nthreads=-1)
        path = system.file("extdata", "prostate.csv", package="h2o")
        h2o_df = h2o.importFile(path)
        h2o_df$RACE = as.factor(h2o_df$RACE)
        model = h2o.gbm(y="CAPSULE",
                        x=c("AGE", "RACE", "PSA", "GLEASON"),
                        training_frame=h2o_df,
                        distribution="bernoulli",
                        ntrees=100,
                        max_depth=4,
                        learn_rate=0.1)

3. Download the MOJO and the resulting h2o-genmodel.jar file to a new **experiment** folder. 

		modelfile = model.download_mojo(path="~/experiment/", get_genmodel_jar=True)
		print("Model saved to " + modelfile)
		Model saved to /Users/user/GBM_model_R_1475248925871_74.zip"
            

**Build and extract a model using Python**

1. Open a terminal window and start python. 
2. Run the following commands to build a simple GBM model. The model, along with the **h2o-genmodel.jar** file will then be downloaded to an **experiment** folder. 
	
	    import h2o
	    from h2o.estimators.gbm import H2OGradientBoostingEstimator
	    h2o.init()
	    h2o_df = h2o.load_dataset("prostate.csv")
	    h2o_df["CAPSULE"] = h2o_df["CAPSULE"].asfactor()
	    model=H2OGradientBoostingEstimator(distribution="bernoulli",
	                                       ntrees=100,
	                                       max_depth=4,
	                                       learn_rate=0.1)
	    model.train(y="CAPSULE",
	                x=["AGE","RACE","PSA","GLEASON"],
	                training_frame=h2o_df)

3. Download the MOJO and the resulting h2o-genmodel.jar file to a new **experiment** folder. 

		modelfile = model.download_mojo(path="~/experiment/", get_genmodel_jar=True)
		print("Model saved to " + modelfile)
		Model saved to /Users/user/GBM_model_python_1475248925871_888.zip           


###Step 2: Compile and run the MOJO

1.  Open a *new* terminal window and change directories to the **experiment** folder:
		
		$ cd experiment

2.  Create your main program in the **experiment** folder by creating a new file called main.java (for example, using "vim main.java"). Include the following contents. Note that this file references the GBM model created above using R. 

        import java.io.*;
        import hex.genmodel.easy.RowData;
        import hex.genmodel.easy.EasyPredictModelWrapper;
        import hex.genmodel.easy.prediction.*;
        import hex.genmodel.MojoModel;
		
        public class main {
          public static void main(String[] args) throws Exception {
            EasyPredictModelWrapper model = new EasyPredictModelWrapper(MojoModel.load("GBM_model_R_1475248925871_74.zip"));

            RowData row = new RowData();
            row.put("AGE", "68");
            row.put("RACE", "2");
            row.put("DCAPS", "2");
            row.put("VOL", "0");
            row.put("GLEASON", "6");

            BinomialModelPrediction p = model.predictBinomial(row);
            System.out.println("Has penetrated the prostatic capsule (1=yes; 0=no): " + p.label);
            System.out.print("Class probabilities: ");
            for (int i = 0; i < p.classProbabilities.length; i++) {
              if (i > 0) {
                System.out.print(",");
              }
              System.out.print(p.classProbabilities[i]);
            }
            System.out.println("");
          }
        }
        
3. Compile and run in terminal window 2. 

		$ javac -cp h2o-genmodel.jar -J-Xms2g -J-XX:MaxPermSize=128m main.java
		$ java -cp .:h2o-genmodel.jar main
		
The following output displays:

	Has penetrated the prostatic capsule (1 yes; 0 no): 0
	Class probabilities: 0.8059929056296662,0.19400709437033375
