package com.mulesoft.modules.cors;

import java.io.Serializable;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: juancavallotti
 * Date: 23/10/13
 * Time: 17:11
 * To change this template use File | Settings | File Templates.
 */
public class Origin implements Serializable {

    private String url;

    private List<String> methods;

    private List<String> headers;

    private List<String> exposeHeaders;

    private Long accessControlMaxAge;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public List<String> getExposeHeaders() {
        return exposeHeaders;
    }

    public void setExposeHeaders(List<String> exposeHeaders) {
        this.exposeHeaders = exposeHeaders;
    }

    public Long getAccessControlMaxAge() {
        return accessControlMaxAge;
    }

    public void setAccessControlMaxAge(Long accessControlMaxAge) {
        this.accessControlMaxAge = accessControlMaxAge;
    }
}
