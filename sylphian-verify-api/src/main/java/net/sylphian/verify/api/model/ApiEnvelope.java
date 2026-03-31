package net.sylphian.verify.api.model;

import java.util.List;
import java.util.Map;

public class ApiEnvelope<T> {
    private boolean success;
    private T data;
    private String message;
    private Map<String, List<String>> errors;
    private Map<String, Object> meta;

    public boolean isSuccess() {
        return success;
    }

    public T getData() {
        return data;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, List<String>> getErrors() {
        return errors;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }
}
