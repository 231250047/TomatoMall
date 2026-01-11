@echo off
set DB=tomatomall
set USER=root

cd /d C:\mysql_export

for /f %%T in ('mysql --default-character-set=utf8mb4 -u%USER% -p -N -e "SHOW TABLES FROM %DB%;"') do (
  echo Exporting %%T ...
  mysql --default-character-set=utf8mb4 -u%USER% -p %DB% ^
    -e "SELECT * FROM `%%T`" ^
    --batch --raw > %%T.csv
)

echo Export finished.
pause
