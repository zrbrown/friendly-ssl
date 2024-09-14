# Friendly SSL

A Spring Boot library that handles SSL certificate generation and renewal using [acme4j](https://acme4j.shredzone.org).

## Quick Start

Include Friendly SSL as a dependency:

```xml
<dependency>
    <groupId>net.eightlives</groupId>
    <artifactId>friendly-ssl</artifactId>
    <version>VERSION</version>
</dependency>
```

Set application properties:

```yaml
friendly-ssl:
  domain: your-website.com
  account-email: email@gmail.com
  acme-session-url: acme://letsencrypt.org

server.ssl.bundle: youralias

spring.ssl.bundle:
  watch.file.quiet-period: 1m
  jks:
    youralias:
      reload-on-update: true
      key.alias: youralias
      keystore:
        location: keystore.p12
        password:
        type: PKCS12
```

## How it works

When the environment is prepared (but before the application context has been created), `keystore-file` is checked to see if it exists and contains a certificate named `certificate-key-alias`. If these conditions are met, application startup continues. Otherwise, a self-signed certificate that expires in 1 day is created and written to the keystore, also creating any keystore parent directories. The self-signed certificate creation may fail, most commonly because keystores with passwords are not supported.
When the application is ready, a thread is started to handle certificate auto-renewal in the background. The auto-renewal process checks for a certificate named `certificate-key-alias` in `keystore-file`. If the named X.509 certificate doesn't exist, a new certificate is ordered. Otherwise, the existing certificate's expiration is checked. If it expires in less than `auto-renewal-hours-before` hours, the certificate is renewed with the provider at `acme-session-url`. Otherwise, a renewal is scheduled to occur `auto-renewal-hours-before` before the expiration.

## Properties

Properties are defined under the `friendly-ssl.*` namespace.

| Property                        | Type         | Default                        | Description                                                                                                                                  |
|---------------------------------|--------------|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------|
| auto-renew-enabled              | boolean      | false                          | If true, certificate will indefinitely auto-renew before its expiration. Otherwise, a certificate will only be ordered on application start. |
| acme-session-url                | string       | acme://letsencrypt.org/staging | ACME URL of the Certificate Authority (CA) that will issue the certificate.                                                                  |
| certificate-key-alias           | string       | friendlyssl                    | The name of the certificate in the keystore.                                                                                                 |
| account-private-key-file        | string       | account.pem                    | The location of the key pair associated with the account.                                                                                    |
| keystore-file                   | string       | keystore.p12                   | The location of the keystore that will contain the certificate.                                                                              |
| terms-of-service-file           | string       | tos                            | The location of the Terms Of Service file.                                                                                                   |
| order-timeout-seconds           | int          | 30                             | Seconds until timeout while ordering a certificate.                                                                                          |
| token-requested-timeout-seconds | int          | 30                             | Seconds until timeout while waiting for the CA to request challenge token.                                                                   |
| auth-challenge-timeout-seconds  | int          | 20                             | Seconds until timeout while checking challenge status.                                                                                       |
| auto-renewal-hours-before       | int          | 72                             | Hours before the current certificate's expiration to trigger auto-renew.                                                                     |
| error-retry-wait-hours          | int          | 1                              | Hours to wait for retry after certificate order failure.                                                                                     |
| domain                          | string       | -                              | The domain for which to issue the certificate.                                                                                               |
| account-email                   | string       | -                              | The account email address.                                                                                                                   |
| endpoints-include               | list(string) | -                              | Endpoints to enable. Possible values are `certificate`, `tos`.                                                                               |

## Reloading SSL Certificates

Spring Boot handles reloading a renewed certificate by periodically checking for changes to the file. `spring.ssl.bundle.watch.file.quiet-period` defines how often the file is checked. 

## Certificate Renewal

### Auto-renew

Friendly SSL will auto-renew the certificate `auto-renewal-hours-before` hours before its expiration if `auto-renew-enabled` is true. This happens at application startup, so if the certificate is within the expiration window it will be renewed immediately. Since a self-signed certificate with a 1 hour expiration is created in the absence of an existing certificate, this means the application can get a signed certificate as soon as it starts up, even without an existing certificate.

### Manual renew

If you don't prefer to use auto-renew, manual renewal can be done by issuing a request to `GET /friendly-ssl/certificate/order`. `endpoints-include` must contain `certificate` to enable this.

## Account

An account with a key pair (`account-private-key-file`) and email address (`account-email`) must exist with the CA to order or renew a certificate. Friendly SSL will create an account with the given email and key pair if one does not already exist. Note that terms of service will need to be accepted (see below).

### Terms Of Service

The CA's terms of service must be accepted to create a session to order or renew a certificate. Friendly SSL will create a file at `terms-of-service-file` automatically. This file contains a JSON list of objects with a `termsOfService` property referring to the terms of service URL and `agreeToTerms` property that must be `YES` to signal acceptance. If this is a new account or the terms of service have changed, `agreeToTerms` will be `NO`.

Example terms of service file:

```json
[ {
  "termsOfService" : "http://localhost:8001",
  "agreeToTerms" : "YES"
}, {
  "termsOfService" : "https://community.letsencrypt.org/tos",
  "agreeToTerms" : "NO"
} ]
```

To accept, either manually change the file or use the endpoint. To use the endpoint, `endpoints-include` must include `tos`. Issue a request to `POST /friendly-ssl/tos/agree` with body `{"termsOfServiceLink":"tos_url"}` where `tos_url` is the `termsOfService` property from the terms of service file to agree to.

## FAQ

### Isn't this usually handled by a reverse proxy or load balancer?

Yes, but if you're just looking to run a single small app on a server (like a personal blog),
Friendly SSL takes away the headache of setting up additional infrastructure.