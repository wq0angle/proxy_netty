#!/bin/bash

# 代理各文件根目录设置
root_dir="/proxyService";

# 设置 JDK 版本和下载链接及路径
jdk_version="22"
jdk_download_url="https://download.oracle.com/java/22/archive/jdk-22_linux-x64_bin.tar.gz"
jdk_tar_file="${root_dir}/openjdk-${jdk_version}_linux-x64_bin.tar.gz"
jdk_extract_dir="${root_dir}/jdk-${jdk_version}"

# 代理服务端程序包下载链接及置放路径
proxy_service_path="${root_dir}/proxy-server-auto";
proxy_service_file="${proxy_service_path}.zip";
proxy_service_url="https://github.com/wq0angle/proxy_netty/releases/download/master-server/proxy-server-auto.zip";

# 安装脚本目录
install_script_file="${root_dir}/proxy-install.sh"
# 脚本链接
install_script_url="https://raw.githubusercontent.com/wq0angle/proxy_netty/master/proxy-install.sh"

# 当前执行脚本路径
current_script_path="$(realpath "$0")"

# 配置文件路径
CONFIG_FILE="${proxy_service_path}/proxy-server.properties"

# 初始化脚本运行
init() {

    # 打印脚本提示及注解
    annotate;

    # 初始化根目录
    init_dir;

    # 检测系统需要的组件
    check_install_unzip;
    check_install_jdk;

    # 代理服务端程序压缩包下载
    download_proxy_service

    # 代理服务端程序压缩包下载及安装
    install_proxy_service;

    # 检测脚本
    check_install_script;
    
    # 设置代理服务端程序配置
    config_Setting;
}

init_dir() {
    # 判断目录是否存在
    if [ ! -d "$root_dir" ]; then
        # 目录不存在，创建目录
        mkdir -p "$root_dir"
        echo "目录 $root_dir 已创建"
        chmod +x "$root_dir"
    fi

    # 检查自动化部署脚本是否存在
    if [ ! -f "$install_script_file" ]; then
        echo "未检测到自动化部署脚本, 正在下载";
        wget -O "$install_script_file" "$install_script_url" >/dev/null 2>&1
        if [ $? -ne 0 ]; then
            echo "自动化部署脚本 下载失败，请检查网络连接和当前用户目录权限。"
            exit 1
        else
            echo "自动化部署脚本 下载成功。"
            chmod +x "$install_script_file"
        fi
    fi
}

# 脚本检测
check_install_script() {
    # 执行来源检测模块
    if [[ "$current_script_path" = "$install_script_file" ]]; then
        echo "
本地脚本执行模式 (稳定版本) (路径: $current_script_path)
        "
    else
        echo "
线上热更新执行模式 (临时版本) | 临时路径: $current_script_path | 存放路径: $install_script_file
注意:
    1.临时脚本将在执行完成后自动清除, 需要使用 source ~/.bashrc 和 proxy install 命令重新安装维护
    2.建议使用 'proxy update' 命令进行持久化更新脚本
"
        exit 1
    fi
}

# 代理服务端程序安装
install_proxy_service() {
    # 解压安装包
    if [ ! -d "$proxy_service_path" ]; then
        echo "未检测到解压的服务端程序包，安装失败";
        exit 1
    fi

    # 检查脚本环境变量是否已设置
    if grep -q "export proxy_start=${proxy_service_path}/proxy_start.sh" ~/.bashrc && grep -q "export proxy_stop=${proxy_service_path}/proxy_stop.sh" ~/.bashrc; then
        echo "脚本环境变量已设置。"
    else
        echo "脚本设置环境变量..."
        echo "export proxy_install=${install_script_file}" >> ~/.bashrc
        echo "export proxy_start=${proxy_service_path}/proxy_start.sh" >> ~/.bashrc
        echo "export proxy_stop=${proxy_service_path}/proxy_stop.sh" >> ~/.bashrc
        echo "export proxy_status=${proxy_service_path}/nohup.out" >> ~/.bashrc
        # echo "alias proxy-install=\"\$proxy_install\"" >> ~/.bashrc
        # echo "alias proxy-start=\"\$proxy_start\"" >> ~/.bashrc
        # echo "alias proxy-stop=\"\$proxy_stop\"" >> ~/.bashrc
        # echo "alias proxy-status=\"\$proxy_status\"" >> ~/.bashrc
        commandSetting;
        echo "脚本环境变量设置成功。"
        exit 1
    fi

     # 添加文件权限并立即加载环境变量
    chmod -R +x "$root_dir"
    source ~/.bashrc
     
    echo "服务端代理程序 安装已完成"
}

