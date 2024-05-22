package com.custom.proxy.provider;


import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
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

    public static KeyPair loadKeyPair(Path keyPath) throws Exception {
        try (PEMParser pemParser = new PEMParser(new FileReader(keyPath.toFile()))) {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            return new KeyPair(converter.getPublicKey(SubjectPublicKeyInfo.getInstance(pemParser.readObject()))
                    , converter.getPrivateKey(PrivateKeyInfo.getInstance(pemParser.readObject())));
        }
    }

    public static X509Certificate loadCertificate(Path certPath) throws Exception {
        try (FileInputStream fis = new FileInputStream(certPath.toFile())) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            return (X509Certificate) cf.generateCertificate(fis);
        }
    }

    public static SSLContext createTargetSslContext(String host) throws Exception {
        // 获取当前工作目录
        Path currentWorkingDir = Paths.get(System.getProperty("user.dir"));
        Path certPath = currentWorkingDir.resolve("tls").resolve("rootCertificate.crt");
        Path keyPath = currentWorkingDir.resolve("tls").resolve("rootPrivateKey.crt");

        KeyPair keyPair = loadKeyPair(keyPath);
        X509Certificate rootCertificate = loadCertificate(certPath);

        X500Name issuer = new X500Name("CN=My Custom Root CA, O=My Company, L=My City, ST=My State, C=My Country Code");
        X500Name subject = new X500Name("CN=" + host + ", O=YourOrg, C=CN");

        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24);
        Date notAfter = new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365);

        X509v1CertificateBuilder builder = new JcaX509v1CertificateBuilder(issuer, serial, notBefore, notAfter, subject, keyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(keyPair.getPrivate());
        X509Certificate targetCert = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(builder.build(signer));

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("rootCert", targetCert);
        keyStore.setKeyEntry("privateKey", keyPair.getPrivate(), null, new java.security.cert.Certificate[]{targetCert});

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, null);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

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