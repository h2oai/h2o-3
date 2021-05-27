supervised_learning = False


def class_extensions():
    def _requires_training_frame(self):
        """
        Determines if Word2Vec algorithm requires a training frame.
        :return: False.
        """
        return False

    @staticmethod
    def from_external(external=H2OFrame):
        """
        Creates new H2OWord2vecEstimator based on an external model.
        
        :param external: H2OFrame with an external model
        :return: H2OWord2vecEstimator instance representing the external model

        :examples:

        >>> words = h2o.create_frame(rows=10, cols=1,
        ...                          string_fraction=1.0,
        ...                          missing_fraction=0.0)
        >>> embeddings = h2o.create_frame(rows=10, cols=100,
        ...                               real_fraction=1.0,
        ...                               missing_fraction=0.0)
        >>> word_embeddings = words.cbind(embeddings)
        >>> w2v_model = H2OWord2vecEstimator.from_external(external=word_embeddings)
        """
        w2v_model = H2OWord2vecEstimator(pre_trained=external)
        w2v_model.train()
        return w2v_model

    @staticmethod
    def _determine_vec_size(pre_trained):
        """
        Determines vec_size for a pre-trained model after basic model verification.
        """
        first_column = pre_trained.types[pre_trained.columns[0]]

        if first_column != 'string':
            raise H2OValueError("First column of given pre_trained model %s is required to be a String",
                                pre_trained.frame_id)

        if list(pre_trained.types.values()).count('string') > 1:
            raise H2OValueError("There are multiple columns in given pre_trained model %s with a String type.",
                                pre_trained.frame_id)

        return pre_trained.dim[1] - 1


extensions = dict(
    __class__=class_extensions,
)

overrides = dict(
    pre_trained=dict(
        setter="""
pt = self._parms["{sname}"] = H2OFrame._validate({pname}, '{pname}')
if pt is not None:
    self.vec_size = H2OWord2vecEstimator._determine_vec_size(pt)
"""
    )
)

