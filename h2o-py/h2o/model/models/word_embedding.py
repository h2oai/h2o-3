# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
# noinspection PyUnresolvedReferences
from h2o.utils.compatibility import *  # NOQA

from collections import OrderedDict

import h2o
from h2o.expr import ExprNode
from h2o.model import ModelBase


class H2OWordEmbeddingModel(ModelBase):
    """
    Word embedding model.
    """

    def find_synonyms(self, word, count=20):
        """
        Find synonyms using a word2vec model.

        :param str word: A single word to find synonyms for.
        :param int count: The first "count" synonyms will be returned.

        :returns: the approximate reconstruction of the training data.

        :examples:

        >>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
        ...                               col_names = ["category", "jobtitle"], 
        ...                               col_types = ["string", "string"], 
        ...                               header = 1)
        >>> words = job_titles.tokenize(" ")
        >>> w2v_model = H2OWord2vecEstimator(epochs = 10)
        >>> w2v_model.train(training_frame=words)
        >>> synonyms = w2v_model.find_synonyms("teacher", count = 5)
        >>> print(synonyms)
        """
        j = h2o.api("GET /3/Word2VecSynonyms", data={'model': self.model_id, 'word': word, 'count': count})
        return OrderedDict(sorted(zip(j['synonyms'], j['scores']), key=lambda t: t[1], reverse=True))

    def transform(self, words, aggregate_method):
        """
        Transform words (or sequences of words) to vectors using a word2vec model.

        :param str words: An H2OFrame made of a single column containing source words.
        :param str aggregate_method: Specifies how to aggregate sequences of words. If your method is ```NONE```,
               no aggregation is performed and each input word is mapped to a single word-vector.
               If your method is ``'AVERAGE'``, input is treated as sequences of words delimited by NA.
               Each word of a sequences is internally mapped to a vector, and vectors belonging to
               the same sentence are averaged and returned in the result.

        :returns: the approximate reconstruction of the training data.

        :examples:

        >>> job_titles = h2o.import_file(("https://s3.amazonaws.com/h2o-public-test-data/smalldata/craigslistJobTitles.csv"), 
        ...                               col_names = ["category", "jobtitle"], 
        ...                               col_types = ["string", "string"], 
        ...                               header = 1)
        >>> STOP_WORDS = ["ax","i","you","edu","s","t","m","subject","can","lines","re","what",
        ...               "there","all","we","one","the","a","an","of","or","in","for","by","on",
        ...               "but","is","in","a","not","with","as","was","if","they","are","this","and","it","have",
        ...               "from","at","my","be","by","not","that","to","from","com","org","like","likes","so"]
        >>> words = job_titles.tokenize(" ")
        >>> words = words[(words.isna()) | (~ words.isin(STOP_WORDS)),:] 
        >>> w2v_model = H2OWord2vecEstimator(epochs = 10)
        >>> w2v_model.train(training_frame=words)
        >>> job_title_vecs = w2v_model.transform(words, aggregate_method = "AVERAGE")
        """
        j = h2o.api("GET /3/Word2VecTransform", data={'model': self.model_id, 'words_frame': words.frame_id, 'aggregate_method': aggregate_method})
        return h2o.get_frame(j["vectors_frame"]["name"])

    def to_frame(self):
        """
        Converts a given word2vec model into H2OFrame.

        :returns: a frame representing learned word embeddings.

        :examples:

        >>> words = h2o.create_frame(rows=1000,cols=1,string_fraction=1.0,missing_fraction=0.0)
        >>> embeddings = h2o.create_frame(rows=1000,cols=100,real_fraction=1.0,missing_fraction=0.0)
        >>> word_embeddings = words.cbind(embeddings)
        >>> w2v_model = H2OWord2vecEstimator(pre_trained=word_embeddings)
        >>> w2v_model.train(training_frame=word_embeddings)
        >>> w2v_frame = w2v_model.to_frame()
        >>> word_embeddings.names = w2v_frame.names
        >>> word_embeddings.as_data_frame().equals(word_embeddings.as_data_frame())
        """
        return h2o.H2OFrame._expr(expr=ExprNode("word2vec.to.frame", self))
