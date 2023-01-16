# -*- encoding: utf-8 -*-
import h2o_cloud_extensions
import h2o_mlops_client
import time
from .status import _is_model_deployed, _is_model_published
from .connect import *
from .utils import *

def deploy(self, environment = None):
    if environment is None:
        environment = h2o_cloud_extensions.settings.mlops.deployment_environment
    elif isinstance(environment, str):
        environment = [environment,]
    assert isinstance(environment, list)
    
    mlops_connection = create_mlops_connection()
    project = get_or_create_project(mlops_connection)
    
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
        if _wait_for_deployment_become_healthy(mlops_connection, deployment, max_wait_time_secs):
            print("Model '%s' was successfully deployed to %s environment." % (self.model_id, env.display_name))
        else:
            warnings.warn(
                "Deployment of model '%s' to %s environment failed. "
                "Go to MLOps UI to remove failed deployment with id '%s'." \
                % (self.model_id, env.display_name, deployment.id))
            
def is_model_deployed(self, environment=None):
    if environment is None:
        environment = h2o_cloud_extensions.settings.mlops.deployment_environment
    elif isinstance(environment, str):
        environment = [environment,]
    assert isinstance(environment, list)
    mlops_connection = create_mlops_connection()
    project = get_or_create_project(mlops_connection)
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

def _wait_for_deployment_become_healthy(mlops_connection, deployment, max_wait_time_secs):
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
    return status.state == h2o_mlops_client.DeployDeploymentState.HEALTHY


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
