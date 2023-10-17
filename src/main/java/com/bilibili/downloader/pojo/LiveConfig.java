package com.bilibili.downloader.pojo;


public class LiveConfig {
    //推流地址
    private String url;
    private String secret;
    //循环次数，-1表示无限循环
    private Integer loop;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getLoop() {
        return loop;
    }

    public void setLoop(Integer loop) {
        this.loop = loop;
    }

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
