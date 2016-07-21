package org.testcontainers.junit;

import com.github.dockerjava.api.command.BuildImageCmd;
import org.junit.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.WaitingConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.rnorth.visibleassertions.VisibleAssertions.pass;
import static org.testcontainers.containers.output.OutputFrame.OutputType.STDOUT;

public class DockerfileTest {

    @Test
    public void simpleDockerfileWorks() {
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withFileFromString("folder/someFile.txt", "hello")
                .withFileFromClasspath("test.txt", "mappable-resource/test-resource.txt")
                .withFileFromClasspath("Dockerfile", "mappable-dockerfile/Dockerfile");

        verifyImage(image);
    }

    @Test
    public void customizableImage() {
        ImageFromDockerfile image = new ImageFromDockerfile() {
            @Override
            protected void configure(BuildImageCmd buildImageCmd) {
                super.configure(buildImageCmd);

                List<String> dockerfile = Arrays.asList(
                        "FROM alpine:3.2",
                        "RUN echo 'hello from Docker build process'",
                        "CMD yes"
                );
                withFileFromString("Dockerfile", String.join("\n", dockerfile));

                buildImageCmd.withNoCache(true);
            }
        };

        verifyImage(image);
    }

    @Test
    public void dockerfileBuilderWorks() {
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withFileFromClasspath("test.txt", "mappable-resource/test-resource.txt")
                .withFileFromString("folder/someFile.txt", "hello")
                .withDockerfileFromBuilder(builder -> builder
                        .from("alpine:3.2")
                        .workDir("/app")
                        .add("test.txt", "test file.txt")
                        .run("ls", "-la", "/app/test file.txt")
                        .copy("folder/someFile.txt", "/someFile.txt")
                        .expose(80, 8080)
                        .cmd("while true; do cat /someFile.txt | nc -l -p 80; done")
                );

        verifyImage(image);
    }

    @Test
    public void filePermissions() throws TimeoutException {
        ImageFromDockerfile image = new ImageFromDockerfile()
                .withFileFromTransferable("/someFile.txt", new Transferable() {
                    @Override
                    public long getSize() {
                        return 0;
                    }

                    @Override
                    public int getFileMode() {
                        return 0123;
                    }

                    @Override
                    public void transferTo(OutputStream outputStream) {

                    }
                })
                .withDockerfileFromBuilder(builder -> builder
                        .from("alpine:3.2")
                        .copy("someFile.txt", "/someFile.txt")
                        .cmd("stat -c \"%a\" /someFile.txt")
                );

        GenericContainer container = new GenericContainer(image);

        try {
            container.start();

            WaitingConsumer consumer = new WaitingConsumer();

            container.followOutput(consumer, STDOUT);

            consumer.waitUntil(frame -> frame.getUtf8String().contains("123"), 5, TimeUnit.SECONDS);
        } finally {
            container.stop();
        }
    }

    protected void verifyImage(ImageFromDockerfile image) {
        GenericContainer container = new GenericContainer(image);

        try {
            container.start();

            pass("Should start from Dockerfile");
        } finally {
            container.stop();
        }
    }
}
