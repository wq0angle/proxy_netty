package com.custom.proxy.test;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

public class ReqMain {
    public static void main(String[] args) throws Exception
    {
        HttpResponse<String> response = Unirest.post("https://www.wq0angle.online/")
                .header("Host","www.wq0angle.online")
                .asString();

        System.out.println(response.getBody());
        System.out.println(response.getHeaders());

    }
}
