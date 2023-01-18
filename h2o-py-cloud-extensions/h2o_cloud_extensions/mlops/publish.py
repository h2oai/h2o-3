# -*- encoding: utf-8 -*-
import h2o.h2o
from .connect import *
from .utils import *
from .status import _is_model_published
from .deploy import deploy
import warnings
import os
import mimetypes
import h2o_mlops_client
import h2o_cloud_extensions

def publish_estimator_automatically(self):
    if h2o_cloud_extensions.settings.mlops.estimator.automatic_publishing:
        publish_estimator(self)
        
def publish_estimator(self):
    if self.model_id is None:
        warnings.warn("No model has been trained yet!")
        return
    mlops_connection = create_mlops_connection()
    project = get_or_create_project(mlops_connection)
    published = _publish_estimator(self, mlops_connection, project)
    if published and h2o_cloud_extensions.settings.mlops.estimator.automatic_deployment:
        deploy(self)

def _publish_estimator(self, mlops_connection, project):
    if not _is_model_published(mlops_connection, project, self.model_id):
        mojo_file_path = self.save_mojo(TEMPORARY_DIRECTORY_FOR_MOJO_MODELS)
        try:
            artifact = _upload_artifact(mlops_connection, project, mojo_file_path)
            _create_experiment(self, mlops_connection, project, artifact)
            print("The model '%s' has been published to MLOps instance." % self.model_id)
            return True
        finally:
            os.remove(mojo_file_path)
    else:
        warnings.warn("The model '%s' has already been published." % self.model_id)
    return False

def is_model_published(self):
    mlops_connection = create_mlops_connection()
    project = get_or_create_project(mlops_connection)
    return _is_model_published(mlops_connection, project, self.model_id)
    
def _upload_artifact(mlops_connection, project, mojo_file_path):
    artifact = mlops_connection.storage.artifact.create_artifact(
        h2o_mlops_client.StorageCreateArtifactRequest(
            h2o_mlops_client.StorageArtifact(
                entity_id=project.id, mime_type=mimetypes.types_map[".zip"]
            )
        )
    ).artifact

    with open(mojo_file_path, "rb") as mojo_file:
        mlops_connection.storage.artifact.upload_artifact(
            file=mojo_file, artifact_id=artifact.id
        )

    return artifact

def _convert_metadata(input: h2o_mlops_client.IngestMetadata) -> h2o_mlops_client.StorageMetadata:
    values = {}
    for k, v in input.values.items():
        i: h2o_mlops_client.IngestMetadataValue = v

        o = h2o_mlops_client.StorageValue(
            bool_value=i.bool_value,
            double_value=i.double_value,
            duration_value=i.duration_value,
            int64_value=i.int64_value,
            string_value=i.string_value,
            json_value=i.json_value,
            timestamp_value=i.timestamp_value,
        )

        values[k] = o

    return h2o_mlops_client.StorageMetadata(values=values)

def _to_storage_value(value):
    if value is None:
        return h2o_mlops_client.StorageValue(string_value='')
    elif isinstance(value, bool):
        return h2o_mlops_client.StorageValue(bool_value=value)
    elif isinstance(value, float):
        return h2o_mlops_client.StorageValue(double_value=value)
    elif isinstance(value, int):
        return h2o_mlops_client.StorageValue(int64_value=value)
    else:
        return h2o_mlops_client.StorageValue(string_value=str(value))


def _resolve_metrics_metadata(self):
    def get_metrics(prefix, metric_type):
        is_valid = lambda key, val: isinstance(val,(type(None), bool, float, int)) and not str(key).endswith("checksum")
        items = self._model_json["output"][metric_type]._metric_json.items()
        return {prefix + str(key): _to_storage_value(val) for key, val in items if is_valid(key, val)}
    
    output =self._model_json["output"]
    metrics = get_metrics("h2o3/metrics/training/", "training_metrics")
    if output["validation_metrics"]:
        validation_metrics = get_metrics("h2o3/metrics/validation/", "validation_metrics")
        metrics = dict(metrics, **validation_metrics)
    if output["cross_validation_metrics"]:
        cross_validation_metrics = get_metrics("h2o3/metrics/cross_validation/", "cross_validation_metrics")
        metrics = dict(metrics, **cross_validation_metrics)
    return metrics

