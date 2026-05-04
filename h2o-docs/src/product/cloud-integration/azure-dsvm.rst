Using H2O-3 with Microsoft Azure Linux Data Science VM
======================================================

The Data Science Virtual Machine (DSVM) for Linux is an Ubuntu-based virtual machine image that simplifies getting started with deep learning on Azure. CNTK, TensorFlow, MXNet, Caffe, Caffe2, DIGITS, H2O-3, Keras, Theano, and Torch are preconfigured and ready to run. The NVIDIA driver, CUDA, and cuDNN are also included. All frameworks are the GPU versions, but they also work on the CPU. Many sample Jupyter notebooks are included.

For a `full list of installed tools for the Linux edition <https://docs.microsoft.com/en-us/azure/machine-learning/machine-learning-data-science-virtual-machine-overview>`__, see the Microsoft documentation site.

To create the H2O Artificial Intelligence VM:

1. Log in to your `Azure portal <https://portal.azure.com>`__, then click the **New** button.

  .. figure:: ../images/azurelin_new.png
     :alt: Create a new VM

2. Search the Marketplace for "H2O". The result shows all of the H2O offerings in the Azure Marketplace. Select the **Data Science Virtual Machine for Linux (Ubuntu)**, then click the **Create** button.

  .. figure:: ../images/azurelin_h2o_dsvm.png
     :alt: Data Science VM for Linux

3. Follow the UI instructions and fill in the basic settings:

   - Username and password that you will use to connect to the VM.
   - Subscription where you want to create the VM.
   - The new resource group name.
   - The location where the VM will be created.
   - Size of the VM.

   For VMs with GPU, select the HDD disk option, then search for the N-Series VM. For more information, see the `GPU on Azure documentation <http://gpu.azure.com/>`__.

4. Wait for the VM solution to be created. The expected creation time is around 10 minutes.

5. After your deployment is created, locate your network security group. By default, your network security group is named **<your VM name>-nsg**. After you locate the security group, look at the inbound security rules.

  .. figure:: ../images/azurelin_inbound_secrules.png
     :alt: Inbound security rules

6. Click **Add** to add a new inbound security rule, then create a TCP rule with port range ``54321``. Click **OK** when you are done.

  .. figure:: ../images/azurelin_add_inbound_secrule.png
     :alt: Add inbound security rule

7. After the new inbound security rule is added, you are ready to start using your Linux DSVM with H2O-3. Connect to your Jupyter Notebooks and look at the examples in the **h2o > python** folder to create your first H2O-3 model on Azure.

   - Connect to Jupyter Notebook by going to ``https://<<VM DNS Name or IP Address>>:8000/``.
   - Connect to H2O Flow by going to ``http://<<VM DNS Name or IP Address>>:54321/``.

Troubleshooting tips
--------------------

- Azure subscriptions by default allow only 20 cores. If you get a validation error on template deployment, this might be the cause. To increase the cores limit, see `Increasing core quota limits in Azure <https://blogs.msdn.microsoft.com/girishp/2015/09/20/increasing-core-quota-limits-in-azure/>`__.
- The OS disk by default is small (approximately 30 GB). In this case, you have approximately 16 GB of free space to start with. This is the same for all VM sizes. We recommend that you add an SSD data disk by following the `Azure documentation for attaching a disk to a Linux VM <https://azure.microsoft.com/en-us/documentation/articles/virtual-machines-linux-classic-attach-disk/>`__.
- Pick a VM size that provides RAM of at least four times the size of your dataset. For more information on Azure VM sizes, see the `Azure virtual machine pricing page <https://azure.microsoft.com/en-us/pricing/details/virtual-machines/>`__.
- The included examples use a ``locate`` function for importing data. This function is not available outside the DSVM environment. For example:

  ::

    air_path = [_locate("smalldata/airlines/allyears2k_headers.zip")]

  To retrieve the datasets, use one of the following methods instead:

  - Replace this path with a pointer to a raw GitHub file at ``https://raw.github.com/h2oai/h2o/master/...``. For example: ``air_path="https://raw.github.com/h2oai/h2o/master/smalldata/airlines/allyears2k_headers.zip"``.
  - Use ``wget`` to retrieve the files.

  A list of the datasets used in the examples — along with their ``locate`` paths — is available in the **h2o > python > README** file.
