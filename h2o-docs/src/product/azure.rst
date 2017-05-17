Using H2O with Azure - BETA
===========================

Microsoft Azure provides an important collection of cloud services, such as serverless computing, virtual machines, storage options, networking, and much more. Azure provides the tools for a user to create a Data Science environment with H2O. 

This section describes the H2O Application for HDInsight on Microsoft Azure:

**Note**: This feature is currently in Beta and should be used for testing purposes only. 

H2O Artificial Intelligence for Azure HDInsight
-----------------------------------------------

The H2O Artificial Intelligence for Azure HDInsight is an application you can install during the creation of a new HDInsight cluster on Azure. This solution will install Sparkling Water on your Spark cluster, allowing you to exploit all the benefits from both Spark and H2O. The cluster can access data from Azure Blob storage and/or Azure Data Lake Store in addition to all the standard data sources that H2O supports. It also provides Jupyter Notebooks with pre-baked H2O examples for an easy jumpstart.

Create the H2O AI for Azure HDInsight
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Follow the steps below to create a new H2O Artificial Intelligence for Azure HDInsight. 

1. In your Azure portal at `https://portal.azure.com <https://portal.azure.com>`__, search for H2O, and select **H2O Artificial Intelligence for HDInsight**.

2. Click the **Create** button, and follow the UI instructions. 

   **Note**: H2O for HDInsight is exclusively for Spark HDI clusters version 3.5 (HDI v3.5). 

   .. figure:: images/azure_select_h2o_hdinsight.png
      :alt: Select H2O Artificial Intelligence for HDInsight

3. In the next screen, under **Basics**, change the Cluster Type to Spark 2.0.2. Sparkling Water is currently configured to work only on Spark 2.0 and above.

4. On the **Applications** tab, select and accept the Terms of Use for H2O. 

   .. figure:: images/azure_terms_of_use.png
      :alt: Terms of Use for H2O

5. On the **Credentials** tab, specify the following: 

   - Cluster Login username and password. These are used to connect to your cluster.
   - SSH Username and password. These are used to connect direcly to the VM present in the cluster.

6. On the **Data Source** tab, you can configure either a Blob Storage Account or a Data Lake Store. This is where your HDFS system will be located. 

7. On the **Cluster Size** tab, select the number of workers nodes you want on your HDI Cluster. Note that you can resize your cluster any time after creation. 

8. Click **Create** to begin the cluster creation. Note that the cluster creation process can take up to 30 minutes. 

9. Connect to your Jupyter Notebooks through
   **https://<ClusterName>.azurehdinsight.net/jupyter**, and log in using the Cluster Login username and password that you previously created. 

10. In Jupyter, you will see 3 folders: H2O-PySparkling-Examples, PySpark Examples, and Scala Examples. Select H2O-PySparkling-Examples.

11. The first step when creating a new notebook is to configure the Spark environment. This information is included in all H2O examples on Jupyter.

   .. figure:: images/azure_configure_spark_env.png
      :alt: Configure a Spark environment

12. Start the H2O Cluster.

   .. figure:: images/azure_start_h2o.png
      :alt: Start the H2O Cluster

You are now ready to start building your H2O Models.

**Note**: To connect to H2O Flow, go to **https://<ClusterName>-h2o.apps.azurehdinsight.net/**.
 

Troubleshooting Tips
~~~~~~~~~~~~~~~~~~~~

- If H2O Flow link doesn’t work and only shows the H2O documentation after the H2O cluster creation, clean your browser cache and try again.
- Make sure that the cluster has enough resources to allocate to your Spark application. Do not allocate more than 75% of the worker’s RAM to the spark application, otherwise it can fail.
- For more information about the cluster available resources, go to **http://<ClusterName>.azurehdinsight.net**.

