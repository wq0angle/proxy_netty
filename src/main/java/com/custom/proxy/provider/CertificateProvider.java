package com.custom.proxy.provider;


import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.Date;

@Data
@Slf4j
public class CertificateProvider {

    private CertificateProvider(){

    }

    private static PrivateKey loadPrivateKey(Path keyPath) throws Exception {
        try (FileReader fileReader = new FileReader(keyPath.toFile());
             PEMParser pemParser = new PEMParser(fileReader)) {

            Object object = pemParser.readObject();
            if (object instanceof PEMKeyPair pemKeyPair) {
                PrivateKeyInfo privateKeyInfo = pemKeyPair.getPrivateKeyInfo();
                return new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
            } else if (object instanceof PrivateKeyInfo privateKeyInfo) {
                return new JcaPEMKeyConverter().getPrivateKey(privateKeyInfo);
            } else {
                throw new IllegalArgumentException("Invalid key format");
            }
        }
    }

    private static X509Certificate loadCertificate(Path certPath) throws Exception {
        try (FileReader fileReader = new FileReader(certPath.toFile());
             PEMParser pemParser = new PEMParser(fileReader)) {

            X509CertificateHolder certificateHolder = (X509CertificateHolder) pemParser.readObject();
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certificateFactory.generateCertificate(
                    new ByteArrayInputStream(certificateHolder.getEncoded()));
        }
    }


    public static SSLContext createTargetSslContext(String host) throws Exception {
        // 获取当前工作目录
        Path currentWorkingDir = Paths.get(System.getProperty("user.dir"));
        Path certPath = currentWorkingDir.resolve("tls").resolve("rootCertificate.crt");
        Path keyPath = currentWorkingDir.resolve("tls").resolve("rootPrivateKey.key");

        X509Certificate certificate = loadCertificate(certPath);
        PrivateKey privateKey = loadPrivateKey(keyPath);

        // 创建 KeyStore 并加载证书和私钥
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("cert", certificate);
        keyStore.setKeyEntry("key", privateKey, new char[0], new java.security.cert.Certificate[]{certificate});

        // 初始化 KeyManagerFactory 和 TrustManagerFactory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        // 创建并初始化 SSLContext
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return sslContext;
    }

    public static void buildSslFile() {
        try {
            // 获取当前工作目录
            Path currentWorkingDir = Paths.get(System.getProperty("user.dir"));

            // 创建 tls 目录的路径
            Path tlsDir = currentWorkingDir.resolve("tls");

            // 确保 tls 目录存在
            if (!Files.exists(tlsDir)) {
                Files.createDirectories(tlsDir);
            }

            // 创建证书文件和私钥文件的路径
            Path certPath = tlsDir.resolve("rootCertificate.crt");
            Path keyPath = tlsDir.resolve("rootPrivateKey.key");


            // 判断证书文件和私钥文件是否已经存在
            if (!Files.exists(certPath) || !Files.exists(keyPath)) {
                KeyPair rootKeyPair = buildKeyPair();
                X509Certificate rootCertificate = buildRootCertificate(rootKeyPair);

                // 使用JcaPEMWriter将证书写入文件
                try (JcaPEMWriter certWriter = new JcaPEMWriter(new FileWriter(certPath.toFile()));
                     JcaPEMWriter keyWriter = new JcaPEMWriter(new FileWriter(keyPath.toFile()))) {
                    certWriter.writeObject(rootCertificate);
                    keyWriter.writeObject(rootKeyPair.getPrivate());
                }

                log.info("Root certificate and private key written to {}", tlsDir);
            } else {
                log.info("Root certificate or private key already exists at {}", tlsDir);
            }
        } catch (Exception e) {
            log.error("Error writing root certificate or private key: {}", e.getMessage(), e);
        }
    }

    private static KeyPair buildKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }


    private static X509Certificate buildRootCertificate(KeyPair rootKeyPair) throws Exception {
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);

        // 根证书的信息
        X500Name dnName = new X500Name("CN=My Custom Root CA, O=My Company, L=My City, ST=My State, C=My Country Code");
        BigInteger certSerialNumber = new BigInteger(Long.toString(now)); // Using the current timestamp as the certificate serial number

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.YEAR, 1); // Add one year to current date

        Date endDate = calendar.getTime();

        String signatureAlgorithm = "SHA256WithRSA"; // Signature algorithm

        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(rootKeyPair.getPrivate());

        X509v1CertificateBuilder certBuilder = new JcaX509v1CertificateBuilder(dnName, certSerialNumber, startDate
                , endDate, dnName, rootKeyPair.getPublic());

        return new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(certBuilder.build(contentSigner));
    }
}