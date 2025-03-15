package com.hsbc.interview.dto;
/**
 * 响应结果基类
 *
 * @param <T> 响应数据类型
 */
public class BaseResponse<T> {
    private int code;
    private String message;
    private String errMsg;
    private T data;

    // 默认构造函数
    public BaseResponse() {
    }

    // 带参数的构造函数
    public BaseResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // Getter 和 Setter 方法
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

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    // 静态方法用于创建成功的响应
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(200, "Success", data);
    }


}
