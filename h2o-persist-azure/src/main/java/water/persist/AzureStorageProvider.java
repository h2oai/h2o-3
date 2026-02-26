package water.persist;

import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import water.util.Log;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides authenticated {@link BlobServiceClient} instances for Azure Blob Storage / ADLS Gen2.
 *
 * Authentication uses {@link DefaultAzureCredential}, which automatically picks up credentials in
 * the following order:
 *   1. Environment variables (AZURE_CLIENT_ID, AZURE_CLIENT_SECRET, AZURE_TENANT_ID)
 *   2. Workload Identity (AZURE_CLIENT_ID, AZURE_TENANT_ID, AZURE_FEDERATED_TOKEN_FILE)
 *   3. Managed Identity (system-assigned or user-assigned via AZURE_CLIENT_ID)
 *   4. Azure CLI credentials (useful for local development)
 *
 * Clients are cached per storage account endpoint to avoid redundant initialization.
 */
final class AzureStorageProvider {

  private final ConcurrentHashMap<String, BlobServiceClient> clientCache = new ConcurrentHashMap<>();

  // Lazily created; shared across all clients since DefaultAzureCredential handles token caching internally
  private volatile DefaultAzureCredential credential;

  /**
   * Returns a {@link BlobServiceClient} for the given storage account endpoint.
   * Clients are cached per endpoint.
   *
   * @param endpoint Storage account endpoint, e.g. {@code https://account.blob.core.windows.net}
   */
  BlobServiceClient getServiceClient(String endpoint) {
    return clientCache.computeIfAbsent(endpoint, this::buildClient);
  }

  BlobContainerClient getContainerClient(String endpoint, String container) {
    return getServiceClient(endpoint).getBlobContainerClient(container);
  }

  private BlobServiceClient buildClient(String endpoint) {
    Log.info("Initializing Azure Blob Storage client for endpoint: " + endpoint);
    return new BlobServiceClientBuilder()
        .endpoint(endpoint)
        .credential(getCredential())
        .buildClient();
  }

  /**
   * Returns the shared {@link DefaultAzureCredential}, creating it on first call.
   * DefaultAzureCredential automatically detects Workload Identity when the environment variables
   * AZURE_CLIENT_ID, AZURE_TENANT_ID, and AZURE_FEDERATED_TOKEN_FILE are set (typically injected
   * by the Azure Workload Identity webhook in Kubernetes).
   */
  private DefaultAzureCredential getCredential() {
    if (credential == null) {
      synchronized (this) {
        if (credential == null) {
          Log.info("Creating DefaultAzureCredential (supports Workload Identity, Managed Identity, " +
              "Service Principal, and Azure CLI)");
          credential = new DefaultAzureCredentialBuilder().build();
        }
      }
    }
    return credential;
  }
}
