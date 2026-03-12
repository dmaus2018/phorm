@REM
@REM Copyright (C) 2022-2026 Philip Helger
@REM
@REM All rights reserved.
@REM

@echo off

docker build --pull -t phelger/phorm .
if errorlevel 1 goto error

docker tag phelger/phorm phelger/valsvc

goto end
:error
echo ERROR
:end
