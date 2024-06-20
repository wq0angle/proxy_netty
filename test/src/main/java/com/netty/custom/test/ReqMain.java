package com.netty.custom.test;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

public class ReqMain {
    public static void main(String[] args) throws Exception
    {
        HttpResponse<String> response = Unirest.post("https://www.wq0angle.online/")
                .header("Host","www.wq0angle.online")
//                .header("X-Target-Url","fanyi.baidu.com:433")
//                .header("X-Target-Method", HttpMethod.CONNECT.name())
                .asString();

        System.out.println(response.getBody());
        System.out.println(response.getHeaders());

    }
}
