package com.example.tomatomall.order;
import com.example.tomatomall.po.Order;
/** A timeout must receive affirmative closure from the payment provider before releasing attempted payments. */
public interface PaymentGateway {
    enum State { PAID, CLOSED, UNKNOWN }
    record Resolution(State state,String tradeNo,String amount) {
        public static Resolution unknown() { return new Resolution(State.UNKNOWN,null,null); }
        public static Resolution closed() { return new Resolution(State.CLOSED,null,null); }
    }
    Resolution resolveAndClose(Order order);
}
