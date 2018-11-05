package org.adbhut.integration.demo;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ImageBanner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.file.Files;
import org.springframework.integration.file.FileHeaders;
import org.springframework.integration.ftp.session.DefaultFtpSessionFactory;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.integration.transformer.GenericTransformer;
import org.springframework.messaging.Message;
import org.springframework.util.ReflectionUtils;

@SpringBootApplication
public class SpringIntegrationDemoApplication {
    @Bean
    DefaultFtpSessionFactory ftpFileSessionFactory(@Value("${ftp.port:54218}") int port,
            @Value("${ftp.username:adbhut}") String userName, @Value("${ftp.password:password}") String password) {

        DefaultFtpSessionFactory ftpSessionFactory = new DefaultFtpSessionFactory();

        ftpSessionFactory.setPort(port);
        ftpSessionFactory.setUsername(userName);
        ftpSessionFactory.setPassword(password);
        ftpSessionFactory.setClientMode(2);

        return ftpSessionFactory;
    }

    @Bean
    IntegrationFlow files(@Value("${input-directory:c:/Users/adbhu/Desktop/in}") File in,
            DefaultFtpSessionFactory ftpSessionFactory, Environment environment) {
        GenericTransformer<File, Message<String>> fileStringGenericTransformer = (File source) -> {

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrintStream printStream = new PrintStream(baos)) {
                ImageBanner imageBanner = new ImageBanner(new FileSystemResource(source));
                imageBanner.printBanner(environment, getClass(), printStream);
                return MessageBuilder.withPayload(new String(baos.toByteArray()))
                        .setHeader(FileHeaders.FILENAME, source.getAbsoluteFile()
                                .getName())
                        .build();
            } catch (IOException ex) {
                ReflectionUtils.rethrowRuntimeException(ex);
            }
            return null;
        };

        return IntegrationFlows.from(Files.inboundAdapter(in)
                .autoCreateDirectory(true)
                .preventDuplicates(true)
                .patternFilter("*.jpg"), poller -> poller.poller(pm -> pm.fixedRate(1000)))
                .transform(File.class, fileStringGenericTransformer)
                .handleWithAdapter(adapters -> adapters.ftp(ftpSessionFactory)
                        .remoteDirectory("uploads")
                        .fileNameGenerator(message -> {
                            Object obj = message.getHeaders()
                                    .get(FileHeaders.FILENAME);
                            String fileName = String.class.cast(obj);
                            return fileName.split("\\.")[0] + ".txt";

                        }))
                .get();

    }

    public static void main(String[] args) {
        SpringApplication.run(SpringIntegrationDemoApplication.class, args);
    }
}
