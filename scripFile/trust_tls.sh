copy "C:\Users\wq0angle\.jdks\corretto-22.0.1\lib\security\cacerts" "D:\cacerts"

"C:\Users\wq0angle\.jdks\corretto-22.0.1\bin\keytool" -import -alias "My Custom Root CA" -keystore "D:\temp\cacerts" -file  "D:\ProxyProject\proxy\tls\rootCertificate.crt"

copy "D:\cacerts" "C:\Users\wq0angle\.jdks\corretto-22.0.1\lib\security\cacerts"