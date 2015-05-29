#Flow Web UI ...

H2O Flow is an open-source user interface for H2O. It is a web-based interactive environment that allows you to combine code execution, text, mathematics, plots, and rich media in a single document, similar to <a href="http://ipython.org/notebook.html" target="_blank">iPython Notebooks</a>. 

With H2O Flow, you can capture, rerun, annotate, present, and share your workflow. H2O Flow allows you to use H2O interactively to import files, build models, and iteratively improve them. Based on your models, you can make predictions and add rich text to create vignettes of your work - all within Flow's browser-based environment. 

Flow's hybrid user interface seamlessly blends command-line computing with a modern graphical user interface. However, rather than displaying output as plain text, Flow provides a point-and-click user interface for every H2O operation. It allows you to access any H2O object in the form of well-organized tabular data. 

H2O Flow sends commands to H2O as a sequence of executable cells. The cells can be modified, rearranged, or saved to a library. Each cell contains an input field that allows you to enter commands, define functions, call other functions, and access other cells or objects on the page. When you execute the cell, the output is a graphical object, which can be inspected to view additional details. 

While H2O Flow supports REST API, R scripts, and CoffeeScript, no programming experience is required to run H2O Flow. You can click your way through any H2O operation without ever writing a single line of code. You can even disable the input cells to run H2O Flow using only the GUI. H2O Flow is designed to guide you every step of the way, by providing input prompts, interactive help, and example flows. 

##Introduction

This guide will walk you through how to use H2O's web UI, H2O Flow. To view a demo video of H2O Flow, click <a href="https://www.youtube.com/watch?feature=player_embedded&v=wzeuFfbW7WE" target="_blank">here</a>. 


---

<a name="GetHelp"></a> 
## Getting Help 
---

First, let's go over the basics. Type `h` to view a list of helpful shortcuts. 

The following help window displays: 

