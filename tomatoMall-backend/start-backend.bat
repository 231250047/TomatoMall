@echo off
echo Setting JAVA_HOME to Java 17...
set JAVA_HOME=D:\JDK\jdk-17
set PATH=%JAVA_HOME%\bin;%PATH%

echo Verifying Java version...
java -version

echo.
echo Starting Spring Boot application with Java 17...
echo.

cd /d "%~dp0"
call mvn spring-boot:run

pause