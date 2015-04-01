@echo off
REM You can override pass the following parameters to this script:
REM 

set JVM=java

REM  Find location of this script

set SDIR=%~dp0
if "%SDIR:~-1%"=="\" set SDIR=%SDIR:~0,-1%

IF exist %SDIR%\..\..\solr-webapp\webapp\nul (
  echo %SDIR%\..\..\solr-webapp\webapp exists
) ELSE (
  echo -------------------
  echo Unzip server\webapps\solr.war to server\solr-webapp\. to use this script.
  echo Starting Solr via "bin\solr.cmd start" will also do this extraction.
  echo -------------------
)

"%JVM%" -Dlog4j.configuration="file:%SDIR%\log4j.properties" -classpath "%SDIR%\..\..\solr-webapp\webapp\WEB-INF\lib\*;%SDIR%\..\..\lib\ext\*" org.apache.solr.cloud.ZkCLI %*
