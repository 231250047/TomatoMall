-- Read-only operational checks. Investigate any rows before manual release/replay.
SELECT order_id,status,expires_at,next_check_at,close_attempts,payment_incident
FROM orders WHERE payment_incident IS NOT NULL
   OR (status IN ('PENDING','CLOSING') AND next_check_at < NOW() - INTERVAL 5 MINUTE);
SELECT id,kind,aggregate_id,attempts,last_error,next_attempt_at,lease_until
FROM outbox_event WHERE sent=0 AND (attempts>=5 OR next_attempt_at<NOW()-INTERVAL 5 MINUTE);
SELECT s.product_id,s.amount,s.frozen,COALESCE(r.reserved,0) AS expected_frozen
FROM stockpiles s LEFT JOIN (
    SELECT i.product_id,SUM(i.quantity) AS reserved FROM order_item i
    JOIN orders o ON o.order_id=i.order_id
    WHERE o.status IN ('PENDING','CLOSING') GROUP BY i.product_id
) r ON r.product_id=s.product_id
WHERE s.frozen<>COALESCE(r.reserved,0) OR s.amount<s.frozen OR s.frozen<0;
-- sent=1 means broker accepted, not consumer completed. Keep events through the operational replay window.