examples = dict(
    epochs="""
>>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
...                               col_names = ["category", "jobtitle"], 
...                               col_types = ["string", "string"], 
...                               header = 1)
>>> words = job_titles.tokenize(" ")
>>> w2v_model = H2OWord2vecEstimator(sent_sample_rate = 0.0, epochs = 10)
>>> w2v_model.train(training_frame=words)
>>> synonyms = w2v_model.find_synonyms("teacher", count = 5)
>>> print(synonyms)
>>>
>>> w2v_model2 = H2OWord2vecEstimator(sent_sample_rate = 0.0, epochs = 1)
>>> w2v_model2.train(training_frame=words)
>>> synonyms2 = w2v_model2.find_synonyms("teacher", 3)
>>> print(synonyms2)
""",
    export_checkpoints_dir="""
>>> import tempfile
>>> from os import listdir
>>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
...                               col_names = ["category", "jobtitle"], 
...                               col_types = ["string", "string"], 
...                               header = 1)
>>> checkpoints_dir = tempfile.mkdtemp()
>>> words = job_titles.tokenize(" ")
>>> w2v_model = H2OWord2vecEstimator(epochs=1,
...                                  max_runtime_secs=10,
...                                  export_checkpoints_dir=checkpoints_dir)
>>> w2v_model.train(training_frame=words)
>>> len(listdir(checkpoints_dir))
""",
    init_learning_rate="""
>>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
...                               col_names = ["category", "jobtitle"], 
...                               col_types = ["string", "string"], 
...                               header = 1)
>>> words = job_titles.tokenize(" ")
>>> w2v_model = H2OWord2vecEstimator(epochs=3, init_learning_rate=0.05)
>>> w2v_model.train(training_frame=words)
>>> synonyms = w2v_model.find_synonyms("assistant", 3)
>>> print(synonyms)
""",
    max_runtime_secs="""
>>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
...                               col_names = ["category", "jobtitle"], 
...                               col_types = ["string", "string"], 
...                               header = 1)
>>> words = job_titles.tokenize(" ")
>>> w2v_model = H2OWord2vecEstimator(epochs=1, max_runtime_secs=10)
>>> w2v_model.train(training_frame=words)
>>> synonyms = w2v_model.find_synonyms("tutor", 3)
>>> print(synonyms)
""",
    min_word_freq="""
>>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
...                               col_names = ["category", "jobtitle"], 
...                               col_types = ["string", "string"], 
...                               header = 1)
>>> words = job_titles.tokenize(" ")
>>> w2v_model = H2OWord2vecEstimator(epochs=1, min_word_freq=4)
>>> w2v_model.train(training_frame=words)
>>> synonyms = w2v_model.find_synonyms("teacher", 3)
>>> print(synonyms)
""",
    norm_model="""
>>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
...                               col_names = ["category", "jobtitle"], 
...                               col_types = ["string", "string"], 
...                               header = 1)
>>> words = job_titles.tokenize(" ")
>>> w2v_model = H2OWord2vecEstimator(epochs=1, norm_model="hsm")
>>> w2v_model.train(training_frame=words)
>>> synonyms = w2v_model.find_synonyms("teacher", 3)
>>> print(synonyms)
""",
    pre_trained="""
>>> words = h2o.create_frame(rows=1000,cols=1,
...                          string_fraction=1.0,
...                          missing_fraction=0.0)
>>> embeddings = h2o.create_frame(rows=1000,cols=100,
...                               real_fraction=1.0,
...                               missing_fraction=0.0)
>>> word_embeddings = words.cbind(embeddings)
>>> w2v_model = H2OWord2vecEstimator(pre_trained=word_embeddings)
>>> w2v_model.train(training_frame=word_embeddings)
>>> model_id = w2v_model.model_id
>>> model = h2o.get_model(model_id)
""",
    sent_sample_rate="""
>>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
...                               col_names = ["category", "jobtitle"], 
...                               col_types = ["string", "string"], 
...                               header = 1)
>>> words = job_titles.tokenize(" ")
>>> w2v_model = H2OWord2vecEstimator(epochs=1, sent_sample_rate=0.01)
>>> w2v_model.train(training_frame=words)
>>> synonyms = w2v_model.find_synonyms("teacher", 3)
>>> print(synonyms)
""",
    training_frame="""
>>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
...                               col_names = ["category", "jobtitle"], 
...                               col_types = ["string", "string"], 
...                               header = 1)
>>> words = job_titles.tokenize(" ")
>>> w2v_model = H2OWord2vecEstimator()
>>> w2v_model.train(training_frame=words)
>>> synonyms = w2v_model.find_synonyms("tutor", 3)
>>> print(synonyms)
""",
    vec_size="""
>>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
...                               col_names = ["category", "jobtitle"], 
...                               col_types = ["string", "string"], 
...                               header = 1)
>>> words = job_titles.tokenize(" ")
>>> w2v_model = H2OWord2vecEstimator(epochs=3, vec_size=50)
>>> w2v_model.train(training_frame=words)
>>> synonyms = w2v_model.find_synonyms("tutor", 3)
>>> print(synonyms)
""",
    window_size="""
>>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
...                               col_names = ["category", "jobtitle"], 
...                               col_types = ["string", "string"], 
...                               header = 1)
>>> words = job_titles.tokenize(" ")
>>> w2v_model = H2OWord2vecEstimator(epochs=3, window_size=2)
>>> w2v_model.train(training_frame=words)
>>> synonyms = w2v_model.find_synonyms("teacher", 3)
>>> print(synonyms)
""",
    word_model="""
>>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
...                               col_names = ["category", "jobtitle"], 
...                               col_types = ["string", "string"], 
...                               header = 1)
>>> words = job_titles.tokenize(" ")
>>> w2v_model = H2OWord2vecEstimator(epochs=3, word_model="skip_gram")
>>> w2v_model.train(training_frame=words)
>>> synonyms = w2v_model.find_synonyms("assistant", 3)
>>> print(synonyms)
"""
)