commandSetting() {
      echo "
# 定义 proxy 命令函数
proxy() {
    case \"\$1\" in
        install)
            \"\$proxy_install\"
            ;;
        update)
            echo '正在从Github更新... | 临时路径: $current_script_path | 存放路径: $install_script_file'
            wget -o -s $install_script_file $install_script_url
            ;;
        start)
            \"\$proxy_start\"
            ;;
        stop)
            \"\$proxy_stop\"
            ;;
        status)
            tail -n 50 \"\$proxy_status\" | less +F
            ;;
        *)
            echo \"请使用正确命令: proxy { install | update | start | stop | status }\"
            ;;
    esac
}" >> ~/.bashrc
}

# 代理服务端程序压缩包下载
download_proxy_service() {
    # 检查文件是否已下载
    if [ ! -f "$proxy_service_file" ]; then
        echo "代理服务端程序 压缩包 未下载，开始下载... | $proxy_service_url"
        wget -O "$proxy_service_file" "$proxy_service_url"
        if [ $? -ne 0 ]; then
            echo "代理服务端程序 压缩包 失败，请检查网络连接和当前用户目录权限。"
            exit 1
        fi
        echo "代理服务端程序 压缩包 下载完成。"
    else
        echo "代理服务端程序 压缩包 已下载。"
    fi

    # 解压安装包
    echo "解压 代理服务端程序 压缩包..."
    unzip -o $proxy_service_file -d $proxy_service_path
    if [ $? -ne 0 ]; then
        echo "代理服务端程序 压缩包 解压失败。 删除问题安装包，请重新下载"
        rm -rf $proxy_service_file;
        exit 1
    fi
    echo "代理服务端程序 压缩包 解压完成"
}

# JDK检测并安装
check_install_jdk() {

    # 检测是否已安装 JDK
    if command -v java &> /dev/null; then
        installed_version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
        if [[ "$installed_version" == "$jdk_version"* ]]; then
            echo "JDK $jdk_version 已安装，跳过安装。"
        else
            echo "检测到已安装其他版本的 JDK: $installed_version，准备安装 JDK $jdk_version。"
        fi
    else
        echo "未检测到 JDK 安装，准备安装 JDK $jdk_version。"
    fi

    # 检查文件是否已下载
    if [ ! -f "$jdk_tar_file" ]; then
        echo "JDK 安装包未下载，开始下载... | $jdk_tar_file"
        wget -O "$jdk_tar_file" "$jdk_download_url"
        if [ $? -ne 0 ]; then
            echo "下载 JDK 安装包失败，请检查网络连接和当前用户目录权限。"
            exit 1
        fi
    else
        echo "JDK 安装包已下载。"
    fi

    # 解压安装包
    if [ ! -d "$jdk_extract_dir" ]; then
        echo "解压 JDK 安装包... | $jdk_extract_dir"
        tar -xvf "$jdk_tar_file" -C $root_dir
        if [ $? -ne 0 ]; then
            echo "解压 JDK 安装包失败。"
            rm -rf $jdk_tar_file;
            exit 1
        fi
    else
        echo "JDK 安装包已解压。"
    fi

    # 检查环境变量是否已设置
    if grep -q "export JAVA_HOME=$jdk_extract_dir" ~/.bashrc && grep -q "export PATH=\$PATH:\$JAVA_HOME/bin" ~/.bashrc; then
        echo "JDK环境变量已设置。"
    else
        echo "JDK设置环境变量..."
        echo "export JAVA_HOME=$jdk_extract_dir" >> ~/.bashrc
        echo "export PATH=\$PATH:\$JAVA_HOME/bin" >> ~/.bashrc
        echo "export CLASSPATH=.:\$JAVA_HOME/lib/dt.jar:\$JAVA_HOME/lib/tools.jar" >> ~/.bashrc
    fi

    # 立即加载环境变量
    source ~/.bashrc

    # 输出 JDK 版本
    echo "JDK安装完成,查验JDK版本:"
    java -version
}

