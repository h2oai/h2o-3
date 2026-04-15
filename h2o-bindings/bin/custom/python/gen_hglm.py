def update_param(name, param):
    if name == 'distribution':
        param['values'].remove('custom')
        return param
    return None

def class_extensions():
    def level_2_names(self):
        """
        Get the level 2 column values.
        """
        return self._model_json["output"]["group_column_names"]
    
    def coefs_random_names(self):
        """
        Get the random effect coefficient names including the intercept if applicable.
        """
        return self._model_json["output"]["random_coefficient_names"]
        
    def coefs_random(self):
        """
        Get the random coefficients of the model.
        """
        level_2_names = self.level_2_names()
        random_coefs = self._model_json["output"]["ubeta"]
        return dict(zip(level_2_names, random_coefs))

    def scoring_history_valid(self, as_data_frame=True):
        """
        Retrieve Model Score History for validation data frame if present

        :returns: The validation score history as an H2OTwoDimTable or a Pandas DataFrame.
        """
        model = self._model_json["output"]
        if "scoring_history_valid" in model and model["scoring_history_valid"] is not None:
            if as_data_frame:
                return model["scoring_history_valid"].as_data_frame()
            else:
                return model["scoring_history_valid"]
        print("No validation scoring history for this model")
        
    def matrix_T(self):
        """
        retrieve the T matrix estimated for the random effects. The T matrix is the Tj matrix described in 
        section II.I of the doc.

        :return: The T matrix as a tuple of tuples.
        """
        model = self._model_json["output"]
        return model["tmat"]
    
    def residual_variance(self):
        """
        retrieve the residual variance estimate from the model building process.
        
        :return: residual variance estiamte as a double
        """
        model = self._model_json["output"]
        return model["residual_variance"]

    def icc(self):
        """
        retrieve the icc from the model building process.
            
        :return: icc as an array
        """
        model = self._model_json["output"]
        return model["icc"]
    
    def mean_residual_fixed(self, train = True):
        """
        retrieve the mean residual error using the fixed effect coefficients only.

        :param train: boolean, if true return result from training frame, else return result from validation frame.
        :return: mean residual error as a double.
        """
        model = self._model_json["output"]
        if train:
            return model["mean_residual_fixed"]
        else:
            return model["mean_residual_fixed_valid"]
        
extensions = dict(
    __class__=class_extensions,
)

doc = dict(
    __class__="""
Fits a HGLM model with both the residual noise and random effect being modeled by Gaussian distribution.  The fixed
effect coefficients are specified in parameter x, the random effect coefficients are specified in parameter 
random_columns.  The column specified in group_column will contain the level 2 index value and must be an enum column.
    """)
examples=dict(
    random_columns="""
    >>> import h2o
    >>> from h2o.estimators import H2OHGLMEstimator
    >>> h2o.init()
    >>> prostate_path <- system.file("extdata", "prostate.csv", package = "h2o")
    >>> prostate <- h2o.uploadFile(path = prostate_path)
    >>> prostate$CAPSULE <- as.factor(prostate$CAPSULE)
    >>> hglm_model =H2OHGLMEstimator(random_columns = ["AGE"], group_column = "RACE")
    >>> hglm_model.train(x=c("AGE","RACE","DPROS"), y="CAPSULE", training_frame=prostate)
    """
)


