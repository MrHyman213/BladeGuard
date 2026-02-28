@echo off
REM Устанавливаем кодировку UTF-8 для консоли
chcp 65001 >nul

REM Устанавливаем переменные окружения для Java
set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Duser.language=ru -Duser.country=RU -Dclient.encoding.override=UTF-8
set MAVEN_OPTS=-Dfile.encoding=UTF-8 -Duser.language=ru -Duser.country=RU

echo Запуск приложения с UTF-8 кодировкой...
echo.

mvn spring-boot:run
