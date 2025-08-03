package com.decacagle.data;

public class ProtectionCheckResponse {

    private boolean isProtected;
    private int userId;
    private boolean admin;
    private boolean respondedToError;

    public ProtectionCheckResponse(boolean isProtected, int userId, boolean admin, boolean respondedToError) {
        this.isProtected = isProtected;
        this.userId = userId;
        this.admin = admin;
        this.respondedToError = respondedToError;
    }

    public boolean isProtected() {
        return isProtected;
    }

    public int getUserId() {
        return userId;
    }

    public boolean hadError() {
        return respondedToError;
    }

    public boolean isAdmin() {
        return admin;
    }

}
