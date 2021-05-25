package water.persist;

public interface S3ClientFactory {

    <T> T newClientInstance();

}
