# -*- encoding: utf-8 -*-
import h2o_cloud_extensions
import h2o_mlops_client
import time
import h2o
from .status import _is_model_deployed, _is_model_published
from .connect import *
from .utils import *

def deploy(self, environment = None):
    environment = _check_environemt(environment)
    mlops_connection = create_mlops_connection()
    project = get_or_create_project(mlops_connection)
    return _deploy(self, mlops_connection, project, environment)

def _deploy(self, mlops_connection, project, environment):
    if not _is_model_published(mlops_connection, project, self.model_id):
        warnings.warn("The model '%s' has not been published yet!" % self.model_id)
        return

    environments = _resolve_environments(mlops_connection, project, environment)
    experiment =  _get_experiment(mlops_connection, project, self.model_id)
    artifact = _get_artifact(mlops_connection, experiment)
    deployments = []
    for env in environments:
        if _is_model_deployed(mlops_connection, project, self.model_id + "_" + env.display_name):
            warnings.warn("The model '%s' has already been deployed to %s environment." 
                          % (self.model_id, env.display_name))
        else:
            deployments.append((_deploy_artifact(mlops_connection, artifact, project, env, self.model_id), env))
    for (deployment, env) in deployments:
        max_wait_time_secs = 300 # 5 minutes
        result = _wait_for_deployment_become_healthy(
            mlops_connection,
            deployment,
            max_wait_time_secs,
            self.model_id,
            env.display_name)
        if result:
            print("Model '%s' was successfully deployed to %s environment." % (self.model_id, env.display_name))
            return result
        else:
            warnings.warn(
                "Deployment of model '%s' to %s environment failed. "
                "Go to MLOps UI to remove failed deployment with id '%s'." \
                % (self.model_id, env.display_name, deployment.id))
            
            
def _check_environemt(environment):
    if environment is None:
        environment = h2o_cloud_extensions.settings.mlops.deployment_environment
    elif isinstance(environment, str):
        environment = [environment,]
    assert isinstance(environment, list)
    return environment
            
def is_deployed(self, environment=None):
    if environment is None:
        environment = h2o_cloud_extensions.settings.mlops.deployment_environment
    elif isinstance(environment, str):
        environment = [environment,]
    assert isinstance(environment, list)
    mlops_connection = create_mlops_connection()
    project = get_or_create_project(mlops_connection)
    return _is_deployed(self, mlops_connection, project, environment)

def _is_deployed(self, mlops_connection, project, environment):
    return all([_is_model_deployed(mlops_connection, project, self.model_id + "_" + env) for env in environment])



def _resolve_environments(mlops_connection, project, environment_names):
    all_environments = mlops_connection.storage.deployment_environment.list_deployment_environments(
        h2o_mlops_client.StorageListDeploymentEnvironmentsRequest(project.id)
    )
    environments = []    
    for environment_name in environment_names:
        environment = None
        for candidate in all_environments.deployment_environment:
            if candidate.display_name == environment_name:
                environment = candidate
                break
        if environment:
            environments.append(environment)
        else:
            warnings.warn("Environment '%s' does not exist." % environment)
    
    return environments

def _deploy_artifact(mlops_connection, artifact, project, environment, model_name):
    composition = h2o_mlops_client.DeployDeploymentComposition(
        experiment_id=artifact.entity_id,
        artifact_id=artifact.id,
        deployable_artifact_type_name="h2o3_mojo",
        artifact_processor_name="h2o3_mojo_extractor",
        runtime_name="h2o3_mojo_runtime"
    )
    to_deploy = h2o_mlops_client.DeployDeployment(
        project_id=project.id,
        display_name = model_name + "_" + environment.display_name, 
        deployment_environment_id=environment.id,
        single_deployment=h2o_mlops_client.DeploySingleDeployment(
            deployment_composition=composition
        )
    )
    deployed_deployment = mlops_connection.deployer.deployment.create_deployment(
        h2o_mlops_client.DeployCreateDeploymentRequest(deployment=to_deploy)
    ).deployment
    return deployed_deployment

