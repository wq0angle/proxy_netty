package com.custom.proxy.entity;

import lombok.Builder;
import lombok.Data;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

@Data
@Builder
public class X509CertificateDTO {

    private X509Certificate certificate;

    private PrivateKey privateKey;
}
