package com.example.tomatomall.exception;

import com.example.tomatomall.vo.Response;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

/**
 * @Author: DingXiaoyu
 * @Date: 0:26 2023/11/26
 * 这个类能够接住项目中所有抛出的异常，
 * 使用了RestControllerAdvice切面完成，
 * 表示所有异常出现后都会通过这里。
 * 这个类将异常信息封装到ResultVO中进行返回。
 */

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(value = TomatoMallException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Response<String> handleAIExternalException(TomatoMallException e) {
        e.printStackTrace();
        return Response.buildFailure(e.getMessage(), e.getCode());
    }

    // 处理数据库访问类异常（包含乐观锁、死锁、锁等待等），统一返回并发冲突提示
    @ExceptionHandler(value = DataAccessException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Response<String> handleDataAccessException(DataAccessException e) {
        e.printStackTrace();
        TomatoMallException te = TomatoMallException.concurrentUpdate();
        return Response.buildFailure(te.getMessage(), te.getCode());
    }

    // 处理调用外部 AI 服务等产生的 RestClientException，将其转换为统一的 AI 错误返回
    @ExceptionHandler(value = RestClientException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Response<String> handleRestClientException(RestClientException e) {
        e.printStackTrace();
        TomatoMallException te = TomatoMallException.aiServiceError();
        return Response.buildFailure(te.getMessage(), te.getCode());
    }

    // 通用异常兜底
    @ExceptionHandler(value = Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Response<String> handleGenericException(Exception e) {
        e.printStackTrace();
        return Response.buildFailure("服务器内部错误", "500");
    }
}