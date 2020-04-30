package water.hive;

import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.security.token.delegation.DelegationTokenIdentifier;
import org.apache.hadoop.security.token.Token;
import org.apache.hadoop.security.token.delegation.AbstractDelegationTokenIdentifier;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Objects;

public class DelegationTokenPrinter {

    private static class PrintingDelegationTokenIdentifier extends AbstractDelegationTokenIdentifier {
        public Text getKind() {
            return new Text("PRINT");
        }
    }

    public static void printToken(final String tokenString) {
        Objects.requireNonNull(tokenString);
        try {
            final Token<DelegationTokenIdentifier> token = new Token<>();
            token.decodeFromUrlString(tokenString);
            final AbstractDelegationTokenIdentifier identifier = new PrintingDelegationTokenIdentifier();
            identifier.readFields(new DataInputStream(new ByteArrayInputStream(token.getIdentifier())));
            System.out.println(
                "token.kind: " + token.getKind() + ", " +
                    "token.service: " + token.getService() + ", " +
                    "id.owner: " + identifier.getOwner() + ", " +
                    "id.renewer: " + identifier.getRenewer() + ", " +
                    "id.realUser: " + identifier.getRealUser() + ", " +
                    "id.issueDate: " + identifier.getIssueDate() + " (" + new Date(identifier.getIssueDate()) + "), " +
                    "id.maxDate: " + identifier.getMaxDate() + " (" + new Date(identifier.getMaxDate()) + "), " +
                    "id.validity: " + (identifier.getMaxDate() - System.currentTimeMillis()) / 3600_000 + " hours");
        } catch (IOException e) {
            System.out.println("Failed to decode token, no debug information will be displayed, cause:" + e.getMessage());
        }
    }

}
