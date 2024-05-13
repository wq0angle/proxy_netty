package com.custom.proxy.test;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

public class ReqMain {
    public static void main(String[] args) throws Exception
    {
        HttpResponse<String> response = Unirest.post("https://d31z4tkdw2rsym.cloudfront.net/")
                .header("Host","wq0angle.online")
                .asString();
        System.out.println(response.getBody());
    }
}
