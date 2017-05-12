Python
------

**I tried to install H2O in Python but ``pip install scikit-learn``
failed - what should I do?**

Use the following commands (prepending with ``sudo`` if necessary):

::

    easy_install pip
    pip install numpy
    brew install gcc
    pip install scipy
    pip install scikit-learn

If you are still encountering errors and you are using OSX, the default
version of Python may be installed. We recommend installing the Homebrew
version of Python instead:

::

    brew install python

If you are encountering errors related to missing Python packages when
using H2O, refer to the following list for a complete list of all Python
packages, including dependencies:

- ``grip``
- ``tabulate``
- ``wheele``
- ``jsonlite``
- ``ipython``
- ``numpy``
- ``scipy``
- ``pandas``
- ``-U gensim``
- ``jupyter``
- ``-U PIL``
- ``nltk``
- ``beautifulsoup4``

--------------

**How do I specify a value as an enum in Python? Is there a Python
equivalent of ``as.factor()`` in R?**

Use ``.asfactor()`` to specify a value as an enum.

--------------

**I received the following error when I tried to install H2O using the
Python instructions on the downloads page - what should I do to resolve
it?**

::

    Downloading/unpacking http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/12/Python/h2o-3.0.0.12-py2.py3-none-any.whl 
      Downloading h2o-3.0.0.12-py2.py3-none-any.whl (43.1Mb): 43.1Mb downloaded 
      Running setup.py egg_info for package from http://h2o-release.s3.amazonaws.com/h2o/rel-shannon/12/Python/h2o-3.0.0.12-py2.py3-none-any.whl 
        Traceback (most recent call last): 
          File "<string>", line 14, in <module> 
        IOError: [Errno 2] No such file or directory: '/tmp/pip-nTu3HK-build/setup.py' 
        Complete output from command python setup.py egg_info: 
        Traceback (most recent call last): 

      File "<string>", line 14, in <module> 

    IOError: [Errno 2] No such file or directory: '/tmp/pip-nTu3HK-build/setup.py' 

    --- 
    Command python setup.py egg_info failed with error code 1 in /tmp/pip-nTu3HK-build

With Python, there is no automatic update of installed packages, so you
must upgrade manually. Additionally, the package distribution method
recently changed from ``distutils`` to ``wheel``. The following
procedure should be tried first if you are having trouble installing the
H2O package, particularly if error messages related to ``bdist_wheel``
or ``eggs`` display.

::

    # this gets the latest setuptools 
    # see https://pip.pypa.io/en/latest/installing.html 
    wget https://bootstrap.pypa.io/ez_setup.py -O - | sudo python 

    # platform dependent ways of installing pip are at 
    # https://pip.pypa.io/en/latest/installing.html 
    # but the above should work on most linux platforms? 

    # on ubuntu 
    # if you already have some version of pip, you can skip this. 
    sudo apt-get install python-pip 

    # the package manager doesn't install the latest. upgrade to latest 
    # we're not using easy_install any more, so don't care about checking that 
    pip install pip --upgrade 

    # I've seen pip not install to the final version ..i.e. it goes to an almost 
    # final version first, then another upgrade gets it to the final version. 
    # We'll cover that, and also double check the install. 

    # after upgrading pip, the path name may change from /usr/bin to /usr/local/bin 
    # start a new shell, just to make sure you see any path changes 

    bash 

    # Also: I like double checking that the install is bulletproof by reinstalling. 
    # Sometimes it seems like things say they are installed, but have errors during the install. Check for no errors or stack traces. 

    pip install pip --upgrade --force-reinstall 

    # distribute should be at the most recent now. Just in case 
    # don't do --force-reinstall here, it causes an issue. 

    pip install distribute --upgrade 


    # Now check the versions 
    pip list | egrep '(distribute|pip|setuptools)' 
    distribute (0.7.3) 
    pip (7.0.3) 
    setuptools (17.0) 


    # Re-install wheel 
    pip install wheel --upgrade --force-reinstall 

After completing this procedure, go to Python and use ``h2o.init()`` to start H2O in Python.

    **Notes**:

    - If you use gradlew to build the jar yourself, you have to start the  jar >yourself before you do ``h2o.init()``.

    - If you download the jar and the H2O package, ``h2o.init()`` will  work like R >and you don't have to start the jar yourself.

--------------

**How should I specify the datatype during import in Python?**

Refer to the following example:

