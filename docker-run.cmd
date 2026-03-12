@REM
@REM Copyright (C) 2022-2026 Philip Helger
@REM
@REM All rights reserved.
@REM

@echo off

docker run -d --name phorm -p 8080:8080 phelger/phorm
if errorlevel 1 goto error

goto end
:error
echo ERROR
:end
