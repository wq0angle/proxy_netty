# java使用netty代理网络请求(源码)

## 描述

#### 一个代理最重要的是稳定性、其次是快速度，但我想要说，请嚣张一点，稳和快我都要!

### 在部署该代理架构需要准备一些东西：

#### 1. 域名(无论是cdn还是https都需要域名)

#### 2. CDN解析加速(不想加速可以忽略，如果想要获得CDN线路加速的效果需要从CDN厂商配置证书、回源域名等一些配置，但目前只支持websocket方式代理)

#### 3. SSL证书(HTTPS必备)

#### 4. 一台海外服务器

## 一共有三种代理模式：

### 1. https远程代理

#### 代理架构示意：client(wifi/vpn) <--tcp/http--> proxyServer <--tcp--> targetServer 

##### 1.本质是建立SSL隧道进行数据流的交互，可以实现wifi代理到远程服务端代理的直连

##### 2.优点是简单快捷，不需要额外的代理客户端软件

##### 3.缺点是不支持部分设备，比如部分手机不支持https代理，它只能进行 ip/域名 + 端口 的代理设置，不支持设置 htts://域名 + 端口

##### 4.需要注意的是，如果服务器开放 http 的代理去支持兼容非 https 代理客户端的话，这样裸奔容易被封服务器，同时流量还容易被监控和拦截

### 2. https代理链

#### 代理架构示意：client(wifi/vpn) <--tcp/http--> proxyClient <--tcp/http--> proxyServer <--tcp--> targetServer 

##### 1.这里需要一个代理客户端，多了一层代理，这样只需要启动客户端，本地设备就能进行代理

##### 2.除本地设备以外，在同局域网内设备都可以 ip/域名 + 端口 方式 wifi代理 到代理客户端，对局域网下所有设备进行代理连接

##### 3.优点是可以解决设备不支持的问题，还能提高安全性，客户端代理以 https验证SSL证书 方式代理到服务端

##### 4.缺点是不支持CDN，部分购买的线路不太友好的服务器传输数据会很慢

### 3. websocket代理链

#### 代理架构示意：client(wifi/vpn) <--tcp/http--> proxyClient <--websocket--> cdn <--websocket--> proxyServer <--tcp--> targetServer 

##### 1.这也需要一个代理客户端，可以支持任意厂商的CDN，websocket建立帧传输通道，实现websocket帧与https加密的TCP流的互转

##### 2.在同局域网内设备都可以http或 ip + 端口 方式 wifi代理到代理客户端，这样整个局域网的设备也可以进行代理连接

##### 3.可以通过 CDN  线路传输数据，解决了传统https代理被cdn截断SSL握手并解密请求导致SSL隧道中断的问题，选择给力的CDN厂商，速度嘎嘎起飞

##### 4.优点是线路加速会使访问速度加快，同时减少数据传输的速度损耗（可以建立动态维护的多组有效活跃的连接通道，在支持CDN的基础上还会透传SSL握手，以减少 CDN 解密请求 带来的额外的 HTTPS的握手次数及 证书卸装载 带来的速度损耗，当然，这是理论上的，实际上协议升级握手问题还没在代码层面实现优化,也就是连接通道管理还没优化）

##### 5.缺点是逻辑复杂，可能存在意外的bug，并且还要考虑并发性能问题


## PS:

#### · 通过CDN用websocket方式代理速度有比较明显的提升，因为CDN线路带来的请求的速度提升比较明显，当然可能直接使用https方式也比较快，但是不是一定的，websocket和https也可能都很慢，这取决于网络接入的运营商不同的线路，可以尝试更换CDN厂商，选择自己满意的CDN厂商，速度可能会嘎嘎快，有些不足的地方，但可以先这样用吧

#### · 目前该项目还有些未完善的地方，比如：websocket握手(连接通道)没完全优化（还停留在一个请求一次握手阶段），客户端代理暂不支持ios系统

#### . 现在已经更新自动化脚本，可以在服务器上在线一键快捷部署，具体安装信息参考在线脚本提示，自动化脚本执行命令: curl -s https://raw.githubusercontent.com/wq0angle/proxy_netty/master/proxy-install.sh | bash，执行完在线脚本会下载到本地，然后还需要执行本地脚本命令进行代理配置，执行命令： source ~/.bashrc 和 proxy install
