# -*- encoding: utf-8 -*-

class H2OCloudConnectionSettings:
    def __init__(self):
        self._client_id = None
        self._token_endpoint_url = None
        self._refresh_token = None

    @property
    def client_id(self):
        return self._client_id

    @client_id.setter
    def client_id(self, value):
        assert isinstance(value, str)
        self._client_id = value

    @property
    def token_endpoint_url(self):
        return self._token_endpoint_url

    @token_endpoint_url.setter
    def token_endpoint_url(self, value):
        assert isinstance(value, str)
        self._token_endpoint_url = value

    @property
    def refresh_token(self):
        return self._refresh_token

    @refresh_token.setter
    def refresh_token(self, value):
        assert isinstance(value, str)
        self._refresh_token = value

class H2OCloudMLOpsSettings:
    def __init__(self):
        self._api_url = None
        self._project_name = None
        self._project_description = None
        self._deployment_environment = []
        self._estimator = H2OCloudMLOpsEntitySettings()
        self._grid_search = H2OCloudMLOpsEntitySettings()
        self._automl = H2OCloudMLOpsAutoMLSettings()

    @property
    def api_url(self):
        return self._api_url

    @api_url.setter
    def api_url(self, value):
        assert isinstance(value, str)
        self._api_url = value

    @property
    def project_name(self):
        return self._project_name

    @project_name.setter
    def project_name(self, value):
        assert isinstance(value, str)
        self._project_name = value

    @property
    def project_description(self):
        return self._project_description

    @project_description.setter
    def project_description(self, value):
        assert isinstance(value, str)
        self._project_description = value

    @property
    def deployment_environment(self):
        return self._deployment_environment

    @deployment_environment.setter
    def deployment_environment(self, value):
        if isinstance(value, str):
            value = [value, ]
        assert isinstance(value, list) and all(isinstance(elem, str) for elem in value), \
            "The value must be string or list of strings!"
        self._deployment_environment = value

    @property
    def estimator(self):
        return self._estimator

    @estimator.setter
    def estimator(self, value):
        assert isinstance(value, H2OCloudMLOpsEntitySettings)
        self._estimator = value

    @property
    def grid_search(self):
        return self._grid_search

    @grid_search.setter
    def grid_search(self, value):
        assert isinstance(value, H2OCloudMLOpsEntitySettings)
        self._grid_search = value

    @property
    def automl(self):
        return self._automl

    @automl.setter
    def automl(self, value):
        assert isinstance(value, H2OCloudMLOpsAutoMLSettings)
        self._automl = value
    

class H2OCloudMLOpsEntitySettings:

    def __init__(self):
        self._automatic_publishing = False
        self._automatic_deployment = False
        
    @property
    def automatic_publishing(self):
        return self._automatic_publishing
    
    @automatic_publishing.setter
    def automatic_publishing(self, value):
        assert isinstance(value, bool)
        self._automatic_publishing = value
    
    @property
    def automatic_deployment(self):
        return self._automatic_deployment
    
    @automatic_deployment.setter
    def automatic_deployment(self, value):
        assert isinstance(value, bool)
        self._automatic_deployment = value

class H2OCloudMLOpsAutoMLSettings(H2OCloudMLOpsEntitySettings):

    def __init__(self):
        self._publishing_strategy = "best"
        self._automatic_publishing = False
        self._automatic_deployment = False
    
    @property
    def publishing_strategy(self):
        return self._publishing_strategy

    @publishing_strategy.setter
    def publishing_strategy(self, value):
        assert isinstance(value, str)
        self._publishing_strategy = value

class H2OCloudExtensionSettings:

    def __init__(self):
        self._connection: H2OCloudConnectionSettings = H2OCloudConnectionSettings()
        self._mlops: H2OCloudMLOpsSettings = H2OCloudMLOpsSettings()

    @property
    def connection(self):
        return self._connection

    @property
    def mlops(self):
        return self._mlops
