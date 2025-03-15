package com.hsbc.interview.intercept;

import com.hsbc.interview.common.TransException;
import com.hsbc.interview.dto.BaseResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import static com.hsbc.interview.common.Constant.*;

@ControllerAdvice
@Log4j2
// 拦截所有异常
public class GlobalExceptionHandler {

    // 处理自定义异常
    @ExceptionHandler(TransException.class)
    public ResponseEntity<BaseResponse<Void>> handleTransException(TransException ex) {
        BaseResponse<Void> response = new BaseResponse<>();
        response.setCode(ex.getCode());
        response.setMessage("服务器内部错误");
        response.setErrMsg(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // 处理其他异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<Void>> handleException(Exception ex) {
        BaseResponse<Void> response = new BaseResponse<>();
        log.error("服务器内部错误", ex);
        response.setCode(HTTP_FAIL_CODE);
        response.setMessage("服务器内部错误");
        response.setErrMsg(ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
