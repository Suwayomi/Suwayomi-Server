FROM openjdk:latest

LABEL maintainer "arbuild"

RUN mkdir Tachidesk
RUN  curl -L $(curl -s https://api.github.com/repos/AriaMoradi/Tachidesk/releases/latest | grep -o "https.*jar") -o /Tachidesk/latest.jar
EXPOSE 4567

CMD ["java","-jar","/Tachidesk/latest.jar"]
