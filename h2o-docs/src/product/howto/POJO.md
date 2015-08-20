#POJOs

##What is a POJO? 

H2O allows you to convert the models you have built to a Plain Old Java Object (POJO), which can then be easily deployed within your Java app and scheduled to run on a specified dataset.

POJOs allow users to build a model using H2O and then deploy the model to score in real-time, using the POJO model or a REST API call to a scoring server.

---

##How can I use POJOs? 

###Customer Examples

**Sharethis** - Embeds our Java POJO as a real-time ad bidding server. This is a production application where every bid on an ad is money spent on a campaign.

**RobertHalf** - Uses our offline model building on resumes, deploys a Java POJO to identify skill sets and professions for all received resumes in real-time and directs it to different line of businesses within RobertHalf.

**MarketShare** - Uses POJOs in production to show marketing the lift generated from their marketing campaigns by spending their marketing budget on the suggestions provided by their recommendation engine. 

---

##How do I do that?

%%should include multiple methods: web UI (Flow), R, Python...any others?%%

###Getting POJOs

0. Create a model using H2O. 
0. Click the **Preview POJO** button in the output. 

	>**Note**: To make the POJO work in your Java application, you will also need the `h2o-genmodel.jar` file (available in `h2o-3/h2o-genmodel/build/libs/h2o-genmodel.jar`).

###Implementing POJOs 

0. Compile the model's POJO into your project. 

###Using POJOs

0. Score in your app using the POJO. 

##Examples

An automation code in Java that automates the process of uploading, parsing, running, predicting, getting AUC scores, and downloading the model as a Java POJO is available on GitHub:

https://github.com/h2oai/h2o/tree/master/h2o-samples/devops-automation


###Hive Tutorial 

For a detailed tutorial on how use a model created in H2O to create a Hive user-defined function (UDF), refer to the following link: 

https://github.com/h2oai/h2o-3-training/tree/master/tutorials/hive_udf_template

##FAQ

**How do I score new cases in real-time in a production environment?**

If you're using the UI, click the **Preview POJO** button for your model. This produces a Java class with methods that you can reference and use in your production app.

**What kind of technology would I need to use?** 

Anything that runs in a JVM. The POJO is a standalone Java class with no dependencies on H2O. 

**Do I need to pre-process all the inputs before calling the scoring engine?**

Currently, yes.

**How do I run a POJO on a Spark Cluster?**

The POJO provides just the math logic to do predictions, so you won’t find any Spark (or even H2O) specific code there.  If you want to use the POJO to make predictions on a dataset in Spark, create a map to call the POJO for each row and save the result to a new column, row-by-row. 


**How do I score using an exported POJO?**

The generated POJO can be used independently of a H2O cluster. First use `curl` to send the h2o-genmodel.jar file and the java code for model to the server. The following is an example; the ip address and model names will need to be changed. 

```
mkdir tmpdir
cd tmpdir
curl http://127.0.0.1:54321/3/h2o-genmodel.jar > h2o-genmodel.jar
curl http://127.0.0.1:54321/3/Models.java/gbm_model > gbm_model.java
```

To score a simple .CSV file, download the [PredictCSV.java](https://raw.githubusercontent.com/h2oai/h2o-3/master/h2o-r/tests/testdir_javapredict/PredictCSV.java) file and compile it with the POJO. Make a subdirectory for the compilation (this is useful if you have multiple models to score on).

```
wget https://raw.githubusercontent.com/h2oai/h2o-3/master/h2o-r/tests/testdir_javapredict/PredictCSV.java
mkdir gbm_model_dir
javac -cp h2o-genmodel.jar -J-Xmx2g -J-XX:MaxPermSize=128m PredictCSV.java gbm_model.java -d gbm_model_dir
``` 

Specify the following:
- the classpath using `-cp` 
- the model name (or class) using `--model` 
- the csv file you want to score using `--input` 
- the location for the predictions using `--output`. 
 
You must match the table column names to the order specified in the POJO. The output file will be in a .hex format, which is a lossless text representation of floating point numbers. Both R and Java will be able to read the hex strings as numerics.

```
java -ea -cp h2o-genmodel.jar:gbm_model_dir -Xmx4g -XX:MaxPermSize=256m -XX:ReservedCodeCacheSize=256m PredictCSV --header --model gbm_model --input input.csv --output output.csv
```

**How do I communicate with a remote cluster using the REST API?**

To create a set of bare POJOs for the REST API payloads that can be used by JVM REST API clients: 

0. Clone the sources from GitHub. 
0. Start an H2O instance. 
0. Enter `% cd py`.
0. Enter `% python generate_java_binding.py`. 

This script connects to the server, gets all the metadata for the REST API schemas, and writes the Java POJOs to `{sourcehome}/build/bindings/Java`. 

##Needs to be updated

- http://docs.h2o.ai/h2oclassic/userguide/scorePOJO.html
- http://learn.h2o.ai/content/demos/streaming_data.html
- http://learn.h2o.ai/content/hackers_station/index.html
