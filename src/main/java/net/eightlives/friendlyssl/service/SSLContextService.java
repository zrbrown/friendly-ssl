package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.exception.FriendlySSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import java.util.Set;

@Component
public class SSLContextService {

    private static final Logger LOG = LoggerFactory.getLogger(SSLContextService.class);

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
                throw new FriendlySSLException("Cannot locate any JMX thread pool. SSL context will not be reloaded" +
                        " and certificate will not be used unless server is restarted.");
            }

            LOG.info("Reloading SSL context for " + beans.size() + " MBeans");
            beans.forEach(bean -> reloadSSLConfigOnThreadPoolJMX(mBeanServer, bean.getObjectName()));
            LOG.info("Finished reloading SSL context");
        } catch (MalformedObjectNameException e) {
            throw new FriendlySSLException(e);
        }
    }

    private void reloadSSLConfigOnThreadPoolJMX(MBeanServer server, ObjectName beanName) {
        try {
            LOG.info("Invoking operation reloadSslHostConfigs on " + beanName);
            server.invoke(beanName, JMX_OPERATION_RELOAD_SSL_HOST_CONFIGS_NAME, new Object[]{}, new String[]{});
        } catch (Exception e) {
            throw new FriendlySSLException(e);
        }
    }
}
