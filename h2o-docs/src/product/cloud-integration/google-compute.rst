.. _install-on-google-cloud:

Install H2O on the Google Cloud Platform offering
=================================================

This section describes how to install and start H2O Flow (H2O-3 web offering) in a Google Compute environment using the available Cloud Launcher offering.

Before you begin
----------------

If you are trying GCP for the first time and have just created an account, please check your Google Compute Engine (GCE) resource quota limits. Our default recommendation for launching H2O-3 is 4 CPUs, 15 GB RAM, and 3 nodes. By default, GCP allocates a maximum of 8 CPUs, so most H2O-3 users can easily run without reaching a quota limit. Some users of H2O, however, may require more resources. If necessary, you can change these settings to match your quota limit, or you can request more resources from GCP. See the `documentation on quotas <https://cloud.google.com/compute/quotas>`__ for more information,including information on how to check your quota and request additional quota.

Installation procedure
----------------------

1. In your browser, `log in to the Google Compute Engine Console <https://console.cloud.google.com/>`__. 

2. In the left navigation panel, select **Marketplace**.

  .. image:: ../images/google_cloud_marketplace.png
     :alt: Google Cloud Platform sidebar showing the location of Marketplace.
     :align: center
     :scale: 70%

3. On the Cloud Launcher page, search for **H2O** and select the H2O-3 offering. 

  .. image:: ../images/google_h2o_offering.png
     :alt: Google Cloud Platform H2O-3 cluster launcher page with blue button to launch on compute engine. There is an overview section and information about H2O.ai and its pricing.
     :align: center

4. Click **Launch on Compute Engine**.

 - Specify a name for this deployment.
 - Select a zone for the deployment.
 - Select or customize a machine type and memory amount.
 - Specify the number of nodes for the virtual machine.
 - Specify the boot disk type and size (in GB).
 - Specify the network and subnetwork names. 

5. Click **Deploy** when you are done. H2O-3 will begin deploying. This can take several minutes. 

 .. image:: ../images/google_deploy_compute_engine.png
  :alt: H2O-3 cluster launcher deployment page with the deloyment settings on the left and the cluster launcher overview on the right.
  :align: center

6. A summary page displays when the compute engine is successfully deployed. This page includes the instance ID and the username (always **h2oai**) and password that will be required when starting H2O-3. Click on the Instance link to retrieve the external IP address for starting H2O-3.

  .. image:: ../images/google_deploy_summary.png
     :alt: H2O-3 cluster summary page outlining cluster information (such as instance ID, instance zone, username/password, etc), get started next steps, a link to documentation, and support.
     :align: center

7. Connect to to the H2O-3 Cluster using one of the following methods:

.. tabs::
  .. group-tab:: Flow

    In your browser, go to https://[External_IP]:54321 to start Flow. Enter your username and password when prompted. 

    .. note:: 

      When starting H2O Flow, you may receive a message indicating that the connection is not private. Note that the connection is secure and encrypted, but H2O uses a self-signed certificate to handle Nginx encryption, which prompts the warning. You can avoid this message by using your own self-signed certificate. 

  .. group-tab:: Python

    .. code-block:: bash

       h2o.connect(url="https://[external ip]:54321", auth=(username, password))

  .. group-tab:: R

    .. code-block:: bash

       h2o.connect(ip="[external ip]", port=54321, https=TRUE, username=username, password=password)