# zip解压组件检测并安装
check_install_unzip() {
    if ! command -v unzip &> /dev/null 
    then
        echo "unzip 未安装，正在安装..."

        # 根据不同的Linux发行版安装unzip
        if [ -f /etc/debian_version ]; then
            # Debian/Ubuntu
            sudo apt-get update
            sudo apt-get install -y unzip
        elif [ -f /etc/redhat-release ]; then
            # CentOS/RHEL
            sudo yum install -y unzip
        elif [ -f /etc/fedora-release ]; then
            # Fedora
            sudo dnf install -y unzip
        elif [ -f /etc/arch-release ]; then
            # Arch Linux
            sudo pacman -S --noconfirm unzip
        else
            echo "无法识别的Linux发行版，无法自动安装unzip"
            exit 1
        fi

        echo "unzip 安装完成"
        else
            echo "unzip 已安装"
    fi
}

# 服务端配置文件设置
config_Setting() {
    if [ -f "$CONFIG_FILE" ]; then
        echo "检测到已存在 代理服务端程序配置文件，是否要重新设置? y 或 Y : 重新设置, 任意键: 跳过"
        read RESET_CONFIG;
        if [ ! "$RESET_CONFIG" = "y" ] && [ ! "$RESET_CONFIG" = "Y" ] ; then
            echo "跳过配置文件设置。"
            return;
        fi
    fi

     echo "服务端配置文件配置."
    
    # 提示用户输入服务端启动端口号
    echo "请输入服务端启动端口号："
    read PORT_VALUE
    if [ -z "$PORT_VALUE" ]; then
        echo "端口号不能为空，请重新运行脚本并输入有效的端口号。"
        exit 1
    fi

    # 提示用户输入静态伪装网站本地端口
    echo "请输入静态伪装网站本地端口："
    read STATIC_WEBSITE_PORT
    if [ -z "$STATIC_WEBSITE_PORT" ]; then
        echo "伪装网站本地端口不能为空，请重新运行脚本并输入有效的伪装网站本地端口。"
        exit 1
    fi

    # 提示用户是否启用SSL证书
    echo "是否启用SSL证书？(y 或 Y : 启用, 任意键: 不启用)"
    read SSL_ENABLED
    if [ "$SSL_ENABLED" = "y" ] || [ "$SSL_ENABLED" = "Y" ] ; then
           # 提示用户输入jsk类型SSL证书文件目录
        echo "请输入jsk类型SSL证书文件目录："
        read SSL_JSK_PATH
        if [ -z "$SSL_JSK_PATH" ]; then
            echo "证书目录不能为空，请重新运行脚本并输入有效的证书目录。"
            exit 1
        fi

        # 提示用户输入jsk类型SSL证书文件名和密码
        echo "提示:需要注意的是配置格式 证书文件名:密码  支持sin，若多域名指向服务器的需求，证书可以配置以英文的','隔开，例如: www.test1.com.jks:123,www.test1.com.jks:123"
        echo "请输入jsk类型SSL证书文件名和密码："
        read SSL_JSK_FILE_PASSWORD
        if [ -z "$SSL_JSK_FILE_PASSWORD" ]; then
            echo "SSL证书文件名和密码不能为空，请重新运行脚本并输入SSL证书文件名和密码。"
            exit 1
        fi

        # 提示用户输入sin回落默认证书名
        echo "请输入sin回落默认证书文件名："
        read SIN_DEFAULT_FILE
        if [ -z "$SIN_DEFAULT_FILE" ]; then
            echo "sin回落默认证书文件名不能为空，请重新运行脚本并输入sin回落默认证书文件名。"
            exit 1
        fi
    else 
        # 设置默认值 
        SSL_ENABLED="false"
        SSL_JSK_PATH="none" 
        SIN_DEFAULT_FILE="none" 
        SSL_JSK_FILE_PASSWORD="none" 
    fi

    # 提示用户输入静态伪装网站目录
    # echo "请输入静态伪装网站目录："
    # read STATIC_WEBSITE_DIRECTORY
    # if [ -z "$STATIC_WEBSITE_DIRECTORY" ]; then
    #     echo "目录不能为空，请重新运行脚本并输入有效的目录。"
    #     exit 1
    # fi

    
    # 确认输入信息
    echo "确认配置信息："
    echo "服务端启动端口号: $PORT_VALUE"
    echo "是否启用SSL证书: $SSL_ENABLED"
    echo "静态伪装网站目录: $proxy_service_path/blog"
    echo "静态伪装网站本地端口: $STATIC_WEBSITE_PORT"
    echo "jsk类型SSL证书文件目录: $SSL_JSK_PATH"
    echo "jsk类型SSL证书文件名和密码: $SSL_JSK_FILE_PASSWORD"
    echo "sin回落默认证书名: $SIN_DEFAULT_FILE"
    echo "确认无误后按回车继续，否则按 Ctrl+C 取消。"
    read -s -n 1

    # 覆盖配置文件内容
    echo "#服务端配置" > "$CONFIG_FILE" 
    echo >> "$CONFIG_FILE" 
    echo "#服务端启动端口号" >> "$CONFIG_FILE"
    echo "server.port = $PORT_VALUE" >> "$CONFIG_FILE"
    echo "#代理监听启动是否启用SSL证书" >> "$CONFIG_FILE"
    echo "ssl.listener.enabled = $SSL_ENABLED" >> "$CONFIG_FILE"
    echo "#静态伪装网站目录" >> "$CONFIG_FILE"
    echo "website.directory = $proxy_service_path/blog" >> "$CONFIG_FILE"
    echo "#静态伪装网站端口" >> "$CONFIG_FILE"
    echo "website.port = $STATIC_WEBSITE_PORT" >> "$CONFIG_FILE"
    echo "#jsk类型SSL证书目录文件" >> "$CONFIG_FILE"
    echo "ssl.jks.path = $SSL_JSK_PATH" >> "$CONFIG_FILE"
    echo "#jsk类型SSL证书文件名和密码" >> "$CONFIG_FILE"
    echo "ssl.jks.file.password = $SSL_JSK_FILE_PASSWORD" >> "$CONFIG_FILE"
    echo "#sin回落默认证书" >> "$CONFIG_FILE"
    echo "sin.default.file = $SIN_DEFAULT_FILE" >> "$CONFIG_FILE"

    echo "配置文件已更新。"
}

annotate() {
    echo "
提示:
    在使用此自动化安装脚本部署代理服务器时，若使用SSL证书加密，则需要提前设置好 DNS 的域名映射，并且需要自行购买SSL证书，或者自己解决，SSL上传或部署到服务器上，按照脚本按照提示设置到SSL证书文件的目录即可。
    目前支持的SSL证书仅支持 *.JKS 加密证书，若使用 CDN + websocket 方式，那么就必须要使用SSL证书来代理了，CDN代理商的SSL证书也需要自行部署解决。

命令:
    1. 运行安装脚本：proxy install
    2. 运行更新脚本：proxy update
    3. 启动代理服务：proxy start
    4. 停止代理服务：proxy stop
    5. 查看代理服务状态：proxy status

"
}

# 执行初始化
init


