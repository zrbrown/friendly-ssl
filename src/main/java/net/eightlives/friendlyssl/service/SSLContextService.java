package net.eightlives.friendlyssl.service;

import lombok.extern.slf4j.Slf4j;
import net.eightlives.friendlyssl.exception.SSLCertificateException;
import org.springframework.stereotype.Component;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import java.util.Set;

@Slf4j
@Component
public class SSLContextService {

    // Should cover desired 'Tomcat:type=ThreadPool,name=https-jsse-nio-<<port>>'
    private static final String JMX_THREAD_POOL_NAME = "*:type=ThreadPool,name=*";
    private static final String JMX_OPERATION_RELOAD_SSL_HOST_CONFIGS_NAME = "reloadSslHostConfigs";

    private final MBeanServer mBeanServer;

    public SSLContextService(MBeanServer mBeanServer) {
        this.mBeanServer = mBeanServer;
    }

    /**
     * Reload this server's SSL context.
     */
    public void reloadSSLConfig() {
        try {
            ObjectName jmxThreadPoolName = new ObjectName(JMX_THREAD_POOL_NAME);
            Set<ObjectInstance> beans = mBeanServer.queryMBeans(jmxThreadPoolName, null);

            if (beans.isEmpty()) {
                throw new SSLCertificateException("Cannot locate any JMX thread pool. SSL context will not be reloaded" +
                        " and certificate will not be used unless server is restarted.");
            }

            log.info("Reloading SSL context for " + beans.size() + " MBeans");
            beans.forEach(bean -> reloadSSLConfigOnThreadPoolJMX(mBeanServer, bean.getObjectName()));
        } catch (MalformedObjectNameException e) {
            throw new SSLCertificateException(e);
        }
    }

    private void reloadSSLConfigOnThreadPoolJMX(MBeanServer server, ObjectName beanName) {
        try {
            log.info("Invoking operation reloadSslHostConfigs on " + beanName);
            server.invoke(beanName, JMX_OPERATION_RELOAD_SSL_HOST_CONFIGS_NAME, new Object[]{}, new String[]{});
        } catch (Exception e) {
            throw new SSLCertificateException(e);
        }
    }
}
