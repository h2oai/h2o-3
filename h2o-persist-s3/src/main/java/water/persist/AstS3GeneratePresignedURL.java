package water.persist;

import water.H2O;
import water.rapids.Val;
import water.rapids.ast.AstBuiltin;
import water.rapids.ast.prims.misc.AstSetProperty;
import water.rapids.vals.ValStr;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Date;

/**
 * Exposes S3 resources as a pre-signed URL in a Rapids expression.
 * 
 * Note: this currently doesn't have other practical use other than for debugging.
 * It could be a useful workaround in cases where PersistS3 fails and provide a viable
 * alternative for users to get data in their clusters.
 */
public class AstS3GeneratePresignedURL extends AstBuiltin<AstSetProperty> {

    @Override
    public String[] args() {
        return new String[]{"path", "duration_millis"};
    }

    @Override
    public int nargs() {
        return 1 + 2;
    } // (s3.generate.presigned.URL path duration_millis)

    @Override
    public String str() {
        return "s3.generate.presigned.URL";
    }

    @Override
    protected ValStr exec(Val[] args) {
        final String path = args[1].getStr();
        final long durationMillis = (long) args[2].getNum();

        Persist persist = H2O.getPM().getPersistForURI(URI.create(path));
        if (!(persist instanceof PersistS3)) {
            throw new IllegalArgumentException("Path '" + path + "' cannot be handled by PersistS3.");
        }

        Date expiration = new Date(Instant.now().toEpochMilli() + durationMillis);
        URL presignedURL = ((PersistS3) persist).generatePresignedUrl(path, expiration);
        return new ValStr(presignedURL.toString());
    }

}
