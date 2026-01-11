package com.example.tomatomall.Util;

import lombok.Data;

@Data
public class ResultUtil {

    private String code;

    private String message;

    private  Object data;
    public static  ResultUtil success(Object data){
        ResultUtil resultUtil = new ResultUtil();
        resultUtil.setCode("200");
        resultUtil.setMessage("请求成功");
        resultUtil.setData(data);
        return resultUtil;
    }
}


