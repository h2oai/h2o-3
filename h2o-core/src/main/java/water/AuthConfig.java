package water;

public class AuthConfig {

    private static final String DEFAULT_REALM = "h2o";

    private final String username;
    private final String password;
    private final String realm = DEFAULT_REALM;

    public AuthConfig(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRealm() {
        return realm;
    }

}
