#!/usr/bin/env bash
# 重新生成「测试签名」keystore。仅用于 CI 冒烟，绝不用于线上发布。
#
# 为什么不是 keytool：本脚手架生成环境里没有 JDK（且不允许安装系统级组件）。
# Java 9+ 的 KeyStore.getDefaultType() 已是 PKCS12，AGP/KeyStore 都能直接读 PKCS12，
# 因此 openssl 导出的 PKCS12 与 `keytool -genkeypair -storetype pkcs12` 等价。
# 产物是否真能被 JDK 读取，由 workflow 里的 `keytool -list -v` 步骤实测证明。
set -euo pipefail
cd "$(dirname "$0")"
ALIAS=ci-test
PASS=ci-test-only-password
DAYS=3650
SUBJ="/C=CN/O=dsha-021-test/OU=CI-TEST-ONLY/CN=DSHA CI Test Signing (NOT FOR RELEASE)"

umask 077
openssl req -x509 -newkey rsa:2048 -sha256 -days "$DAYS" -nodes \
  -keyout .tmp-key.pem -out test-signing.cert.pem -subj "$SUBJ"
trap 'rm -f .tmp-key.pem' EXIT
openssl pkcs12 -export -inkey .tmp-key.pem -in test-signing.cert.pem \
  -name "$ALIAS" -out test-signing.keystore -passout "pass:$PASS"
openssl x509 -in test-signing.cert.pem -noout -fingerprint -sha256
echo "keystore: ci/test-signing.keystore  alias=$ALIAS  password=$PASS"
