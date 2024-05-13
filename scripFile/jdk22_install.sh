#!/bin/bash

# 设置 JDK 版本和下载链接
jdk_version="22"
jdk_download_url="https://download.oracle.com/java/22/archive/jdk-22_linux-x64_bin.tar.gz"

# 下载 JDK 安装包
wget -O openjdk-${jdk_version}_linux-x64_bin.tar.gz $jdk_download_url

# 解压安装包
tar -xvf openjdk-${jdk_version}_linux-x64_bin.tar.gz

# 设置环境变量
echo "export JAVA_HOME=$(pwd)/jdk-${jdk_version}" >> ~/.bashrc
echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.bashrc
echo "export CLASSPATH=.:$JAVA_HOME/lib/dt.jar:$JAVA_HOME/lib/tools.jar" >> ~/.bashrc

# 立即加载环境变量
source ~/.bashrc

# 输出 JDK 版本
java -version