# -*- encoding: utf-8 -*-
from __future__ import absolute_import, division, print_function, unicode_literals
from collections import OrderedDict
from h2o.utils.compatibility import *  # NOQA

from .model_base import ModelBase
from .metrics_base import *  # NOQA
import h2o


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
