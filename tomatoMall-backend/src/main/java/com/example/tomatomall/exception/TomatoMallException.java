package com.example.tomatomall.exception;

public class TomatoMallException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private String code;

    public TomatoMallException(String message,String code) {
        super(message);
        this.code = code;
    }

    public static TomatoMallException productNotExist(){
        return new TomatoMallException("商品不存在","400");
    }

    public static TomatoMallException notLogin(){
        return new TomatoMallException("未登�?", "401");
    }

    public static TomatoMallException usernameAlreadyExists(){
        return new TomatoMallException("用户已存�?", "400");
    }

    public static TomatoMallException usernameError(){
        return new TomatoMallException("用户未注�?", "400");
    }

    public static TomatoMallException passwordError(){
        return new TomatoMallException("密码不正�?", "400");
    }

    public static TomatoMallException cartItemNotExist(){
        return new TomatoMallException("购物车商品不存在!", "400");
    }

    public  static TomatoMallException stockpileNotExist(){
        return new TomatoMallException("库存不存�?", "400");
    }

    public static TomatoMallException stockpileNotEnough(){
        return new TomatoMallException("库存不足!", "400");
    }
    public static TomatoMallException aliPayError(){return new TomatoMallException("生成订单错误","400");}

    public static TomatoMallException advertisementIdNull(){
        return new TomatoMallException("广告id为空!", "400");
    }

    public static TomatoMallException advertisementNotExist(){
        return new TomatoMallException("广告不存�?", "400");
    }


    public static TomatoMallException commentNotExist(){
        return new TomatoMallException("评论不存�?", "400");
    }

    public static TomatoMallException deleteCommentError(){
        return new TomatoMallException("删除评论失败!", "400");
    }
    public static TomatoMallException discountTooMuch(){
        return new TomatoMallException("折扣不合�?", "400");
    }

    public static TomatoMallException concurrentUpdate() {
        return new TomatoMallException("并发更新冲突，请重试", "409");
    }

    public static TomatoMallException orderNotExist() {
        return new TomatoMallException("订单不存在", "400");
    }

    public static TomatoMallException aiServiceError() {
        return new TomatoMallException("外部AI服务调用失败", "502");
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "TomatoMallException{code='" + code + "', message='" + getMessage() + "'}";
    }
}
