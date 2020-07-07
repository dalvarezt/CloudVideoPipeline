FROM openliberty/open-liberty:kernel-java8-openj9-ubi
LABEL \
  org.opencontainers.image.authors="Daniel Alvarez Torres" \
  org.opencontainers.image.vendor="IBM" \
  org.opencontainers.image.url="local" \
  org.opencontainers.image.source="https://github.com/dalvarezt/CloudVideoPipeline/VideoDownloader" \
  org.opencontainers.image.version="$VERSION" \
  org.opencontainers.image.revision="$REVISION" \
  vendor="Open Liberty" \
  name="system" \
  version="$VERSION-$REVISION" \
  summary="Generates video from images stored at IBM Cloud COS" 

COPY --chown=1001:0 target/*.war /config/apps/
COPY --chown=1001:0 src/main/liberty/config/ /config/

EXPOSE 9080
RUN configure.sh
