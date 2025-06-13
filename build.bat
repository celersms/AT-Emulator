@echo off
SETLOCAL
REM === CONFIG BEGIN =========================================

REM If you have Proguard installed, set the jar location below
SET PROGUARD=C:\Tools\proguard\lib\proguard.jar

REM === CONFIG END ===========================================
TITLE Building AT Emulator...
PUSHD "%~dp0"

REM Resolve the JDK path
SET JMINVER=1.5
SET JDK=%JDK_HOME%
IF EXIST "%JDK%\bin\javac.exe" GOTO JDKFOUND
SET JDK=%JAVA_HOME%
IF EXIST "%JDK%\bin\javac.exe" GOTO JDKFOUND
FOR /f "tokens=2*" %%i IN ('reg query "HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Java Development Kit" /s 2^>nul ^| find "JavaHome"') DO SET JDK=%%j
IF EXIST "%JDK%\bin\javac.exe" GOTO JDKFOUND
FOR /f "tokens=2*" %%i IN ('reg query "HKEY_LOCAL_MACHINE\SOFTWARE\Wow6432Node\JavaSoft\Java Development Kit" /s 2^>nul ^| find "JavaHome"') DO SET JDK=%%j
IF NOT EXIST "%JDK%\bin\javac.exe" GOTO JDKNOTFOUND
:JDKFOUND
IF NOT EXIST "%JDK%\jre\lib\rt.jar" GOTO RTNOTFOUND

REM Get the AT Emulator version
SET VER=0.0.0
FOR /F "tokens=*" %%i IN (src\VERSION.txt) DO SET VER=%%i

REM Compile the AT Emulator
rd /s /q classes >nul 2>nul
mkdir classes\emul 2>nul
"%JDK%\bin\javac" -source %JMINVER% -target %JMINVER% -bootclasspath "%JDK%\jre\lib\rt.jar" -d classes\emul -g:lines -classpath src src\com\celer\emul\AT.java
IF %ERRORLEVEL% NEQ 0 GOTO EXIT

REM Update the build number and generate the Manifest
SET BLD=0
FOR /F "tokens=*" %%i IN (src\Emul.bld) DO SET BLD=%%i
SET /A BLD=BLD+1
(ECHO %BLD%) >src\Emul.bld
ECHO New build number: %BLD%
(
ECHO Manifest-Version: 1.0
ECHO DeliveryID: AT_Emulator_%VER%b%BLD%
ECHO Created-By: CelerSMS
ECHO Main-Class: com.celer.emul.AT
ECHO Copyright: CelerSMS, 2018-2025
ECHO.
) >classes\Emul.MF
mkdir _rel 2>nul

REM Pack the JAR (optionally optimize with Proguard)
IF NOT EXIST "%PROGUARD%" GOTO NOPROGUARD
"%JDK%\bin\jar" cmf classes\Emul.MF emul_in.jar -C classes\emul .
IF %ERRORLEVEL% NEQ 0 GOTO EXIT
for %%x in ("%JDK%\") do set SH_JDK=%%~dpsx
"%JDK%\bin\java" -jar %PROGUARD% -injars emul_in.jar -outjars _rel\AT.jar -libraryjars "%SH_JDK%\jre\lib\rt.jar" @src\Emul.pro
IF %ERRORLEVEL% NEQ 0 GOTO EXIT
del emul_in.jar /q >nul 2>nul
GOTO BLDOK
:NOPROGUARD
"%JDK%\bin\jar" cmf classes\Emul.MF _rel\AT.jar -C classes\emul .
IF %ERRORLEVEL% NEQ 0 GOTO EXIT
:BLDOK
GOTO EXIT

:JDKNOTFOUND
ECHO JDK not found. If you have JDK %JMINVER% or later installed, set JDK_HOME environment variable to the JDK installation directory.
GOTO EXIT
:RTNOTFOUND
ECHO %JDK%\jre\lib\rt.jar not found
:EXIT
pause
POPD
ENDLOCAL
@echo on
