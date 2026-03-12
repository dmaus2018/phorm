#
# Copyright (C) 2022-2026 Philip Helger
#
# All rights reserved.
#

# Stage 1

FROM ubuntu:latest AS build

# Install wget and unzip
RUN apt-get update \
  && apt-get install -y unzip \
  && rm -rf /var/lib/apt/lists/*

COPY target/*.war phorm.war
RUN unzip phorm.war -d /phorm \
  && rm /phorm/WEB-INF/classes/application.properties \
  && mv /phorm/WEB-INF/classes/application.docker.properties /phorm/WEB-INF/classes/application.properties


# Stage 2

FROM tomcat:10.1-jdk21

ENV CATALINS_OPTS="-Djava.security.egd=file:/dev/urandom"

WORKDIR $CATALINA_HOME/webapps

COPY --from=build /phorm $CATALINA_HOME/webapps/ROOT