def _create_experiment(self, mlops_connection, project, artifact):
    ingestion = mlops_connection.ingest.model.create_model_ingestion(
        h2o_mlops_client.IngestModelIngestion(artifact_id=artifact.id)
    ).ingestion

    model_params = h2o_mlops_client.StorageExperimentParameters(
        target_column=self.actual_params.get("response_column", None),
        weight_column=self.actual_params.get("weights_column", None),
        fold_column=self.actual_params.get("fold_column", None),
    )
    mlops_metadata_object = _convert_metadata(ingestion.model_metadata)
    parameters_for_metadata = \
        {"h2o3/parameters/" + str(key): _to_storage_value(val) for key, val in self.actual_params.items()}
    all_metadata = dict(mlops_metadata_object.values, **parameters_for_metadata)
    all_metadata.update(_resolve_metrics_metadata(self))
    all_metadata_object = h2o_mlops_client.StorageMetadata(values=all_metadata)

    experiment = mlops_connection.storage.experiment.create_experiment(
        h2o_mlops_client.StorageCreateExperimentRequest(
            project_id=project.id,
            experiment=h2o_mlops_client.StorageExperiment(
                display_name=self.model_id,
                metadata=all_metadata_object,
                parameters=model_params,
            ),
        )
    ).experiment

    # Linking the artifact to the experiment.
    artifact.entity_id = experiment.id
    artifact.type = ingestion.artifact_type
    
    mlops_connection.storage.artifact.update_artifact(
        h2o_mlops_client.StorageUpdateArtifactRequest(
            artifact=artifact, update_mask="type,entityId"
        )
    )
    return artifact

def publish_grid_search(self):
    if self.models is None or len(self.models) == 0:
        warnings.warn("The grid search instance doesn't contain any trained model.")
    else:
        mlops_connection = create_mlops_connection()
        project = get_or_create_project(mlops_connection)
        published_models=[]
        for model in self.models:
            if _publish_estimator(model, mlops_connection, project):
                published_models.append(model)
        if h2o_cloud_extensions.settings.mlops.grid_search.automatic_deployment:
            for published_model in published_models:
                deploy(published_model)

def is_grid_search_published(self):
    if self.models is None or len(self.models) == 0:
        warnings.warn("The grid search instance doesn't contain any trained model.")
        return True
    else:
        mlops_connection = create_mlops_connection()
        project = get_or_create_project(mlops_connection)
        return all([_is_model_published(mlops_connection, project, model.model_id) for model in self.models])

def publish_grid_search_automatically(self):
    if h2o_cloud_extensions.settings.mlops.grid_search.automatic_publishing:
        publish_grid_search(self)
        
def _get_automl_models(self, strategy):
    if not strategy:
        strategy = h2o_cloud_extensions.settings.mlops.automl.publishing_strategy
    strategy = strategy.lower()
    if not self.leader:
        warnings.warn("AutoML leaderboard doesn't contain any model for publishing.")
        return None
    if strategy == "best":
        return [self.leader, ]
    elif strategy == "all":
        model_ids = [item for sublist in h2o.as_list(self.leaderboard['model_id'])
                     for item in sublist if item != 'model_id']
        return [h2o.get_model(model_id) for model_id in model_ids]
    else:
        warnings.warn("'%s' is not valid strategy for AutoML model publishing." % strategy)
        return None

def publish_automl(self, strategy = None):
    models_to_publish=_get_automl_models(self, strategy)
    if models_to_publish:
        mlops_connection = create_mlops_connection()
        project = get_or_create_project(mlops_connection)
        published_models = []
        for model in models_to_publish:
            if _publish_estimator(model, mlops_connection, project):
                published_models.append(model)
        if h2o_cloud_extensions.settings.mlops.automl.automatic_deployment:
            for published_model in published_models:
                deploy(published_model)

def publish_automl_automatically(self):
    if h2o_cloud_extensions.settings.mlops.automl.automatic_publishing:
        publish_automl(self)

def is_automl_published(self, strategy = None):
    models_to_publish=_get_automl_models(self, strategy)
    if models_to_publish:
        mlops_connection = create_mlops_connection()
        project = get_or_create_project(mlops_connection)
        return all([_is_model_published(mlops_connection, project, model.model_id) for model in models_to_publish])
    else:
        return True
