import h2o
from h2o.base import Keyed
from ._base import H2OAutoMLBaseMixin


class H2OAutoMLOutput(H2OAutoMLBaseMixin, Keyed):
    """
    AutoML Output object containing the results of AutoML
    """

    def __init__(self, state):
        self._project_name = state['project_name']
        self._key = state['json']['automl_id']['name']
        self._leader = state['leader']
        self._leaderboard = state['leaderboard']
        self._event_log = el = state['event_log']
        self._training_info = {r[0]: r[1]
                               for r in el[el['name'] != '', ['name', 'value']]
                                   .as_data_frame(use_pandas=False, header=False)
                               }

    def __getitem__(self, item):
        if (
            hasattr(self, item) and
            # do not enable user to get anything else than properties through the dictionary interface
            hasattr(self.__class__, item) and
            isinstance(getattr(self.__class__, item), property)
        ):
            return getattr(self, item)
        raise KeyError(item)

    @property
    def project_name(self):
        return self._project_name

    @property
    def leader(self):
        return self._leader

    @property
    def leaderboard(self):
        return self._leaderboard

    @property
    def training_info(self):
        return self._training_info

    @property
    def event_log(self):
        return self._event_log

    #-------------------------------------------------------------------------------------------------------------------
    # Overrides
    #-------------------------------------------------------------------------------------------------------------------
    @property
    def key(self):
        return self._key

    def detach(self):
        self._project_name = None
        h2o.remove(self.leaderboard)
        h2o.remove(self.event_log)
