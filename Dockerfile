FROM tomcat:9.0.59-jre11-openjdk

ARG WAR_FILE=build/libs/*.war
COPY ${WAR_FILE} /usr/local/tomcat/webapps/helloworld.war

EXPOSE 8080
CMD ["catalina.sh", "run"]
