package com.custom.proxy.provider;


import com.custom.proxy.entity.X509CertificateDTO;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.crypto.Cipher;
import javax.net.ssl.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.*;
import java.util.*;

@Data
@Slf4j
public class CertificateProvider {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private static PrivateKey loadPrivateKey(Path keyPath) throws Exception {
        try (PEMParser pemParser = new PEMParser(new FileReader(keyPath.toFile()))) {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
            return converter.getKeyPair((org.bouncycastle.openssl.PEMKeyPair) pemParser.readObject()).getPrivate();
        }
    }

    private static X509Certificate loadCertificate(Path certPath) throws Exception {
        try (PEMParser pemParser = new PEMParser(new FileReader(certPath.toFile()))) {
            X509CertificateHolder certificateHolder = (X509CertificateHolder) pemParser.readObject();
            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateHolder);
        }
    }

    //创建目标SSL证书上下文
    public static SslContext createTargetSslContext(String host) throws Exception {
        // 获取当前工作目录
        Path currentWorkingDir = Paths.get(System.getProperty("user.dir"));
        Path caCertPath = currentWorkingDir.resolve("tls").resolve("rootCertificate.crt");
        Path caKeyPath = currentWorkingDir.resolve("tls").resolve("rootPrivateKey.key");

        X509Certificate caCert = loadCertificate(caCertPath);
        PrivateKey caPrivateKey = loadPrivateKey(caKeyPath);

        // 生成服务器证书
        X509CertificateDTO certDTO = buildTargetCertificate(host, caPrivateKey, caCert);
        X509Certificate cert = certDTO.getCertificate();
        PrivateKey key = certDTO.getPrivateKey();

        // 创建 KeyStore 并加载证书和私钥
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("cert", cert);
        keyStore.setKeyEntry("key", key, new char[0], new java.security.cert.Certificate[]{cert, caCert});

        // 初始化 KeyManagerFactory 和 TrustManagerFactory
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        // 使用 SslContextBuilder 创建并初始化 SSLContext，指定多个协议
        return SslContextBuilder
                .forServer(kmf)
                .trustManager(tmf)  // 设置 TrustManagerFactory
                .protocols("TLSv1.1", "TLSv1.2", "TLSv1.3")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();
    }

    public static void buildRootSslFile() {
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

    //生成目标证书
    public static X509CertificateDTO buildTargetCertificate(String host, PrivateKey caPrivateKey, X509Certificate caCert) throws Exception {
        X509CertificateHolder caCertHolder = new X509CertificateHolder(caCert.getEncoded());
        X500Name issuer = caCertHolder.getIssuer();  // 从根证书的Subject获取Issuer
        X500Name subject = new X500Name("CN=" + host);
        BigInteger serial = new BigInteger(String.valueOf(System.currentTimeMillis()));
        Date startDate = new Date(System.currentTimeMillis());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.YEAR, 1);
        Date endDate = calendar.getTime();

        KeyPair keyPair = buildKeyPair();

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, startDate, endDate, subject, keyPair.getPublic()
        );

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSA").setProvider("BC").build(caPrivateKey);
        X509CertificateHolder certHolder = certBuilder.build(signer);

        X509Certificate cert = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder);

        X509CertificateDTO targetCertDTO = X509CertificateDTO
                .builder()
                .certificate(cert)
                .privateKey(keyPair.getPrivate())
                .build();

        // 将目标证书和根证书保存到一个文件中 | 将根证书追加到目标证书形成证书链
        Path currentWorkingDir = Paths.get(System.getProperty("user.dir"));
        Path certPath = currentWorkingDir.resolve("tls").resolve("targetCertificateWithChain.crt");
        Path keyPath = currentWorkingDir.resolve("tls").resolve("targetKey.key");

        try (JcaPEMWriter certWriter = new JcaPEMWriter(new FileWriter(certPath.toFile()));
             JcaPEMWriter keyWriter = new JcaPEMWriter(new FileWriter(keyPath.toFile()))) {
            certWriter.writeObject(cert);
            certWriter.writeObject(caCert);
            keyWriter.writeObject(targetCertDTO.getPrivateKey());
        }
        targetCertDTO.setCertificate(loadCertificate(certPath));
        targetCertDTO.setPrivateKey(loadPrivateKey(keyPath));
        return targetCertDTO;
    }

    public static X509Certificate buildRootCertificate(KeyPair rootKeyPair) throws Exception {
        X500Name dnName = new X500Name("CN=My Custom Root CA, O=My Company, L=My City, ST=My State, C=My Country Code");
        BigInteger certSerialNumber = new BigInteger(Long.toString(System.currentTimeMillis()));
        Date startDate = new Date(System.currentTimeMillis());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(startDate);
        calendar.add(Calendar.YEAR, 1);
        Date endDate = calendar.getTime();

        ContentSigner contentSigner = new JcaContentSignerBuilder("SHA256WithRSA").setProvider("BC").build(rootKeyPair.getPrivate());

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName, certSerialNumber, startDate, endDate, dnName, rootKeyPair.getPublic());

        // 添加基本约束扩展，标记为CA证书
        certBuilder.addExtension(org.bouncycastle.asn1.x509.Extension.basicConstraints, true, new org.bouncycastle.asn1.x509.BasicConstraints(true));

        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(contentSigner));
    }

    public static void trustAllRootCerts() throws Exception {
        // 获取当前工作目录
        Path currentWorkingDir = Paths.get(System.getProperty("user.dir"));
        Path certPath = currentWorkingDir.resolve("tls").resolve("rootCertificate.crt");

        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        FileInputStream fis = new FileInputStream(String.valueOf(certPath));
        X509Certificate caCert = (X509Certificate) cf.generateCertificate(fis);

        // Create a KeyStore containing our trusted CAs
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(null, null);
        ks.setCertificateEntry("My Custom Root CA", caCert);

        // Create a TrustManager that trusts the CAs in our KeyStore
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);

        // Create an SSLContext that uses our TrustManager
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, tmf.getTrustManagers(), new java.security.SecureRandom());

        // Set the default SSLContext
        SSLContext.setDefault(sslContext);
    }

    public static void main(String[] args) throws Exception {
        buildRootSslFile();

        Path currentWorkingDir = Paths.get(System.getProperty("user.dir"));
        Path caCertPath = currentWorkingDir.resolve("tls").resolve("rootCertificate.crt");
        Path caKeyPath = currentWorkingDir.resolve("tls").resolve("rootPrivateKey.key");

        X509Certificate caCert = loadCertificate(caCertPath);
        PrivateKey caPrivateKey = loadPrivateKey(caKeyPath);

        trustAllRootCerts();

        X509CertificateDTO targetCertificateDTO = buildTargetCertificate("www.baidu.com", caPrivateKey, caCert);
        X509Certificate targetCertificate = targetCertificateDTO.getCertificate();
        PrivateKey targetKey = targetCertificateDTO.getPrivateKey();

        // 验证目标证书的签名是否由根证书签名
        targetCertificate.verify(caCert.getPublicKey());

        // 验证私钥是否与目标证书的公钥匹配
        PublicKey publicKey = targetCertificate.getPublicKey();

        byte[] testMessage = "Test message".getBytes();

        // 加密
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedMessage = cipher.doFinal(testMessage);

        // 解密
        cipher.init(Cipher.DECRYPT_MODE, targetKey);
        byte[] decryptedMessage = cipher.doFinal(encryptedMessage);

        if (new String(decryptedMessage).equals(new String(testMessage))) {
            System.out.println("私钥与目标证书的公钥匹配");
        } else {
            System.out.println("私钥与目标证书的公钥不匹配");
        }

        // 验证整个证书链
        verifyCertificateChain(targetCertificate, caCert);
        System.out.println("证书验证成功");
    }

    private static void verifyCertificateChain(X509Certificate targetCertificate, X509Certificate rootCertificate) throws Exception {
        List<X509Certificate> certChain = new ArrayList<>();
        certChain.add(targetCertificate);
        certChain.add(rootCertificate);

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        CertPath certPath = certFactory.generateCertPath(certChain);

        TrustAnchor anchor = new TrustAnchor(rootCertificate, null);
        PKIXParameters params = new PKIXParameters(Collections.singleton(anchor));
        params.setRevocationEnabled(false);

        CertPathValidator validator = CertPathValidator.getInstance("PKIX");
        PKIXCertPathValidatorResult result = (PKIXCertPathValidatorResult) validator.validate(certPath, params);

        System.out.println("证书链验证成功: " + result);
    }
}