# 使用官方 Ubuntu 基础镜像（轻量且兼容性好）
FROM ubuntu:22.04

# 安装必要工具（wget用于下载JDK，vim可选）
RUN apt-get update && apt-get install -y wget vim

# 下载并安装 JDK 21
RUN wget https://download.java.net/java/GA/jdk21/fd2272bbf8e04c3dbaee13770090416c/35/GPL/openjdk-21_linux-x64_bin.tar.gz && \
    tar -xzf openjdk-21_linux-x64_bin.tar.gz -C /usr/local/ && \
    rm openjdk-21_linux-x64_bin.tar.gz

# 设置环境变量
ENV JAVA_HOME /usr/local/jdk-21
ENV PATH $JAVA_HOME/bin:$PATH

# 复制 Java 应用 JAR 包到容器（假设 JAR 包名为 app.jar）
COPY target/springInterview-1.0-SNAPSHOT.jar /app/test.jar

# 指定容器启动命令
CMD ["java", "-jar", "/app/test.jar"]