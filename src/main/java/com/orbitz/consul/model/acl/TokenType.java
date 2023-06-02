package com.orbitz.consul.model.acl;

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
