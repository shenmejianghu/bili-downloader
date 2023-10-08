package com.bilibili.downloader.pojo;

public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data){
        return new Result<>(200,"操作成功",data);
    }
    public static <T> Result<T> fail(T data,String message){
        return new Result<>(204,message,data);
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public Result() {
        this.code = 200;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
