package net.eightlives.friendlyssl.annotation;

import net.eightlives.friendlyssl.integration.AcmeServerInitializer;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("integration")
@Tag("slow")
@SpringBootTest
@ContextConfiguration(initializers = AcmeServerInitializer.class)
public @interface PebbleTest {
}
