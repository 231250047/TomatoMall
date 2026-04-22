@echo off
echo ========================================
echo Testing DeepSeek API Key
echo ========================================
echo.

set API_KEY=sk-a40956d6f83c4cac9f3dcb13b711bf3f

echo API Key: %API_KEY:~0,20%...
echo.
echo Sending test request to DeepSeek...
echo.

curl -X POST "https://api.deepseek.com/chat/completions" ^
  -H "Content-Type: application/json" ^
  -H "Authorization: Bearer %API_KEY%" ^
  -d "{\"model\": \"deepseek-chat\", \"messages\": [{\"role\": \"user\", \"content\": \"你好\"}], \"max_tokens\": 50}"

echo.
echo ========================================
echo Test completed
echo ========================================
pause
