package com.custom.proxy.provider;


import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v1CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v1CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

    private static CertificateProvider instance;
    private X509Certificate x509Certificate;
    private PrivateKey privateKey;

    private CertificateProvider(){

    }

    public void buildSslFile() {
        try {
            // 获取当前工作目录
            Path currentWorkingDir = Paths.get(System.getProperty("user.dir"));

            // 创建 tls 目录的路径
            Path tlsDir = currentWorkingDir.resolve("tls");

            // 确保 tls 目录存在
            if (!Files.exists(tlsDir)) {
                Files.createDirectories(tlsDir);
            }

            // 创建证书文件的路径
            Path certPath = tlsDir.resolve("rootCertificate.crt");

            // 写入或覆盖证书文件
            Files.write(certPath, x509Certificate.getEncoded());

            log.info("Root certificate written to {}", certPath);
        } catch (Exception e) {
            log.error("Error writing root certificate: {}", e.getMessage(), e);
        }
    }

    public void loadSslFile(Path certPath) throws Exception {
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());

        // 初始化KeyStore
        ks.load(null, null); // 加载一个空的KeyStore，无需密码

        // 从文件加载证书
        try (FileInputStream fis = new FileInputStream(certPath.toFile())) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            while (fis.available() > 0) {
                Certificate cert = cf.generateCertificate(fis);
                ks.setCertificateEntry("rootCert", cert); // 添加证书到KeyStore
            }
        }

        tmf.init(ks);

        SSLContext sc = SSLContext.getInstance("TLS");
        sc.init(null, tmf.getTrustManagers(), new SecureRandom());

        HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
    }

    public SslContext createTargetSslContext(String host) throws Exception {
        KeyPair keyPair = buildKeyPair();

        X500Name issuer = new X500Name("CN=My Custom Root CA, O=My Company, L=My City, ST=My State, C=My Country Code"); // 根证书的信息
        X500Name subject = new X500Name("CN=" + host + ", O=YourOrg, C=CN"); // 动态生成的证书的信息

        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24);
        Date notAfter = new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365);

        X509v1CertificateBuilder builder = new JcaX509v1CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, keyPair.getPublic());

        ContentSigner signer = new JcaContentSignerBuilder("SHA256WithRSAEncryption").build(privateKey); // 使用根证书的私钥签名
        X509Certificate targetCert = new JcaX509CertificateConverter().setProvider(new BouncyCastleProvider()).getCertificate(builder.build(signer));

        // 将证书和私钥转换为 Netty 所需的格式
        return SslContextBuilder
                .forServer(privateKey, targetCert)
                .build();
    }

    public static CertificateProvider getInstance() throws Exception {
        if(instance == null){
            instance = new CertificateProvider();
            KeyPair keyPair = buildKeyPair();
            instance.setPrivateKey(keyPair.getPrivate());
            instance.setX509Certificate(instance.buildCertificate(buildKeyPair()));
        }
        return instance;
    }

    public SslContext createSslContext() throws Exception {
        // 将证书和私钥转换为 Netty 所需的格式
        return SslContextBuilder
                .forServer(privateKey, x509Certificate)
                .build();
    }

    private static KeyPair buildKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private X509Certificate buildCertificate(KeyPair keyPair) throws Exception {
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

        ContentSigner contentSigner = new JcaContentSignerBuilder(signatureAlgorithm).build(keyPair.getPrivate());

        X509v1CertificateBuilder certBuilder = new JcaX509v1CertificateBuilder(dnName, certSerialNumber, startDate
                , endDate, dnName, keyPair.getPublic());

        return new JcaX509CertificateConverter()
                .setProvider(new BouncyCastleProvider())
                .getCertificate(certBuilder.build(contentSigner));
    }
}