![help menu](https://raw.githubusercontent.com/h2oai/h2o/master/docs/Flow-images/Shortcuts.png)

To close this window, click the **X** in the upper-right corner, or click the **Close** button in the lower-right corner. You can also click behind the window to close it. You can also access this list of shortcuts by clicking the **Help** menu and selecting **Keyboard Shortcuts**. 

For additional help, select the **Help** sidebar to the right and click the **Assist Me!** button. 

![Assist Me](images/Flow_AssistMeButton.png) 

You can also type `assist` in a blank cell and press **Ctrl+Enter**. A list of common tasks displays to help you find the correct command. 

 ![Assist Me links](images/Flow_assist.png)
 
There are multiple resources to help you get started with Flow in the **Help** sidebar. To access this document, select the **Getting Started with H2O Flow** link below the **Help Topics** heading. 

You can also explore the pre-configured flows available in H2O Flow for a demonstration of how to create a flow. To view the example flows, click the **Browse installed packs...** link in the **Packs** subsection of the **Help** sidebar. Click the **examples** folder and select the example flow from the list. 

  ![Flow Packs](images/Flow_ExampleFlows.png)

If you have a flow currently open, a confirmation window appears asking if the current notebook should be replaced. To load the example flow, click the **Load Notebook** button. 

To view the REST API documentation, click the **Help** tab in the sidebar and then select the type of REST API documentation (**Routes** or **Schemas**). 

 ![REST API documentation](images/Flow_REST_docs.png)

Before getting started with H2O Flow, make sure you understand the different cell modes. 

---

<a name="Cell"></a>
## Understanding Cell Modes

There are two modes for cells: edit and command. 


###Using Edit Mode
In edit mode, the cell is yellow with a blinking bar to indicate where text can be entered and there is an orange flag to the left of the cell.


![Edit Mode](images/Flow_EditMode.png)
 
<a name="CmdMode"></a>
##Using Command Mode
 In command mode, the flag is yellow. The flag also indicates the cell's format: 

- **MD**: Markdown 
   
   **Note**: Markdown formatting is not applied until you run the cell by clicking the **Run** button or clicking the **Run** menu and selecting **Run**. 

 ![Flow - Markdown](images/Flow_markdown.png)

- **CS**: Code (default)

 ![Flow - Code](images/Flow_parse_code_ex.png)

- **RAW**: Raw format (for code comments) 

 ![Flow - Raw](images/Flow_raw.png)

- **H[1-6]**: Heading level (where 1 is a first-level heading) 

 ![Flow - Heading Levels](images/Flow_headinglevels.png)

**NOTE**: If there is an error in the cell, the flag is red. 

 ![Cell error](images/Flow_redflag.png)
 
 If the cell is executing commands, the flag is teal. The flag returns to yellow when the task is complete. 
 
 ![Cell executing](images/Flow_cellmode_runningflag.png)

###Changing Cell Formats

To change the cell's format (for example, from code to Markdown), make sure you are in not in command (not edit) mode and that the cell you want to change is selected. The easiest way to do this is to click on the flag to the left of the cell. Enter the keyboard shortcut for the format you want to use. The flag's text changes to display the current format. 

Cell Mode     | Keyboard Shortcut
------------- | -----------------
Code          | `y`
Markdown      | `m`
Raw text      | `r`
Heading 1     | `1`
Heading 2     | `2`
Heading 3     | `3` 
Heading 4     | `4` 
Heading 5     | `5`
Heading 6     | `6` 

###Running Flows
When you run the flow, a progress bar that indicates the current status of the flow. You can cancel the currently running flow by clicking the **Stop** button in the progress bar. 

  ![Flow Progress Bar](images/Flow_progressbar.png)

When the flow is complete, a message displays in the upper right. 
**Note**: If there is an error in the flow, H2O Flow stops the flow at the cell that contains the error. 

  ![Flow - Completed Successfully](images/Flow_run_pass.png)
  ![Flow - Did Not Complete](images/Flow_run_fail.png) 


###Using Keyboard Shortcuts

Here are some important keyboard shortcuts to remember: 

- Click a cell and press **Enter** to enter edit mode, which allows you to change the contents of a cell. 
- To exit edit mode, press **Esc**. 
- To execute the contents of a cell, press the **Ctrl** and **Enter** buttons at the same time.

The following commands must be entered in [command mode](#CmdMode).  

- To add a new cell *above* the current cell, press **a**. 
- To add a new cell *below* the current cell, press **b**. 
- To delete the current cell, press the **d** key *twice*. (**dd**). 

You can view these shortcuts by clicking **Help** > **Keyboard Shortcuts** or by clicking the **Help** tab in the sidebar. 


###Using Flow Buttons
There are also a series of buttons at the top of the page below the flow name that allow you to save the current flow, add a new cell, move cells up or down, run the current cell, and cut, copy, or paste the current cell. If you hover over the button, a description of the button's function displays. 

 ![Flow buttons](images/Flow_buttons.png)
 
You can also use the menus at the top of the screen to edit the order of the cells, toggle specific format types (such as input or output), create models, or score models. You can also access troubleshooting information or obtain help with Flow.  
 ![Flow menus](images/Flow_menus.png)

**Note**: To disable the code input and use H2O Flow strictly as a GUI, click the **Cell** menu, then **Toggle Cell Input**. 

Now that you are familiar with the cell modes, let's import some data. 

---

<a name="ImportData"></a>
# ... Importing Data

If you don't have any of your own data to work with, you can find some example datasets here: 

- <a href="http://docs.h2o.ai/resources/publicdata.html"  target="_blank">http://docs.h2o.ai/resources/publicdata.html </a>
- <a href="http://data.h2o.ai" target="_blank">http://data.h2o.ai</a>


There are multiple ways to import data in H2O flow:

- Click the **Assist Me!** button in the **Help** sidebar, then click the **importFiles** link. Enter the file path in the auto-completing **Search** entry field and press **Enter**. Select the file from the search results and select it by clicking the **Add All** link.
 
- You can also drag and drop the file onto the **Search** field in the cell.
  
 ![Flow - Import Files](images/Flow_Import_DragDrop.png)

- In a blank cell, select the CS format, then enter `importFiles ["path/filename.format"]` (where `path/filename.format` represents the complete file path to the file, including the full file name. The file path can be a local file path or a website address. 

After selecting the file to import, the file path displays in the "Search Results" section. To import a single file, click the plus sign next to the file. To import all files in the search results, click the **Add all** link. The files selected for import display in the "Selected Files" section. 

**Note**: If the file is compressed, it will only be read using a single thread. For best performance, we recommend uncompressing the file before importing, as this will allow use of the faster multithreaded distributed parallel reader during import. 

 ![Import Files](images/Flow_import.png)

- To import the selected file(s), click the **Import** button. 

- To remove all files from the "Selected Files" list, click the **Clear All** link. 

- To remove a specific file, click the **X** next to the file path. 

After you click the **Import** button, the raw code for the current job displays. A summary displays the results of the file import, including the number of imported files and their Network File System (nfs) locations. 

 ![Import Files - Results](images/Flow_import_results.png)

##Uploading Data

To upload a local file, click the **Data** menu and select **Upload File...**. Click the **Choose File** button, select the file, click the **Choose** button, then click the **Upload** button. 
  
  ![File Upload Pop-Up](images/Flow_UploadDataset.png)
  
  When the file has uploaded successfully, a message displays in the upper right and the **Setup Parse** cell displays. 

  
  ![File Upload Successful](images/Flow_FileUploadPass.png)

Ok, now that your data is available in H2O Flow, let's move on to the next step: parsing. Click the **Parse these files** button to continue. 

---

<a name="ParseData"></a>
##Parsing Data

After you have imported your data, parse the data.

Select the parser type (if necessary) from the drop-down **Parser** list. For most data parsing, H2O automatically recognizes the data type, so the default settings typically do not need to be changed. The following options are available: 

- Auto
- ARFF
- XLS
- XLSX
- CSV
- SVMLight

If a separator or delimiter is used, select it from the **Separator** list. 

Select a column header option, if applicable: 

- **Auto**: Automatically detect header types.
- **First row contains column names**: Specify heading as column names.
- **First row contains data**: Specify heading as data. This option is selected by default.

Select any necessary additional options: 

- **Enable single quotes as a field quotation character**: Treat single quote marks (also known as apostrophes) in the data as a character, rather than an enum. This option is not selected by default. 
- **Delete on done**: Check this checkbox to delete the imported data after parsing. This option is selected by default. 

A preview of the data displays in the "Data Preview" section. 
 ![Flow - Parse options](images/Flow_parse_setup.png)

**Note**: To change the column type, select the drop-down list at the top of the column and select the data type. The options are: 
  - Unknown
  - Numeric
  - Enum
  - Time
  - UUID
  - String
  - Invalid


After making your selections, click the **Parse** button. 

After you click the **Parse** button, the code for the current job displays. 

 ![Flow - Parse code](images/Flow_parse_code_ex.png)
 
Since we've submitted a couple of jobs (data import & parse) to H2O now, let's take a moment to learn more about jobs in H2O.  
 
--- 
 
<a name="ViewJobs"></a>
## Viewing Jobs

Any command (such as `importFiles`) you enter in H2O is submitted as a job, which is associated with a key. The key identifies the job within H2O and is used as a reference.

### Viewing All Jobs

To view all jobs, click the **Admin** menu, then click **Jobs**, or enter `getJobs` in a cell in CS mode. 

 ![View Jobs](images/Flow_getJobs.png)

The following information displays: 

- Type (for example, `Frame` or `Model`)
- Link to the object 
- Description of the job type (for example, `Parse` or `GBM`)
- Start time
- End time
- Run time

To refresh this information, click the **Refresh** button. To view the details of the job, click the **View** button. 

### Viewing Specific Jobs

To view a specific job, click the link in the "Destination" column. 

![View Job - Model](images/Flow_ViewJob_Model.png)

The following information displays: 

- Type (for example, `Frame`)
- Link to object (key)
- Description (for example, `Parse`)
- Status
- Run time
- Progress

**NOTE**: For a better understanding of how jobs work, make sure to review the [Viewing Frames](#ViewFrames) section as well. 
 
Ok, now that you understand how to find jobs in H2O, let's submit a new one by building a model. 

---

<a name="BuildModel"></a>
# ... Building Models

To build a model: 

- Click the **Assist Me!** button and select **buildModel**

  or 

- Click the **Assist Me!** button, select **getFrames**, then click the **Build Model...** button below the parsed .hex data set

  or 

- Click the **View** button after parsing data, then click the **Build Model** button

  or 

- Click the drop-down **Model** menu and select the model type from the list


The **Build Model...** button can be accessed from any page containing the .hex key for the parsed data (for example, `getJobs` > `getFrame`). 


 ![Model Builder](images/Flow_ModelBuilder.png)

 
In the **Build a Model** cell, select an algorithm from the drop-down menu: 

<a name="Kmeans"></a>
- **K-means**: Create a K-Means model.

<a name="GLM"></a>
- **Generalized Linear Model**: Create a Generalized Linear model.

<a name="DRF"></a>
- **Distributed RF**: Create a distributed Random Forest model.  

<a name="NB"></a>
- **Naïve Bayes**: Create a Naïve Bayes model. 

<a name="PCA"></a> 
- **Principal Component Analysis**: Create a Principal Components Analysis model for modeling without regularization or performing dimensionality reduction. 

<a name="GBM"></a>
- **Gradient Boosting Machine**: Create a Gradient Boosted model

<a name="DL"></a>
- **Deep Learning**: Create a Deep Learning model.

The available options vary depending on the selected model. If an option is only available for a specific model type, the model type is listed. If no model type is specified, the option is applicable to all model types. 

- **Model_ID**: (Optional) Enter a custom name for the model to use as a reference. By default, H2O automatically generates an ID containing the model type (for example, `gbm-6f6bdc8b-ccbc-474a-b590-4579eea44596`). 

- **Training_frame**: (Required) Select the dataset used to build the model. 

  **NOTE**: If you click the **Build a model** button from the `Parse` cell, the training frame is entered automatically. 

- **Validation_frame**: (Optional) Select the dataset used to evaluate the accuracy of the model. 

- **Ignored_columns**: (Optional) Click the plus sign next to a column name to add it to the list of columns excluded from the model. To add all columns, click the **->** button. To remove a column from the list of ignored columns, click the X next to the column name. To remove all columns from the list of ignored columns, click the **<-** button. To search for a specific column, type the column name in the **Search** field above the column list. To only show columns with a specific percentage of missing values, specify the percentage in the **Only show columns with more than 0% missing values** field. 

- **User_points**: [(K-Means](#Kmeans), [PCA)](#PCA) For K-Means, specify the number of initial cluster centers. For PCA, specify the initial Y matrix. 
**Note**: The PCA **User_points** parameter should only be used by advanced users for testing purposes.  

- **Transform**: [(PCA)](#PCA) Select the transformation method for the training data: None, Standardize, Normalize, Demean, or Descale. The default is None. 

- **Response_column**: (Required for [GLM](#GLM), [GBM](#GBM), [DL](#DL), [DRF](#DRF), [Naïve Bayes](#NB)) Select the column to use as the independent variable.

- **Solver**: [(GLM)](#GLM) Select the solver to use (IRLSM, L\_BFGS, or auto). IRLSM is fast on on problems with small number of predictors and for lambda-search with L1 penalty, while [L_BFGS](http://cran.r-project.org/web/packages/lbfgs/vignettes/Vignette.pdf) scales better for datasets with many columns. The default is IRLSM. 

- **Ntrees**: [(GBM](#GBM), [DRF)](#DRF) Specify the number of trees. The default value is 50. 

- **Max\_depth**: [(GBM](#GBM), [DRF)](#DRF) Specify the maximum tree depth. For GBM, the default value is 5. For DRF, the default value is 20. 

- **Min\_rows**: [(GBM)](#GBM), [(DRF)](#DRF) Specify the minimum number of observations for a leaf ("nodesize" in R). For Grid Search, use comma-separated values. The default value is 10.

- **Nbins**: [(GBM](#GBM), [DRF)](#DRF) (Numerical/real/int only) Specify the number of bins for the histogram to build, then split at the best point. The default value is 20. 

- **Nbins_cats**: [(GBM](#GBM), [DRF)](#DRF) (Categorical/enums only) Specify the number of bins for the histogram to build, then split at the best point. Higher values can lead to more overfitting. The default is 100. 

- **R2_stopping**: [(GBM](#GBM), [DRF)](#DRF) Specify a threshold for the coefficient of determination (r^2) metric value. When this threshold is met or exceeded, H2O stops making trees. The default value is 0.999999. 

- **Mtries**: [(DRF)](#DRF) Specify the columns to randomly select at each level. To use the square root of the columns, enter `-1`.  The default value is -1.  

- **Sample\_rate**: [(DRF)](#DRF) Specify the sample rate. The range is 0 to 1.0 and the default value is 0.632. 

- **Build\_tree\_one\_node**: [(DRF)](#DRF) To run on a single node, check this checkbox. This is suitable for small datasets as there is no network overhead but fewer CPUs are used. The default setting is disabled. 

- **Binomial\_double\_trees**: [(DRF)](#DRF) (Binary classification only) Build twice as many trees (one per class). Enabling this option can lead to higher accuracy. 

- **Learn_rate**: [(GBM)](#GBM) Specify the learning rate. The range is 0.0 to 1.0 and the default is 0.1. 

- **Distribution**: [(GBM)](#GBM) Select the distribution type from the drop-down list. The options are auto, bernoulli, multinomial, or gaussian and the default is auto.

- **Loss**: ([DL](#DL)) Select the loss function. For DL, the options are Automatic, MeanSquare, CrossEntropy, Huber, or Absolute and the default value is Automatic. Absolute, MeanSquare, and Huber are applicable for regression or classification, while CrossEntropy is only applicable for classification. Huber can improve for regression problems with outliers.

- **Score\_each\_iteration**: ([K-Means](#Kmeans), [DRF](#DRF), [Naïve Bayes](#NB), [PCA](#PCA), [GBM](#GBM), [GLM](#GLM)) To score during each iteration of the model training, check this checkbox. 

- **K**: [(K-Means)](#Kmeans), [(PCA)](#PCA) For K-Means, specify the number of clusters. For PCA, specify the rank of matrix approximation. The default for K-Means and PCA is 1.  

- **Gamma**: [(PCA)](#PCA) Specify the regularization weight for PCA. The default is 0. 

- **Max_iterations**: [(K-Means](#Kmeans), [PCA](#PCA),[GLM)](#GLM) Specify the number of training iterations. For K-Means and PCA, the default is 1000. For GLM, the default is -1. 
 
- **Objective_epsilon**: [(GLM)](#GLM) Specify a threshold for convergence. If the objective value is less than this threshold, the model is converged. 

- **Beta_epsilon**: [(GLM)](#GLM) Specify the beta epsilon value. If the L1 normalization of the current beta change is below this threshold, consider using convergence. 

- **Gradient_epsilon**: [(GLM)](#GLM) (For L-BFGS only) Specify a threshold for convergence. If the objective value (using the L-infinity norm) is less than this threshold, the model is converged. 

- **Init**: [(K-Means](#Kmeans), [PCA)](#PCA) Select the initialization mode. For K-Means, the options are Furthest, PlusPlus, Random, or User. For PCA, the options are PlusPlus, User, or None. 

  **Note**: If PlusPlus is selected, the initial Y matrix is chosen by the final cluster centers from the K-Means PlusPlus algorithm. 

- **Offset_column**: [(GLM)](#GLM) Select a column to use as the offset. 

- **Weights_column**: [(GLM)](#GLM) Select a column to use for the observation weights. 

- **Family**: [(GLM)](#GLM) Select the model type (Gaussian, Binomial, Poisson, or Gamma).

- **Activation**: [(DL)](#DL) Select the activation function (Tanh, TanhWithDropout, Rectifier, RectifierWithDropout, Maxout, MaxoutWithDropout). The default option is Rectifier. 

- **Hidden**: [(DL)](#DL) Specify the hidden layer sizes (e.g., 100,100). For Grid Search, use comma-separated values: (10,10),(20,20,20). The default value is [200,200]. The specified value(s) must be positive. 

- **Epochs**: ([DL](#DL)) Specify the number of times to iterate (stream) the dataset. The value can be a fraction. The default value for DL is 10.0. 

- **Variable_importances**: ([DL](#DL)) Check this checkbox to compute variable importance. This option is not selected by default. 

- **Laplace**: [(Naïve Bayes)](#NB) Specify the Laplace smoothing parameter. The default value is 0. 

- **Min\_sdev**: [(Naïve Bayes)](#NB) Specify the minimum standard deviation to use for observations without enough data. The default value is 0.001. 

- **Eps\_sdev**: [(Naïve Bayes)](#NB) Specify the threshold for standard deviation. If this threshold is not met, the **min\_sdev** value is used. The default value is 0. 

- **Min\_prob**: [(Naïve Bayes)](#NB) Specify the minimum probability to use for observations without enough data. The default value is 0.001. 

- **Eps\_prob**: [(Naïve Bayes)](#NB) Specify the threshold for standard deviation. If this threshold is not met, the **min\_sdev** value is used. The default value is 0. 

- **Compute_metrics**: [(Naïve Bayes)](#NB) To compute metrics on training data, check this checkbox. The Naïve Bayes classifier assumes independence between predictor variables conditional on the response, and a Gaussian distribution of numeric predictors with mean and standard deviation computed from the training dataset. When building a Naïve Bayes classifier, every row in the training dataset that contains at least one NA will be skipped completely. If the test dataset has missing values, then those predictors are omitted in the probability calculation during prediction. 

- **Non-negative**: [(GLM)](#GLM) To force coefficients to be non-negative, check this checkbox. 

- **Standardize**: ([K-Means](#Kmeans), [GLM](#GLM)) To standardize the numeric columns to have mean of zero and unit variance, check this checkbox. Standardization is highly recommended; if you do not use standardization, the results can include components that are dominated by variables that appear to have larger variances relative to other attributes as a matter of scale, rather than true contribution. This option is selected by default. 

- **Beta_constraints**: ([GLM](#GLM))To use beta constraints, select a dataset from the drop-down menu. The selected frame is used to constraint the coefficient vector to provide upper and lower bounds. 

**Advanced Options**


- **Checkpoint**: [(DL)](#DL) Enter a model key associated with a previously-trained Deep Learning model. Use this option to build a new model as a continuation of a previously-generated model (e.g., by a grid search).

- **Use\_all\_factor\_levels**: ([DL](#DL)) Check this checkbox to use all factor levels in the possible set of predictors; if you enable this option, sufficient regularization is required. By default, the first factor level is skipped. For Deep Learning models, this option is useful for determining variable importances and is automatically enabled if the autoencoder is selected. 

- **Train\_samples\_per\_iteration**: [(DL)](#DL) Specify the number of global training samples per MapReduce iteration. To specify one epoch, enter 0. To specify all available data (e.g., replicated training data), enter -1. To use the automatic values, enter -2. The default is -2. 

- **Adaptive_rate**: [(DL)](#DL) Check this checkbox to enable the adaptive learning rate (ADADELTA). This option is selected by default. If this option is enabled, the following parameters are ignored: `rate`, `rate_decay`, `rate_annealing`, `momentum_start`, `momentum_ramp`, `momentum_stable`, and `nesterov_accelerated_gradient`. 

- **Input\_dropout\_ratio**: [(DL)](#DL) Specify the input layer dropout ratio to improve generalization. Suggested values are 0.1 or 0.2. The range is >= 0 to <1 and the default value is 0. 

- **L1**: [(DL)](#DL) Specify the L1 regularization to add stability and improve generalization; sets the value of many weights to 0. The default value is 0. 

- **L2**: [(DL)](#DL) Specify the L2 regularization to add stability and improve generalization; sets the value of many weights to smaller values. The default value is 0.

- **Score_interval**: [(DL)](#DL) Specify the shortest time interval (in seconds) to wait between model scoring. The default value is 5. 

- **Score\_training\_samples**: [(DL)](#DL) Specify the number of training set samples for scoring. To use all training samples, enter 0. The default value is 10000. 

- **Score\_validation\_samples**: [(DL)](#DL) (Requires selection from the **Validation_Frame** drop-down list) Specify the number of validation set samples for scoring. To use all validation set samples, enter 0. The default value is 0. This option is applicable to classification only. 

- **Score\_duty\_cycle**: [(DL)](#DL) Specify the maximum duty cycle fraction for scoring. A lower value results in more training and a higher value results in more scoring. The default value is 0.1.

- **Autoencoder**: [(DL)](#DL) Check this checkbox to enable the Deep Learning autoencoder. This option is not selected by default. 
   **Note**: This option requires a loss function other than CrossEntropy. If this option is enabled, **use\_all\_factor\_levels** must be enabled. 

- **Balance_classes**: ([GLM](#GLM), [GBM](#GBM), [DRF](#DRF), [DL](#DL), [Naïve Bayes)](#nb) Oversample the minority classes to balance the class distribution. This option is not selected by default. This option is only applicable for classification. Majority classes can be undersampled to satisfy the **Max\_after\_balance\_size** parameter.

- **Max\_confusion\_matrix\_size**: ([DRF](#DRF), [Naïve Bayes](#NB), [GBM](#GBM)) Specify the maximum size (in number of classes) for confusion matrices to be printed in the Logs. 

- **Max\_hit\_ratio\_k**: ([DRF](#DRF), [Naïve Bayes](#NB)) Specify the maximum number (top K) of predictions to use for hit ratio computation. Applicable to multi-class only. To disable, enter 0. 

- **Link**: [(GLM)](#GLM) Select a link function (Identity, Family_Default, Logit, Log, or Inverse).

- **Alpha**: [(GLM)](#GLM) Specify the regularization distribution between L2 and L2. The default value is 0.5. 

- **Lambda**: [(GLM)](#GLM) Specify the regularization strength. There is no default value.  

- **Lambda_search**: [(GLM)](#GLM) Check this checkbox to enable lambda search, starting with lambda max. The given lambda is then interpreted as lambda min. 

- **Rate**: [(DL)](#DL) Specify the learning rate. Higher rates result in less stable models and lower rates result in slower convergence. The default value is 0.005. Not applicable if **adaptive_rate** is enabled. 

- **Rate_annealing**: [(DL)](#DL) Specify the learning rate annealing. The formula is rate/(1+rate\_annealing value \* samples). The default value is 10.000001. Not applicable if **adaptive_rate** is enabled.

- **Momentum_start**: [(DL)](#DL) Specify the initial momentum at the beginning of training. A suggested value is 0.5. The default value is 0. Not applicable if **adaptive_rate** is enabled.

- **Momentum_ramp**: [(DL)](#DL) Specify the number of training samples for increasing the momentum. The default value is 1000000. Not applicable if **adaptive_rate** is enabled.

- **Momentum_stable**: [DL](#DL) Specify the final momentum value reached after the **momentum_ramp** training samples. Not applicable if **adaptive_rate** is enabled. 

- **Nesterov\_accelerated\_gradient**: [(DL)](#DL) Check this checkbox to use the Nesterov accelerated gradient. This option is recommended and selected by default. Not applicable is **adaptive_rate** is enabled. 

- **Hidden\_dropout\_ratios**: [(DL)](#DL) Specify the hidden layer dropout ratios to improve generalization. Specify one value per hidden layer, each value between 0 and 1 (exclusive). There is no default value. This option is applicable only if *TanhwithDropout*, *RectifierwithDropout*, or *MaxoutWithDropout* is selected from the **Activation** drop-down list. 

**Expert Options**

- **Overwrite\_with\_best\_model**: [(DL)](#DL) Check this checkbox to overwrite the final model with the best model found during training. This option is selected by default. 

- **Target\_ratio\_comm\_to\_comp**: [(DL)](#DL) Specify the target ratio of communication overhead to computation. This option is only enabled for multi-node operation and if **train\_samples\_per\_iteration** equals -2 (auto-tuning). The default value is 0.02. 

- **Rho**: [(DL)](#DL) Specify the adaptive learning rate time decay factor. The default value is 0.99. This option is only applicable if **adaptive_rate** is enabled. 

- **Epsilon**: [(DL)](#DL) Specify the adaptive learning rate time smoothing factor to avoid dividing by zero. The default value is 1.0E-8. This option is only applicable if **adaptive_rate** is enabled. 

- **Max_W2**: [(DL)](#DL) Specify the constraint for the squared sum of the incoming weights per unit (e.g., for Rectifier). The default value is infinity. 

- **Initial\_weight\_distribution**: [(DL)](#DL) Select the initial weight distribution (Uniform Adaptive, Uniform, or Normal). The default is Uniform Adaptive. If Uniform Adaptive is used, the **initial\_weight\_scale** parameter is not applicable. 
 
- **Initial\_weight\_scale**: [(DL)](#DL) Specify the initial weight scale of the distribution function for Uniform or Normal distributions. For Uniform, the values are drawn uniformly from initial weight scale. For Normal, the values are drawn from a Normal distribution with the standard deviation of the initial weight scale. The default value is 1.0. If Uniform Adaptive is selected as the **initial\_weight\_distribution**, the **initial\_weight\_scale** parameter is not applicable.

- **Classification_stop**: [(DL)](#DL) (Applicable to discrete/categorical datasets only) Specify the stopping criterion for classification error fractions on training data. To disable this option, enter -1. The default value is 0.0. 

- **Max\_hit\_ratio\_k**: [(DL,)](#DL)[GLM](#GLM) (Classification only) Specify the maximum number (top K) of predictions to use for hit ratio computation (for multi-class only). To disable this option, enter 0. The default value is 10. 

- **Regression_stop**: [(DL)](#DL) (Applicable to real value/continuous datasets only) Specify the stopping criterion for regression error (MSE) on the training data. To disable this option, enter -1. The default value is 0.000001. 

- **Diagnostics**: [(DL)](#DL) Check this checkbox to compute the variable importances for input features (using the Gedeon method). For large networks, selecting this option can reduce speed. This option is selected by default. 

 **Fast_mode**: [(DL)](#DL) Check this checkbox to enable fast mode, a minor approximation in back-propagation. This option is selected by default. 

- **Ignore\_const\_cols**: Check this checkbox to ignore constant training columns, since no information can be gained from them. This option is selected by default. 

- **Force\_load\_balance**: [(DL)](#DL) Check this checkbox to force extra load balancing to increase training speed for small datasets and use all cores. This option is selected by default. 

- **Single\_node\_mode**: [(DL)](#DL) Check this checkbox to force H2O to run on a single node for fine-tuning of model parameters. This option is not selected by default. 

- **Replicate\_training\_data**: [(DL)](#DL) Check this checkbox to replicate the entire training dataset on every node for faster training on small datasets. This option is not selected by default. This option is only applicable for clouds with more than one node. 

- **Shuffle\_training\_data**: [(DL)](#DL) Check this checkbox to shuffle the training data. This option is recommended if the training data is replicated and the value of **train\_samples\_per\_iteration** is close to the number of nodes times the number of rows. This option is not selected by default. 

- **Missing\_values\_handling**: [(DL)](#DL) Select how to handle missing values (Skip or MeanImputation). The default value is MeanImputation. 

- **Quiet_mode**: [(DL)](#DL) Check this checkbox to display less output in the standard output. This option is not selected by default.

- **Sparse**: [(DL)](#DL) Check this checkbox to use sparse iterators for the input layer. This option is not selected by default as it rarely improves performance. 

- **Col_major**: [(DL)](#DL) Check this checkbox to use a column major weight matrix for the input layer. This option can speed up forward propagation but may reduce the speed of backpropagation. This option is not selected by default. 

- **Average_activation**: [(DL)](#DL) Specify the average activation for the sparse autoencoder. The default value is 0. If **Rectifier** is selected as the **Activation** type, this value must be positive. For Tanh, the value must be in (-1,1). 

- **Sparsity_beta**: [(DL)](#DL) Specify the sparsity regularization. The default value is 0. 

- **Max\_categorical\_features**: [(DL)](#DL) Specify the maximum number of categorical features enforced via hashing. The default is unlimited.

- **Reproducible**: [(DL)](#DL) To force reproducibility on small data, check this checkbox. If this option is enabled, the model takes more time to generate, since it uses only one thread. 

- **Export\_weights\_and\_biases**: [(DL)](#DL) To export the neural network weights and biases as H2O frames, check this checkbox. 

- **Class\_sampling\_factors**: ([GLM](#GLM), [DRF](#DRF), [Naïve Bayes)](#NB), [GBM](#GBM), [DL](#DL)) Specify the per-class (in lexicographical order) over/under-sampling ratios. By default, these ratios are automatically computed during training to obtain the class balance. There is no default value. This option is only applicable for classification problems and when **Balance_Classes** is enabled. 

- **Max\_after\_balance\_size**: [DRF](#DRF), [GBM](#GBM), [DL](#DL) Specify the maximum relative size of the training data after balancing class counts (can be less than 1.0). Requires **balance\_classes**. 

- **Seed**: ([K-Means](#Kmeans), [GBM](#GBM), [DL](#DL), [DRF](#DRF)) Specify the random number generator (RNG) seed for algorithm components dependent on randomization. The seed is consistent for each H2O instance so that you can create models with the same starting conditions in alternative configurations. 

- **Prior**: [(GLM)](#GLM) Specify prior probability for y ==1. Use this parameter for logistic regression if the data has been sampled and the mean of response does not reflect reality. The default value is -1. 

- **Max\_active\_predictors**: [(GLM)](#GLM) Specify the maximum number of active predictors during computation. This value is used as a stopping criterium to prevent expensive model building with many predictors. 



---

<a name="ViewModel"></a>
## Viewing Models

Click the **Assist Me!** button, then click the **getModels** link, or enter `getModels` in the cell in CS mode and press **Ctrl+Enter**. A list of available models displays. 

 ![Flow Models](images/Flow_getModels.png)

To view all current models, you can also click the **Model** menu and click **List All Models**. 

To inspect a model, check its checkbox then click the **Inspect** button, or click the **Inspect** button to the right of the model name. 

 ![Flow Model](images/Flow_GetModel.png)
 
 A summary of the model's parameters displays. To display more details, click the **Show All Parameters** button. 
 
   **NOTE**: The **Clone this model...** button will be supported in a future version. 
 
To delete a model, click the **Delete** button. 

To generate a POJO to be able to use the model outside of H2O, click the **Preview POJO** button. 

To learn how to make predictions, continue to the next section. 

---

<a name="Predict"></a>
# ... Making Predictions

After creating your model, click the key link for the model, then click the **Predict** button. 
Select the model to use in the prediction from the drop-down **Model:** menu and the data frame to use in the prediction from the drop-down **Frame** menu, then click the **Predict** button. 

 ![Making Predictions](images/Flow_makePredict.png)


---
 
<a name="ViewPredict"></a>
## Viewing Predictions

Click the **Assist Me!** button, then click the **getPredictions** link, or enter `getPredictions` in the cell in CS mode and press **Ctrl+Enter**. A list of the stored predictions displays. 
To view a prediction, click the **View** button to the right of the model name. 

 ![Viewing Predictions](images/Flow_getPredict.png)

You can also view predictions by clicking the drop-down **Score** menu and selecting **List All Predictions**. 

---

<a name="ViewFrame"></a>
## Viewing Frames

To view a specific frame, click the "Key" link for the specified frame, or enter `getFrameSummary "FrameName"` in a cell in CS mode (where `FrameName` is the name of a frame, such as `allyears2k.hex`.

 ![Viewing specified frame](images/Flow_getFrame.png) 


From the `getFrameSummary` cell, you can: 

- view a truncated list of the rows in the data frame by clicking the **View Data** button
- split the dataset by clicking the **Split...** button
- view the columns, data, and factors in more detail or plot a graph by clicking the **Inspect** button
- create a model by clicking the **Build Model** button
- make a prediction based on the data by clicking the **Predict** button
- download the data as a .csv file by clicking the **Download** button
- view the characteristics or domain of a specific column by clicking the **Summary** link

When you view a frame, you can "drill-down" to the necessary level of detail (such as a specific column or row) using the **Inspect** button or by clicking the links. The following screenshot displays the results of clicking the **Inspect** button for a frame.

![Inspecting Frames](images/Flow_inspectFrame.png)

This screenshot displays the results of clicking the **columns** link. 

![Inspecting Columns](images/Flow_inspectCol.png)


To view all frames, click the **Assist Me!** button, then click the **getFrames** link, or enter `getFrames` in the cell in CS mode and press **Ctrl+Enter**. You can also view all current frames by clicking the drop-down **Data** menu and selecting **List All Frames**. 

A list of the current frames in H2O displays that includes the following information for each frame: 


- Link to the frame (the "key")
- Number of rows and columns
- Size 


For parsed data, the following information displays: 

- Link to the .hex file
- The **Build Model**, **Predict**, and **Inspect** buttons

 ![Parsed Frames](images/Flow_getFrames.png)

To make a prediction, check the checkboxes for the frames you want to use to make the prediction, then click the **Predict on Selected Frames** button. 

---

### Splitting Frames

Datasets can be split within Flow for use in model training and testing. 

 ![splitFrame cell](images/Flow_splitFrame.png)

0. To split a frame, click the **Assist Me** button, then click **splitFrame**.
  **Note**: You can also click the drop-down **Data** menu and select **Split Frame...**.
0. From the drop-down **Frame:** list, select the frame to split. 
0. In the second **Ratio** entry field, specify the fractional value to determine the split. The first **Ratio** field is automatically calculated based on the values entered in the second **Ratio** field. 
   
  **Note**: Only fractional values between 0 and 1 are supported (for example, enter `.5` to split the frame in half). The total sum of the ratio values must equal one. H2O automatically adjusts the ratio values to equal one; if unsupported values are entered, an error displays.  
0. In the **Key** entry field, specify a name for the new frame. 
0. (Optional) To add another split, click the **Add a split** link. To remove a split, click the `X` to the right of the **Key** entry field. 
0. Click the **Create** button.  

---
### Creating Frames

To create a frame with a large amount of random data (for example, to use for testing), click the drop-down **Admin** menu, then select **Create Synthetic Frame**. Customize the frame as needed, then click the **Create** button to create the frame. 

---

### Plotting Frames

To create a plot from a frame, click the **Inspect** button, then click the **Plot** button. 

Select the type of plot (point, path, or rect) from the drop-down **Type** menu, then select the x-axis and y-axis from the following options: 

- label 
- type
- missing 
- zeros
- +Inf
- -Inf
- min
- max
- mean
- sigma
- cardinality

Select one of the above options from the drop-down **Color** menu to display the specified data in color, then click the **Plot** button to plot the data. 

**Note**: Because H2O stores enums internally as numeric then maps the integers to an array of strings, any `min`, `max`, or `mean` values for categorical columns are not meaningful and should be ignored. Displays for categorical data will be modified in a future version of H2O. 

---

<a name="Flows"></a>

# ... Using Flows

You can use and modify flows in a variety of ways:

- Clips allow you to save single cells 
- Outlines display summaries of your workflow
- Flows can be saved, duplicated, loaded, or downloaded

---


<a name="Clips"></a>

## Using Clips

Clips enable you to save cells containing your workflow for later reuse. To save a cell as a clip, click the paperclip icon to the right of the cell (highlighted in the red box in the following screenshot). 
 ![Paperclip icon](images/Flow_clips_paperclip.png)

To use a clip in a workflow, click the "Clips" tab in the sidebar on the right. 

 ![Clips tab](images/Flow_clips.png)

All saved clips, including the default system clips (such as `assist`, `importFiles`, and `predict`), are listed. Clips you have created are listed under the "My Clips" heading. To select a clip to insert, click the circular button to the left of the clip name. To delete a clip, click the trashcan icon to right of the clip name. 

**NOTE**: The default clips listed under "System" cannot be deleted. 

Deleted clips are stored in the trash. To permanently delete all clips in the trash, click the **Empty Trash** button. 

**NOTE**: Saved data, including flows and clips, are persistent as long as the same IP address is used for the cluster. If a new IP is used, previously saved flows and clips are not available. 

---

<a name="Outline"></a>
## Viewing Outlines

The "Outline" tab in the sidebar displays a brief summary of the cells currently used in your flow; essentially, a command history. 

- To jump to a specific cell, click the cell description. 
- To delete a cell, select it and press the X key on your keyboard. 

 ![View Outline](images/Flow_outline.png)

---

<a name="SaveFlow"></a>
## Saving Flows

You can save your flow for later reuse. To save your flow as a notebook, click the "Save" button (the first button in the row of buttons below the flow name), or click the drop-down "Flow" menu and select "Save." 
To enter a custom name for the flow, click the default flow name ("Untitled Flow") and type the desired flow name. A pencil icon indicates where to enter the desired name. 

 ![Renaming Flows](images/Flow_rename.png)

To confirm the name, click the checkmark to the right of the name field. 
 
 ![Confirm Name](images/Flow_rename2.png)

To reuse a saved flow, click the "Flows" tab in the sidebar, then click the flow name. To delete a saved flow, click the trashcan icon to the right of the flow name. 

 ![Flows](images/Flow_flows.png)

### Finding Saved Flows on your Disk
 
By default, flows are saved to the `h2oflows` directory underneath your home directory.  The directory where flows are saved is printed to stdout:
 
```
03-20 14:54:20.945 172.16.2.39:54323     95667  main      INFO: Flow dir: '/Users/<UserName>/h2oflows'
```

To back up saved flows, copy this directory to your preferred backup location.  

To specify a different location for saved flows, use the command-line argument `-flow_dir` when launching H2O:

`java -jar h2o.jar -flow_dir /<New>/<Location>/<For>/<Saved>/<Flows>`  

where `/<New>/<Location>/<For>/<Saved>/<Flows>` represents the specified location.  If the directory does not exist, it will be created the first time you save a flow.

### Saving Flows on a Hadoop cluster

**Note**: If you are running H2O Flow on a Hadoop cluster, H2O will try to find the HDFS home directory to use as the default directory for flows. If the HDFS home directory is not found, flows cannot be saved unless a directory is specified while launching using `-flow_dir`:

`hadoop jar h2odriver.jar -nodes 1 -mapperXmx 1g -output hdfsOutputDirName -flow_dir hdfs:///<Saved>/<Flows>/<Location>`  

The location specified in `flow_dir` may be either an hdfs or regular filesystem directory.  If the directory does not exist, it will be created the first time you save a flow.

### Copying Flows

To create a copy of the current flow, select the **Flow** menu, then click **Make a Copy**. The name of the current flow changes to "Copy of <FlowName>" (where <FlowName> is the name of the flow). You can save the duplicated flow using this name by clicking **Flow** > **Save**. 


### Downloading Flows

After saving a flow as a notebook, click the **Flow** menu, then select **Download...**. A new window opens and the saved flow is downloaded to the default downloads folder on your computer. The file is exported as `<filename>.flow`, where `<filename>` is the name specified when the flow was saved. 

**Caution**: You must have an active internet connection to download flows. 

### Loading Flows

To load a saved flow, click the **Flows** tab in the sidebar at the right. In the pop-up confirmation window that appears, select **Load Notebook**, or click **Cancel** to return to the current flow. 

 ![Confirm Replace Flow](images/Flow_confirmreplace.png)

After clicking **Load Notebook**, the saved flow is loaded. 

To load an exported flow, click the **Flow** menu and select **Open...**. In the pop-up window that appears, click the **Choose File** button and select the exported flow, then click the **Open** button. 

 ![Open Flow](images/Flow_Open.png)

**Notes**: 
- Only exported flows using the default .flow filetype are supported. Other filetypes will not open. 
- If the current notebook has the same name as the selected file, a pop-up confirmation appears to confirm that the current notebook should be overwritten. 

---

<a name="Troubleshooting"></a>
# ...Troubleshooting Flow

To troubleshoot issues in Flow, use the **Admin** menu. The **Admin** menu allows you to check the status of the cluster, view a timeline of events, and view or download logs for issue analysis. 

**NOTE**: To view the current version, click the **Help** menu, then click **About**. 

## Viewing Cluster Status

Click the **Admin** menu, then select **Cluster Status**. A summary of the status of the cluster (also known as a cloud) displays, which includes the same information: 

- Cluster health
- Whether all nodes can communicate (consensus)
- Whether new nodes can join (locked/unlocked)
  
  **Note**: After you submit a job to H2O, the cluster does not accept new nodes. 
- H2O version
- Number of used and available nodes
- When the cluster was created

 ![Cluster Status](images/Flow_CloudStatus.png)


The following information displays for each node:   

- IP address (name)
- Time of last ping
- Number of cores
- Load
- Amount of data (used/total)
- Percentage of cached data
- GC (free/total/max)
- Amount of disk space in GB (free/max)
- Percentage of free disk space 

To view more information, click the **Show Advanced** button. 

---

## Viewing CPU Status (Water Meter)

To view the current CPU usage, click the **Admin** menu, then click **Water Meter (CPU Meter)**. A new window opens, displaying the current CPU use statistics. 

---

## Viewing Logs
To view the logs for troubleshooting, click the **Admin** menu, then click **Inspect Log**. 

 ![Inspect Log](images/Flow_viewLog.png)

To view the logs for a specific node, select it from the drop-down **Select Node** menu. 

---

## Downloading Logs

To download the logs for further analysis, click the **Admin** menu, then click **Download Log**. A new window opens and the logs download to your default download folder. You can close the new window after downloading the logs. Send the logs to [support@h2o.ai](mailto:support@h2o.ai) for issue resolution. 

---

## Viewing Stack Trace Information

To view the stack trace information, click the **Admin** menu, then click **Stack Trace**. 

 ![Stack Trace](images/Flow_stacktrace.png)

To view the stack trace information for a specific node, select it from the drop-down **Select Node** menu. 

---

##Viewing Network Test Results

To view network test results, click the **Admin** menu, then click **Network Test**. 

  ![Network Test Results](images/Flow_NetworkTest.png)

---

## Accessing the Profiler

The Profiler looks across the cluster to see where the same stack trace occurs, and can be helpful for identifying what the currently used CPU is doing. 
To view the profiler, click the **Admin** menu, then click **Profiler**. 

 ![Profiler](images/Flow_profiler.png)

To view the profiler information for a specific node, select it from the drop-down **Select Node** menu. 

---


## Viewing the Timeline

To view a timeline of events in Flow, click the **Admin** menu, then click **Timeline**. The following information displays for each event: 

- Time of occurrence (HH:MM:SS:MS)
- Number of nanoseconds for duration
- Originator of event ("who")
- I/O type
- Event type
- Number of bytes sent & received

 ![Timeline](images/Flow_timeline.png)

To obtain the most recent information, click the **Refresh** button.  

---

## Shutting Down H2O

To shut down H2O, click the **Admin** menu, then click **Shut Down**. A *Shut down complete* message displays in the upper right when the cluster has been shut down. 


