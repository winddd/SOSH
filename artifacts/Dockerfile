# syntax=docker/dockerfile:1

# TraceScope/Boomslang AE image.
# Build from the repository root:
#   docker build -f artifacts/Dockerfile -t boomslang-ae .

FROM eclipse-temurin:17-jdk-focal@sha256:5751a59a993d1011047d52df2b56a961a328596d75f6bea23c68b6647490db20 AS java_base

FROM ubuntu:22.04@sha256:b8b6ee6aa931ecd9d0d952abc34dc0e5f7c6a30c6bb71b079fe399fde0329c02 AS viper_monosat_builder

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       cmake \
       g++ \
       libgmp-dev \
       make \
       python3 \
       python3-setuptools \
       python3-wheel \
       zlib1g-dev \
    && rm -rf /var/lib/apt/lists/*

COPY artifacts/baselines/viper/source/resources/monosat /src/monosat
RUN cmake -S /src/monosat -B /build/monosat \
      -DPYTHON=ON \
      -DBUILD_DYNAMIC=ON \
      -DPYTHON_EXECUTABLE=/usr/bin/python3 \
    && cmake --build /build/monosat --parallel 8 \
    && cd /build/monosat \
    && python3 setup.py bdist_wheel --dist-dir /wheels

# One runtime carries all three verifiers. Cobra's archived configuration uses
# CUDA 11; PolySI and TraceScope do not require GPU access.
FROM nvidia/cuda:11.8.0-base-ubuntu22.04@sha256:f895871972c1c91eb6a896eee68468f40289395a1e58c492e1be7929d0f8703b AS runtime_base

ENV DEBIAN_FRONTEND=noninteractive \
    BOOMSLANG_HOME=/opt/boomslang \
    BOOMSLANG_JAR=/opt/boomslang/boomslang.jar \
    ELLE_JAR=/opt/baselines/elle/elle-cli.jar \
    JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:$PATH \
    JAVA_HEAP=16g \
    HOME=/tmp

# libmonosat.so links against GMP, zlib and the standard C++ runtime. `time`
# is installed because the paper reports maximum resident-set size from
# /usr/bin/time -v.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
       bash \
       ca-certificates \
       cloc \
       coreutils \
       cuda-cudart-11-8 \
       libcublas-11-8 \
       libgmp10 \
       libgmpxx4ldbl \
       libcusparse-11-8 \
       libstdc++6 \
       python3 \
       python3-pip \
       time \
       xauth \
       xvfb \
       zlib1g \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /opt/boomslang

COPY --from=java_base /opt/java/openjdk /opt/java/openjdk
COPY --from=viper_monosat_builder /wheels /tmp/viper-wheels
COPY config.yaml log4j2.xml ./
COPY artifacts/lib/libmonosat.so /usr/local/lib/libmonosat.so
COPY artifacts/container-entrypoint.sh /usr/local/bin/boomslang-entrypoint
COPY artifacts/baselines/cobra/source/target/CobraVerifier-0.0.1-SNAPSHOT-jar-with-dependencies.jar /opt/baselines/cobra/cobra.jar
COPY artifacts/baselines/cobra/source/cobra.conf.default /opt/baselines/cobra/cobra.conf.default
COPY artifacts/baselines/cobra/source/include/libmonosat.so /opt/baselines/cobra/include/libmonosat.so
COPY artifacts/baselines/cobra/source/include/libgpumm.so /opt/baselines/cobra/include/libgpumm.so
COPY artifacts/baselines/polysi/source/build/libs/PolySI-1.0.0-SNAPSHOT.jar /opt/baselines/polysi/polysi.jar
COPY artifacts/baselines/elle/elle-cli-0.1.9-standalone.jar /opt/baselines/elle/elle-cli.jar
COPY artifacts/baselines/viper/source/src /opt/baselines/viper/src
COPY artifacts/baselines/viper/config.artifact.yaml /opt/baselines/viper/config.artifact.yaml
COPY artifacts/baselines/viper/requirements.artifact.txt /tmp/viper-requirements.txt

RUN chmod 0755 /usr/local/bin/boomslang-entrypoint \
    && python3 -m pip install --no-cache-dir /tmp/viper-wheels/*.whl \
    && python3 -m pip install --no-cache-dir -r /tmp/viper-requirements.txt \
    && python3 -c 'import monosat, z3, yaml, numpy, networkx, edn_format, dns' \
    && rm -rf /tmp/viper-wheels /tmp/viper-requirements.txt \
    && java -version \
    && test -s /opt/baselines/cobra/cobra.jar \
    && test -s /opt/baselines/polysi/polysi.jar \
    && test -s /opt/baselines/elle/elle-cli.jar \
    && ldconfig \
    && useradd --create-home --uid 1000 --shell /bin/bash boomslang \
    && mkdir -p /results \
    && chown boomslang:boomslang /results

# Build TraceScope after the stable runtime and baseline layers. Changes to
# TraceScope's Java sources therefore invalidate only this stage and the final
# JAR-copy layer below.
FROM java_base AS builder

WORKDIR /src

# Copy build metadata first so dependency downloads remain cached when only
# source files change.
COPY gradlew settings.gradle.kts ./
COPY gradle ./gradle
COPY app/build.gradle.kts ./app/build.gradle.kts
COPY resources/monosat.jar ./resources/monosat.jar

RUN ./gradlew --no-daemon :app:dependencies

COPY app/src ./app/src
COPY style ./style

RUN ./gradlew --no-daemon \
      -Dorg.gradle.jvmargs="-Xmx16g -XX:MaxMetaspaceSize=2g" \
      :app:shadowJar \
    && test -s app/build/libs/boomslang.jar

FROM runtime_base AS runtime

ARG VCS_REF=unknown
ARG BUILD_DATE=unknown

LABEL org.opencontainers.image.title="TraceScope/Boomslang ATC'26 Artifact" \
      org.opencontainers.image.description="Reproducible runtime for the TraceScope isolation checker" \
      org.opencontainers.image.revision="${VCS_REF}" \
      org.opencontainers.image.created="${BUILD_DATE}"

COPY --from=builder /src/app/build/libs/boomslang.jar /opt/boomslang/boomslang.jar

USER boomslang
WORKDIR /results

VOLUME ["/data", "/results"]
ENTRYPOINT ["/usr/local/bin/boomslang-entrypoint"]