def _wait_for_deployment_become_healthy(mlops_connection, deployment, max_wait_time_secs, model_name, environment):
    svc = mlops_connection.deployer.deployment_status
    status: h2o_mlops_client.DeployDeploymentStatus
    deadline = time.monotonic() + max_wait_time_secs

    while True:
        time.sleep(10)
        status = svc.get_deployment_status(
            h2o_mlops_client.DeployGetDeploymentStatusRequest(deployment_id=deployment.id)
        ).deployment_status
        if (
            status.state == h2o_mlops_client.DeployDeploymentState.HEALTHY
            or time.monotonic() > deadline
        ):
            break
    if status.state == h2o_mlops_client.DeployDeploymentState.HEALTHY:
        return {
            "model_name": model_name,
            "environment": environment,
            "score_url" : status.scorer.score.url,
            "sample_request_url" : status.scorer.sample_request.url,
            "schema_url" : status.scorer.score.url[0:-5] + "schema"
        }
    else:
        None


def _get_experiment(mlops_connection, project, model_id):
    experiments = mlops_connection.storage.experiment.list_experiments(
        h2o_mlops_client.StorageListExperimentsRequest(
            project_id=project.id,
            filter=QueryUtils.filter_by("display_name", model_id)
        )
    ).experiment
    if len(experiments) == 0:
        return None
    if len(experiments) > 1:
        warnings.warn("The MLops instance contains more experiments with "
                      "display_name '%s'. Choosing an experiment with id '%s'." % (model_id, experiments[0].id))
    return experiments[0]

def _get_artifact(mlops_connection, experiment):
    if not experiment:
        return None
    artifacts = mlops_connection.storage.artifact.list_entity_artifacts(
        h2o_mlops_client.StorageListEntityArtifactsRequest(
            entity_id=experiment.id,
        )
    ).artifact
    if len(artifacts) == 0:
        return None
    if len(artifacts) > 1:
        warnings.warn("The MLops instance contains more artifact for the experiment with display_name '%s'. "
                      "Choosing an artifact with id '%s'." % (experiment.display_name, artifacts[0].id))
    return artifacts[0]

def is_grid_search_deployed(self, environment = None):
    if environment is None:
        environment = h2o_cloud_extensions.settings.mlops.deployment_environment
    elif isinstance(environment, str):
        environment = [environment,]
    assert isinstance(environment, list)
    if self.models is None or len(self.models) == 0:
        warnings.warn("The grid search instance doesn't contain any trained model.")
        return True
    else:
        mlops_connection = create_mlops_connection()
        project = get_or_create_project(mlops_connection)
        return all([_is_deployed(model, mlops_connection, project, environment) for model in self.models])

def deploy_grid_search(self, environment = None):
    if is_grid_search_deployed(self, environment):
        warnings.warn("All models of grid_search have already been deployed.") 
        return None
    else:
        environment = _check_environemt(environment)
        mlops_connection = create_mlops_connection()
        project = get_or_create_project(mlops_connection)
        return [_deploy(model, mlops_connection, project, environment) for model in self.models]

def _get_published_automl_models(self, mlops_connection, project):
    if not self.leader:
        warnings.warn("AutoML leaderboard doesn't contain any model.")
        return True
    model_ids = [item for sublist in h2o.as_list(self.leaderboard['model_id'])
                 for item in sublist if item != 'model_id']
    published_models = \
        [h2o.get_model(model_id) for model_id in model_ids if _is_model_published(mlops_connection, project, model_id)]
    return published_models

def is_automl_deployed(self, environment = None):
    environment = _check_environemt(environment)
    mlops_connection = create_mlops_connection()
    project = get_or_create_project(mlops_connection)
    published_models = _get_published_automl_models(self, mlops_connection, project)
    return all([_is_deployed(model, mlops_connection, project, environment) for model in published_models])

def deploy_automl(self, environment = None):
    environment = _check_environemt(environment)
    mlops_connection = create_mlops_connection()
    project = get_or_create_project(mlops_connection)
    published_models = _get_published_automl_models(self, mlops_connection, project)
    if all([_is_deployed(model, mlops_connection, project, environment) for model in published_models]):
        warnings.warn("All published models of automl instance have already been deployed.")
        return None
    else:
        return [_deploy(model, mlops_connection, project, environment) for model in published_models]
