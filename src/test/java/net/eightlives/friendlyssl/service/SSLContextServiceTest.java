package net.eightlives.friendlyssl.service;

import net.eightlives.friendlyssl.exception.FriendlySSLException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.management.*;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SSLContextServiceTest {

    private SSLContextService service;

    @Mock
    private MBeanServer mBeanServer;

    @BeforeEach
    void setUp() {
        service = new SSLContextService(mBeanServer);
    }

    @DisplayName("When mBean server does not return any JMX thread pools")
    @Test
    void noJMXThreadPools() {
        when(mBeanServer.queryMBeans(any(ObjectName.class), isNull())).thenReturn(Set.of());

        assertThrows(FriendlySSLException.class, () -> service.reloadSSLConfig());
    }

    @DisplayName("When mBean server returns JMX thread pool")
    @Nested
    class JMXThreadPool {

        private ObjectName beanName;

        @BeforeEach
        void setUp() throws MalformedObjectNameException {
            beanName = new ObjectName("*:type=ThreadPool,name=*");
            ObjectInstance objectInstance = mock(ObjectInstance.class);
            when(objectInstance.getObjectName()).thenReturn(beanName);
            when(mBeanServer.queryMBeans(eq(beanName), isNull())).thenReturn(Set.of(objectInstance));
        }

        @DisplayName("When SSL reload fails")
        @ParameterizedTest(name = "with exception {0}")
        @ValueSource(classes = {MBeanException.class, InstanceNotFoundException.class, ReflectionException.class})
        void sslReloadFail(Class<Throwable> exceptionClass) throws MBeanException, InstanceNotFoundException, ReflectionException {
            when(mBeanServer.invoke(beanName, "reloadSslHostConfigs", new Object[]{}, new String[]{}))
                    .thenThrow(exceptionClass);

            assertThrows(FriendlySSLException.class, () -> service.reloadSSLConfig());
        }

        @DisplayName("When SSL reload succeeds")
        @Test
        void sslReloadSuccess() throws MBeanException, InstanceNotFoundException, ReflectionException {
            service.reloadSSLConfig();

            verify(mBeanServer, times(1))
                    .invoke(beanName, "reloadSslHostConfigs", new Object[]{}, new String[]{});
        }
    }
}
