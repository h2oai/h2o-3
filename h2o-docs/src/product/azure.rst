Using H2O with Azure - BETA
===========================

Microsoft Azure provides an important collection of cloud services, such as serverless computing, virtual machines, storage options, networking, and much more. Azure provides the tools for a user to create a Data Science environment with H2O. 

This section describes the H2O Application for HDInsight on Microsoft Azure:

**Note**: This feature is currently in Beta and should be used for testing purposes only. 

H2O Artificial Intelligence for HDInsight
-----------------------------------------

The H2O Artificial Intelligence for HDInsight is an application you can install during the creation of a new HDInsight Cluster on Azure. This solution will install Sparkling Water on your spark cluster so you can exploit all the benefits from both Spark and H2O. 

Create the H2O Artificial Intelligence for HDInsight
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Follow the steps below to create a new H2O Artificial Intelligence for HDInsight. 

1. In your Azure portal at `https://portal.azure.com <https://portal.azure.com>`__, search for H2O, and select **H2O Artificial Intelligence for HDInsight**.

2. Click the **Create** button, and follow the UI instructions. 

   **Note** H2O for HDInsight is exclusively for Spark HDI clusters version 3.5 (HDI v3.5). 

   .. figure:: images/azure_select_h2o_hdinsight.png
      :alt: Select H2O Artificial Intelligence for HDInsight

3. On the Applications tab, select and accept the Terms of Use for H2O. 

   .. figure:: images/azure_terms_of_use.png
      :alt: Terms of Use for H2O

4. On the Credentials tab, specify the following: 

   - Cluster Login username and password. These are used to connect to your cluster.
   - SSH Username and password. These are used to connect direcly to the VM present in the cluster.

5. On the Data Source tab, you can configure either a Storage Account or a Data Lake Store. This is where your HDFS system will be located. 

6. On the Cluster Size tab, select the number of workers nodes you want on your HDI Cluster. Note that you can resize your cluster any time after creation. 

7. Click **Create** to begin the cluster creation. Note that the cluster creation process can take up to 40 minutes. 

8. Connect to your Jupyter Notebooks through https://<ClusterName>.azurehdinsight.net/jupyter, and log in using the Cluster Login username and password that you previously created. 

9. In Jupyter, you will see 3 folders: PySparkling Examples, PySpark Examples, and Scala Examples. Select PySparkling Examples.

10. The first step when creating a new notebook is to configure the Spark environment. This information is included in the **4_sentiment_sparkling** example. Note that the configuration will vary based on whether your Spark environment is 2.0 or 1.6. 

   **Spark 2.0**: For Spark 2.0, use the correct jar, and specify the IP address provided by the output of the first cell.

   .. figure:: images/azure_configure_spark_env.png
      :alt: Configure a Spark environment - Spark 2.0

   **Spark 1.6**: For Spark 1.6, use the correct Maven coordinates.

   .. figure:: images/azure_configure_spark_env_1_6.png
      :alt: Configure a Spark environment - Spark 1.6

11. Add the Sparkling Water Egg file.

   .. figure:: images/azure_sw_egg.png
      :alt: Adding the Sparkling Water Egg file

12. Start the H2O Cluster.

   .. figure:: images/azure_start_h2o.png
      :alt: Start the H2O Cluster

You are now ready to start building your H2O Models.

**Note**: To connect to H2O FLOW, go to https://<ClusterName>-h2o.apps.azurehdinsight.net/ (Only available for Spark 2.0.) 
 

H2O Artificial Intelligence for HDInsight Troubleshooting Tips
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

- Make sure that you added the correct Maven coordinates and Python egg file when configuring the Spark environment. 

- Make sure that the cluster has enough resources to allocate to your Spark application. For more information about the cluster available resources, go to http://<ClusterName>.azurehdinsight.net.

