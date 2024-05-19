package com.custom.proxy.provider;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;

public class LocalSslContextProvider {
    public static KeyStore generateSelfSignedCertificate() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, SignatureException, InvalidKeyException, NoSuchProviderException, IOException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X509Certificate cert = generateCertificate(keyPair);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("alias", keyPair.getPrivate(), "password".toCharArray(), new X509Certificate []{cert});

        return keyStore;
    }

    public static X509Certificate generateCertificate(KeyPair keyPair) throws CertificateException, InvalidKeyException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException {
        X509V3CertificateGenerator certGenerator = new X509V3CertificateGenerator();
        certGenerator.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        certGenerator.setSubjectDN(new X509Principal("CN=localhost"));
        certGenerator.setIssuerDN(new X509Principal("CN=localhost"));
        certGenerator.setPublicKey(keyPair.getPublic());
        certGenerator.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24));
        certGenerator.setNotAfter(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365));
        certGenerator.setSignatureAlgorithm("SHA256WithRSAEncryption");

        return certGenerator.generate(keyPair.getPrivate(), "BC");
    }

    public static SslContext getSslContext() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyStore keyStore = generateSelfSignedCertificate();
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, "password".toCharArray());
        return SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .protocols("TLSv1.1", "TLSv1.2", "TLSv1.3")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();
    }

    public static SslContext createSslContext() throws Exception {
        String keyStoreFilePath = "D:\\文件\\代理客户端\\bin\\Debug\\net8.0\\rootCert.pfx";
        String keyStorePassword = "";

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream inputStream = new FileInputStream(keyStoreFilePath)) {
            keyStore.load(inputStream, keyStorePassword.toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

        return SslContextBuilder
                .forServer(keyManagerFactory)
                .protocols("TLSv1.1","TLSv1.2","TLSv1.3")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();
    }
}
