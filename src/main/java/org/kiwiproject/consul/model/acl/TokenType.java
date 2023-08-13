package org.kiwiproject.consul.model.acl;

/**
 * @deprecated for removal without replacement, since this was part of the legacy ACL system. For more details see
 * comments in <a href="https://github.com/kiwiproject/consul-client/issues/185">Investigate: TokenType enum is not used</a>
 */
@Deprecated(since = "1.1.0", forRemoval = true)
public enum TokenType {

    CLIENT("client"), MANAGEMENT("management");

    private final String display;

    TokenType(String display) {
        this.display = display;
    }

    public String toDisplay() {
        return this.display;
    }
}
