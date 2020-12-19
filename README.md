# FriendlySSL
FriendlySSL is a Spring Boot library that handles TLS certificate generation and renewal. It is useful for a public-facing web application when the maintainer does not want to manually renew the certificate.

## Quickstart

## Usage
Simply add the `@FriendlySSL` annotation to your Spring Boot application class:

```java
@FriendlySSL
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

On startup, if the property `server.ssl.enabled` is not `true`, Spring Boot will fail to start. To handle this, FriendlySSL will check for a certificate with the key alias defined by `friendly-ssl.certificate-key-alias` in the keystore defined by `friendly-ssl.keystore-file`.
* If the keystore does not exist or if the keystore exists without the key alias, a self-signed certificate will be generated with an expiration of 1 day from now and be put into a new keystore defined by `friendly-ssl.keystore-file` (overwriting any existing keystore with that file location).
* If the keystore exists and has a certificate with the key alias, no action will be taken and the server should start normally.
* If the keystore is corrupt or inaccessible (e.g. has a password), it will be logged and no action will be taken, likely causing Spring Boot to not start.

If auto-renewal is enabled (see config below), as soon as Spring Boot is ready, the auto-renewal process will start. The certificate with the key alias defined by `friendly-ssl.certificate-key-alias` in the keystore defined by `friendly-ssl.keystore-file` is checked.
* If the certificate expiration is less than or equal to `friendly-ssl.auto-renewal-hours-before` hours from expiring, it will initiate a renewal.
* If the certificate expiration is greater than `friendly-ssl.auto-renewal-hours-before` hours from expiring, nothing will happen and the auto-renewal process will schedule itself to run again at `friendly-ssl.auto-renewal-hours-before` hours before the certificate expiration time.

## Configuration
All configuration keys are prefixed with `friendly-ssl.`. Required properties must be defined by thet consuming application.

| Key                              | Required | Value Type  | Value Description |
| -------------------------------- | -------- | ----------- | ----------------- |
| domain                           | yes      | string      |  |
| account-email                    | yes      | string      |  |
| endpoints-include                | no       | string list |  |
| auto-renew-enabled               | no       | boolean     |  |
| acme-session-url                 | no       | string      |  |
| certificate-key-alias            | no       | string      |  |
| account-private-key-file         | no       | string      |  |
| keystore-file                    | no       | string      |  |
| terms-of-service-file            | no       | string      |  |
| order-timeout-seconds            | no       | int         |  |
| token-requested-timeout-seconds  | no       | int         |  |
| auth-challenge-timeout-seconds   | no       | int         |  |
| auto-renewal-hours-before        | no       | int         |  |
| error-retry-wait-hours           | no       | int         |  |

## Implementation
FriendlySSL uses the [acme4j](https://github.com/shred/acme4j) library to communicate with the ACME server and assist in certificate renewal.
