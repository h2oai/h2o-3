"""
AutoH2O

H2O AutoML is a package intended to automate parts of the model training process

:copyright: (c) 2017 H2O.ai
"""

import h2o
from h2o.job import H2OJob
from h2o.frame import H2OFrame

class H2OAutoML(object):
    """
    Primary driver for AutoML

    :examples:
    >>> # Setting up an AutoML object
    >>> a1 = AutoML(response_column="class", training_path=training_path, build_control=build_control)
    """
    #-------------------------------------------------------------------------------------------------------------------
    # Construction
    #-------------------------------------------------------------------------------------------------------------------
    def __init__(self,
                 response_column=None,
                 training_path=None, training_frame=None,
                 validation_path=None, validation_frame=None,
                 test_path=None, test_frame=None,
                 ignored_columns=None,
                 build_control=None,
                 feature_engineering=None,
                 build_models=None,
                 ensemble_parameters=None):

        try:
            h2o.api("GET /3/Metadata/schemas/AutoMLV99") #Check if H2O jar contains AutoML
        except h2o.exceptions.H2OResponseError as e:
            print e
            print "*******************************************************************\n" \
                  "*Please verify that your H2O jar has the proper AutoML extensions.*\n" \
                  "*******************************************************************\n" \
                  "\nVerbose Error Message:"

        self.build_control = build_control

        # input_spec:
        self.response_column = response_column
        self.training_path = training_path
        self.training_frame = training_frame.frame_id if isinstance(training_frame, h2o.H2OFrame) else training_frame
        self.validation_path = validation_path
        self.validation_frame = validation_frame.frame_id if isinstance(validation_frame, h2o.H2OFrame) else validation_frame
        self.test_path = test_path
        self.test_frame = test_frame.frame_id if isinstance(test_frame, h2o.H2OFrame) else test_frame
        self.ignored_columns = ignored_columns

        if self.training_path is not None and self.training_frame is not None:
            raise ValueError('Both training_path and training_frame are set; please choose one or the other!  Set training_path to the path to a file or directory (including s3, hdfs, ...), or set training_frame to the ID of a Frame that has already been parsed into H2O.')

        if self.training_path is None and self.training_frame is None:
            raise ValueError('Neither training_path nor training_frame are set; please choose one or the other!  Set training_path to the path to a file or directory (including s3, hdfs, ...), or set training_frame to the ID of a Frame that has already been parsed into H2O.')

        if self.validation_path is not None and self.validation_frame is not None:
            raise ValueError('Both validation_path and validation_frame are set; please choose one or the other!  Set validation_path to the path to a file or directory (including s3, hdfs, ...), or set validation_frame to the ID of a Frame that has already been parsed into H2O.')

        if self.test_path is not None and self.test_frame is not None:
            raise ValueError('Both test_path and test_frame are set; please choose one or the other!  Set test_path to the path to a file or directory (including s3, hdfs, ...), or set test_frame to the ID of a Frame that has already been parsed into H2O.')

        if self.response_column is None:
            raise ValueError('The response_column is not set; please set it to the name of the column that you are trying to predict in your data.')

        self.feature_engineering = feature_engineering
        self.build_models = build_models
        self.ensemble_parameters = ensemble_parameters

        self._job=None
        self._automl_key=None # obtained from the job
        self._user_feedback=None
        self._leader_key=None  # the leader model key, fetched
        self._leader_model=None # the leader model
        self._leaderboard=None # the leaderboard

    def learn(self, async=False, verbose=False, print_feedback=False):
        """
        Begins the automl task, which is a background task that incrementally improves
        over time (given by max_run_time). At any point, the user may use the "predict"/"performance"
        to inspect the incremental

        :param bool async: Boolean indicating if the process should wait until the job finishes
        :param bool verbose: Boolean indicating if automl parameters should be printed to the console
        :param bool print_feedback: Boolean indicating if automl feedback should be printed to the console

        :returns: An AutoML object.

        :examples:
        >>> # Set up an AutoML object
        >>> a1 = AutoML(response_column="class", training_path=training_path, build_control=build_control)
        >>> # Launch AutoML
        >>> a1.learn()
        """

        input_spec = {
            'response_column': self.response_column,
        }
        if self.training_path is not None:
            input_spec['training_path'] = {
                'path': self.training_path,
            }
        if self.training_frame is not None:
            input_spec['training_frame'] = self.training_frame

        if self.validation_path is not None:
            input_spec['validation_path'] = {
                'path': self.validation_path,
            }
        if self.validation_frame is not None:
            input_spec['validation_frame'] = self.validation_frame

        if self.test_path is not None:
            input_spec['test_path'] = {
                'path': self.test_path,
            }
        if self.test_frame is not None:
            input_spec['test_frame'] = self.test_frame

        if self.ignored_columns is not None:
            input_spec['ignored_columns'] = self.ignored_columns

        automl_build_params = {
            'input_spec': input_spec,
        }

        # NOTE: if the user hasn't specified some block of parameters don't send them!
        # This lets the back end use the defaults.
        if None is not self.build_control:
            automl_build_params['build_control'] = self.build_control
        if None is not self.feature_engineering:
            automl_build_params['feature_engineering'] = self.feature_engineering
        if None is not self.build_models:
            automl_build_params['build_models'] = self.build_models
        if None is not self.ensemble_parameters:
            automl_build_params['ensemble_parameters'] = self.ensemble_parameters

        if verbose:
            print(automl_build_params)

        resp = h2o.api('POST /99/AutoMLBuilder',json=automl_build_params)
        if 'job' not in resp:
            print("Exception from the back end: ")
            print(resp)
            return

        self._job = H2OJob(resp['job'], "AutoML")
        self._automl_key = self._job.dest_key
        if not async:
            self._job.poll()
            self.fetch()
        self._user_feedback = h2o.api("GET /99/AutoML/"+self._automl_key)["user_feedback_table"]
        if(print_feedback):
            print("\nFull AutoML Feedback for project " + self.project_name() +"\n")
            print(self._user_feedback.as_data_frame())

    def project_name(self):
        """
        Retrieve the project name for an AutoML object

        :return: the project name (a string) of the AutoML object

        :examples:
        >>> # Set up an AutoML object
        >>> a1 = AutoML(response_column="class", training_path=training_path, build_control=build_control)
        >>> # Get the project name
        >>> a1.project_name()
        """
        res = h2o.api("GET /99/AutoML/"+self._automl_key)
        return res["project"]

    def get_leader(self):
        """
        Retrieve the top model from an AutoML object

        :return: an H2O model

        :examples:
        >>> # Set up an AutoML object
        >>> a1 = AutoML(response_column="class", training_path=training_path, build_control=build_control)
        >>> # Launch AutoML
        >>> a1.learn()
        >>> # Get the top model
        >>> a1.get_leader()
        """
        leader = h2o.get_model(self._leader_model)
        return leader

    def get_leaderboard(self):
        """
        Retrieve the leaderboard from an AutoML object

        :return: an H2OFrame with model ids in the first column and evaluation metric in the second column sorted
                 by the evaluation metric

        :examples:
        >>> # Set up an AutoML object
        >>> a1 = AutoML(response_column="class", training_path=training_path, build_control=build_control)
        >>> # Launch AutoML
        >>> a1.learn()
        >>> # Get the leaderboard
        >>> a1.get_leaderboard()
        """
        res = h2o.api("GET /99/AutoML/"+self._automl_key)
        return res["leaderboard_table"]

    def predict(self, test_data):
        """
        Predict on a dataset.

        :param H2OFrame test_data: Data on which to make predictions.

        :returns: A new H2OFrame of predictions.

        :examples:
        >>> #Set up an AutoML object
        >>> a1 = AutoML(response_column="class", training_path=training_path, build_control=build_control)
        >>> #Launch AutoML
        >>> a1.learn()
        >>> #Predict with #1 model from AutoML leaderboard
        >>> a1.predict()

        """
        if self.fetch():
            self._model = h2o.get_model(self._leader_model)
            return self._model.predict(test_data)
        print("No model built yet...")

    #Helper Functions
    def fetch(self):
        res = h2o.api("GET /99/AutoML/"+self._automl_key)
        self._leaderboard = [key["name"] for key in res['leaderboard']['models']]

        if self._leaderboard is not None and len(self._leaderboard) > 0:
            self._leader_model = self._leaderboard[0]
        else:
            self._leader_model = None
        return self._leader_model is not None

