# 模拟支付宝回调测试脚本
# 用于测试支付成功后的库存扣减逻辑

Write-Host "=== 模拟支付宝支付成功回调 ===" -ForegroundColor Green
Write-Host ""
Write-Host "订单ID：5" -ForegroundColor Yellow
Write-Host "回调地址：http://localhost:8080/api/orders/notify" -ForegroundColor Yellow
Write-Host ""

# 构造支付宝回调参数（简化版，不包含签名验证）
$body = @{
    out_trade_no = "5"  # 你的订单号
    trade_no = "2024012322001234567890"  # 支付宝交易号（模拟）
    total_amount = "30.00"
    trade_status = "TRADE_SUCCESS"
}

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/orders/test-notify" `
        -Method POST `
        -Body $body `
        -ContentType "application/x-www-form-urlencoded" `
        -TimeoutSec 10

    Write-Host "✅ 回调成功！" -ForegroundColor Green
    Write-Host "响应内容: $($response.Content)" -ForegroundColor Cyan
} catch {
    Write-Host "❌ 回调失败: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "=== 验证库存是否扣减 ===" -ForegroundColor Green
& mysql -uroot -p123456 tomatomall -e "SELECT product_id, amount, frozen FROM stockpiles WHERE product_id = 51;"
