# java使用netty代理网络请求(源码)

## 描述

### 在部署该代理架构需要准备一些东西：

#### 域名(无论是cdn还是https都需要域名)

#### CDN解析加速(不想加速可以忽略，如果想要获得CDN线路加速的效果需要从CDN厂商配置证书、回源域名等一些配置，但目前只支持websocket)

#### SSL证书(HTTPS必备)

#### 一台海外服务器

## 一共有三种代理模式：

### 1.https远程单代理

#### 本质是建立SSL隧道进行数据流的交互，可以实现wifi代理到远程服务端代理的直连，优点是简单快捷，缺点是不支持部分设备，比如部分手机不支持https代理，如果只是http代理，裸奔容易被封服务器，同时流量还容易被监控和拦截

### 2.https代理链

#### 其实和 1. 差不多，但多了一层客户端，这样只需要启动客户端，在同局域网内设备都可以http方式wifi代理到客户端，客户端再转发原先的TCP流以https代理方式到服务端，优点是可以解决设备不支持的问题，缺点是使用比较繁琐，同时不支持CDN，部分购买的线路不太友好的服务器传输数据会很慢

### 3.websocket代理链

#### 可以支持任意厂商的CDN，websocket建立帧传输通道，实现websocket帧与https加密的TCP流的互转，可以通过CDN线路传输数据，解决了传统https代理被cdn截断SSL握手并解密请求导致SSL隧道中断的问题，选择给力的CDN厂商，速度嘎嘎起飞，优点是线路加速会使访问速度加快，同时减少数据传输的速度损耗(在支持CDN的基础上还会透传SSL握手，减少代理需求的不必要的额外的HTTPS的握手次数及证书卸装载，当然，这是理论上的，实际上协议升级握手问题还没在代码层面实现优化)，缺点是逻辑复杂，可能存在意外的bug


## PS:

#### 只是通过CDN用websocket方式代理速度已经非常快了，因为CDN线路带来的请求的速度提升还是非常可观的，可以选择自己满意的CDN厂商，速度嘎嘎快，有些不足的地方，但可以先这样用着

#### 目前该项目还有些未完善的地方，比如：不支持https代理和weboscket代理共存，websocket握手没完全优化，客户端代理暂不支持跨平台可移植化，后续会持续迭代优化
