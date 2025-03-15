# ʹ�ùٷ� Ubuntu �������������Ҽ����Ժã�
FROM ubuntu:22.04

# ��װ��Ҫ���ߣ�wget��������JDK��vim��ѡ��
RUN apt-get update && apt-get install -y wget vim

# ���ز���װ JDK 21
RUN wget https://download.java.net/java/GA/jdk21/fd2272bbf8e04c3dbaee13770090416c/35/GPL/openjdk-21_linux-x64_bin.tar.gz && \
    tar -xzf openjdk-21_linux-x64_bin.tar.gz -C /usr/local/ && \
    rm openjdk-21_linux-x64_bin.tar.gz

# ���û�������
ENV JAVA_HOME /usr/local/jdk-21
ENV PATH $JAVA_HOME/bin:$PATH

# ���� Java Ӧ�� JAR �������������� JAR ����Ϊ app.jar��
COPY target/springInterview-1.0-SNAPSHOT.jar /app/test.jar

# ָ��������������
CMD ["java", "-jar", "/app/test.jar"]