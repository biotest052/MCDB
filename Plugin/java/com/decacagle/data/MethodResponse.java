package com.decacagle.data;

public class MethodResponse {

    private int statusCode;
    private String statusMessage;
    private String response;
    private boolean error;

    /**
     * Strucutured output for synchronous requests made on database
     * @param statusCode The status code of the request, follows MDN HTTP standards
     * @param statusMessage A message explaining the status code
     * @param response The output of the request itself (ie readRow.response == {rowData})
     * @param error Boolean declaring whether the request was successful
     */
    public MethodResponse(int statusCode, String statusMessage, String response, boolean error) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.response = response;
        this.error = error;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getResponse() {
        return response;
    }

    public boolean hasError() {
        return error;
    }

}