::

    #Let's say you want to change the second column "CAPSULE" of prostate.csv
    #to categorical. You have 3 options.

    #Option 1. Use a dictionary of column names to types. 
    fr = h2o.import_file("smalldata/logreg/prostate.csv", col_types = {"CAPSULE":"Enum"})
    fr.describe()

    #Option 2. Use a list of column types.
    c_types = [None]*9
    c_types[1] = "Enum"
    fr = h2o.import_file("smalldata/logreg/prostate.csv", col_types = c_types)
    fr.describe()

    #Option 3. Use parse_setup().
    fraw = h2o.import_file("smalldata/logreg/prostate.csv", parse = False)
    fsetup = h2o.parse_setup(fraw) 
    fsetup["column_types"][1] = '"Enum"'
    fr = h2o.parse_raw(fsetup) 
    fr.describe()

--------------

**How do I view a list of variable importances in Python?**

Use ``model.varimp(return_list=True)`` as shown in the following example:

::

    model = h2o.gbm(y = "IsDepDelayed", x = ["Month"], training_frame = df)
    vi = model.varimp(return_list=True)
    Out[26]:
    [(u'Month', 69.27436828613281, 1.0, 1.0)]

--------------

**How can I get the H2O Python Client to work with third-party plotting libraries for plotting metrics outside of Flow?**

In Flow, plots are created using the H2O UI and using specific RESTful commands that are issued from the UI. You can obtain similar plotting specific data in Python using a third-party plotting library such as Pandas or Matplotlib. In addition, every metric that H2O displays in the Flow is calculated on the backend and stored for each model. So you can inspect any metric after getting the data from H2O and then using a plotting library in Python to create the graphs. 

The example below shows how to plot the logloss for training and validation using Pandas to store the data and also generate the plot. Pandas has a simplified but limited plotting API, and it is also based on Matplotlib. 

::

    # import pandas and matplotlib
    import pandas as pd
    import matplotlib.pyplot as plt
    %matplotlib inline 

    # get the scoring history for the model
    scoring_history = pd.DataFrame(model.score_history())

    # plot the validation and training logloss
    scoring_history.plot(x='number_of_trees', y = ['validation_logloss', 'training_logloss'])


--------------

**What is PySparkling? How can I use it for grid search or early stopping?**

PySparkling basically calls H2O Python functions for all operations on H2O data frames. You can perform all H2O Python operations available in H2O Python version 3.6.0.3 or later from PySparkling.

For help on a function within IPython Notebook, run ``H2OGridSearch?``

Here is an example of grid search in PySparkling:

::

    from h2o.grid.grid_search import H2OGridSearch
    from h2o.estimators.gbm import H2OGradientBoostingEstimator

    iris = h2o.import_file("/Users/nidhimehta/h2o-dev/smalldata/iris/iris.csv")

    ntrees_opt = [5, 10, 15]
    max_depth_opt = [2, 3, 4]
    learn_rate_opt = [0.1, 0.2]
    hyper_parameters = {"ntrees": ntrees_opt, "max_depth":max_depth_opt,
              "learn_rate":learn_rate_opt}

    gs = H2OGridSearch(H2OGradientBoostingEstimator(distribution='multinomial'), hyper_parameters)
    gs.train(x=range(0,iris.ncol-1), y=iris.ncol-1, training_frame=iris, nfold=10)

    #gs.show
    print gs.sort_by('logloss', increasing=True)

Here is an example of early stopping in PySparkling:

::

    from h2o.grid.grid_search import H2OGridSearch
    from h2o.estimators.deeplearning import H2ODeepLearningEstimator

    hidden_opt = [[32,32],[32,16,8],[100]]
    l1_opt = [1e-4,1e-3]
    hyper_parameters = {"hidden":hidden_opt, "l1":l1_opt}

    model_grid = H2OGridSearch(H2ODeepLearningEstimator, hyper_params=hyper_parameters)
    model_grid.train(x=x, y=y, distribution="multinomial", epochs=1000, training_frame=train,
       validation_frame=test, score_interval=2, stopping_rounds=3, stopping_tolerance=0.05, stopping_metric="misclassification")

--------------

**Do you have a tutorial for grid search in Python?**

Yes, a notebook is available `here <https://github.com/h2oai/h2o-3/blob/master/h2o-py/demos/H2O_tutorial_eeg_eyestate.ipynb>`__ that demonstrates the use of grid search in Python.
