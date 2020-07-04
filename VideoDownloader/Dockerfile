FROM openliberty/open-liberty:kernel-java8-openj9-ubi

COPY --chown=1001:0 target/VideoDownloader.war /config/dropins/
COPY --chown=1001:0 src/main/liberty/config/server.xml /config/

EXPOSE 9080
RUN configure.sh
