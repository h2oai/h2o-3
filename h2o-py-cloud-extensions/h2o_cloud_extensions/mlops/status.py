# -*- encoding: utf-8 -*-
from .utils import *
import h2o_mlops_client

def _is_model_published(mlops_connection, project, model_id):
    experiments = mlops_connection.storage.experiment.list_experiments(
        h2o_mlops_client.StorageListExperimentsRequest(
            project_id=project.id,
            filter=QueryUtils.filter_by("display_name", model_id)
        )
    ).experiment
    return len(experiments) > 0

def _is_model_deployed(mlops_connection, project, deployment_name):
    deployments=mlops_connection.deployer.deployment.list_project_deployments(
        h2o_mlops_client.DeployListProjectDeploymentsRequest(
            project_id=project.id,
            paging=h2o_mlops_client.DeployPagingRequest(
                page_size=1000000
            )
        )
    ).deployment
    relevant_deployments=list([deployment for deployment in deployments if deployment.display_name==deployment_name])
    return len(relevant_deployments) > 0
