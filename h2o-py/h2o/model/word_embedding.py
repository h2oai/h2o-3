# -*- encoding: utf-8 -*-
"""
Word embedding model.

:copyright: (c) 2017 H2O.ai
:license:   Apache License Version 2.0 (see LICENSE for details)
"""
from __future__ import absolute_import, division, print_function, unicode_literals
from collections import OrderedDict
from h2o.utils.compatibility import *  # NOQA

from .model_base import ModelBase
from .metrics_base import *
import h2o


class H2OWordEmbeddingModel(ModelBase):
    def find_synonyms(self, word, count=20):
        """Find synonyms using a word2vec model.

        Parameters
        ----------
          word : string
            A single word to find synonyms for.

          count : int
            The top "count" synonyms will be returned.

        Returns
        -------
          Return the approximate reconstruction of the training data.
        """
        j = h2o.api("GET /3/Word2VecSynonyms", data={'model': self.model_id, 'word': word, 'count': count})
        return OrderedDict(sorted(zip(j['synonyms'], j['scores']), key=lambda t: t[1], reverse=True))
