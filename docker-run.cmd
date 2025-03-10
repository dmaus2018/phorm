@REM
@REM Copyright (C) 2022-2025 Philip Helger
@REM
@REM All rights reserved.
@REM

@echo off

docker run -d --name valsvc -p 8080:8080 phelger/valsvc
if errorlevel 1 goto error

goto end
:error
echo ERROR
:end
