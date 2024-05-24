copy "C:\Users\wq0angle\.jdks\corretto-22.0.1\lib\security\cacerts" "D:\cacerts"

"C:\Users\wq0angle\.jdks\corretto-22.0.1\bin\keytool" -import -alias "My Custom Root CA" -keystore "C:\Users\wq0angle\.jdks\corretto-22.0.1\lib\security\cacerts" -file "D:\ProxyProject\proxy\tls\rootCertificate.crt"
changeit
copy "D:\cacerts" "C:\Users\wq0angle\.jdks\corretto-22.0.1\lib\security\cacerts"

"C:\Users\wq0angle\.jdks\corretto-22.0.1\bin\keytool" -import -alias "My Custom Root CA" -file "D:\ProxyProject\proxy\tls\rootCertificate.crt"
changeit

"C:\Users\wq0angle\.jdks\corretto-22.0.1\bin\keytool" -list -alias "My Custom Root CA"
"C:\Users\wq0angle\.jdks\corretto-22.0.1\bin\keytool"  -delete -alias "My Custom Root CA"