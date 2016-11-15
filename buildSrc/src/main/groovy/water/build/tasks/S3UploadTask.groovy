package water.build.tasks;

import org.gradle.api.DefaultTask;

import io.minio.MinioClient
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

class S3UploadTask extends DefaultTask {
    public static String AMAZON_ENDPOINT = "s3.amazonaws.com"

    @Input
    Boolean useHttps = true
    @Input
    String accessKey = System.getenv("AWS_ACCESS_KEY_ID")
    @Input
    String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY")
    @Input
    Boolean debug = false
    @Input
    String bucket
    @Input
    String objectName
    @Input
    String file

    @TaskAction
    public void perform() {
        MinioClient minioClient = new MinioClient(AMAZON_ENDPOINT,
                                                  getAccessKey(),
                                                  getSecretKey(),
                                                  getUseHttps())
        if (getDebug()) {
            minioClient.traceOn(System.err)
        }
        minioClient.putObject(getBucket(), getObjectName(), getFile())
    }
}
