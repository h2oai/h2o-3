# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from collections import OrderedDict
from h2o.utils.compatibility import *  # NOQA

from .model_base import ModelBase
from .metrics_base import *  # NOQA
import h2o
from h2o.expr import ExprNode


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
        """
        j = h2o.api("GET /3/Word2VecSynonyms", data={'model': self.model_id, 'word': word, 'count': count})
        return OrderedDict(sorted(zip(j['synonyms'], j['scores']), key=lambda t: t[1], reverse=True))

    def transform(self, words, aggregate_method):
        """
        Transform words (or sequences of words) to vectors using a word2vec model.

        :param str words: An H2OFrame made of a single column containing source words.
        :param str aggregate_method: Specifies how to aggregate sequences of words. If method is `NONE`
               then no aggregation is performed and each input word is mapped to a single word-vector.
               If method is 'AVERAGE' then input is treated as sequences of words delimited by NA.
               Each word of a sequences is internally mapped to a vector and vectors belonging to
               the same sentence are averaged and returned in the result.

        :returns: the approximate reconstruction of the training data.
        """
        j = h2o.api("GET /3/Word2VecTransform", data={'model': self.model_id, 'words_frame': words.frame_id, 'aggregate_method': aggregate_method})
        return h2o.get_frame(j["vectors_frame"]["name"])

    def to_frame(self):
        """
        Converts a given word2vec model into H2OFrame.

        :returns: a frame representing learned word embeddings.
        """
        return h2o.H2OFrame._expr(expr=ExprNode("word2vec.to.frame", self))