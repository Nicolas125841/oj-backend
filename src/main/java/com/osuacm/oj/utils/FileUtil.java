package com.osuacm.oj.utils;

import com.osuacm.oj.services.ProblemService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.FileSystemUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipFile;

public class FileUtil {
    private static final Log log = LogFactory.getLog(FileUtil.class);

    public enum FileType {FILE, DIRECTORY, DIRECTORY_RECURSIVE};

    @Async
    public CompletableFuture<Boolean> deleteFile(Path file, FileType fileType) {
        return switch (fileType){
            case FILE, DIRECTORY -> {
                try {
                    yield CompletableFuture.completedFuture(Files.deleteIfExists(file));
                } catch (IOException e) {
                    yield CompletableFuture.failedFuture(e);
                }
            }
            case DIRECTORY_RECURSIVE -> {
                try {
                    yield CompletableFuture.completedFuture(FileSystemUtils.deleteRecursively(file));
                } catch (IOException e) {
                    yield CompletableFuture.failedFuture(e);
                }
            }
        };
    }

    @Async
    public CompletableFuture<Path> createFile(Path file, FileType fileType) {
        return switch (fileType) {
            case FILE -> {
                try {
                    yield CompletableFuture.completedFuture(Files.createFile(file));
                } catch (IOException e) {
                    yield CompletableFuture.failedFuture(e);
                }
            }
            case DIRECTORY, DIRECTORY_RECURSIVE -> {
                try {
                    yield CompletableFuture.completedFuture(Files.createDirectory(file));
                } catch (IOException e) {
                    yield CompletableFuture.failedFuture(e);
                }
            }
        };
    }

    @Async
    public CompletableFuture<Void> unzip(Path file) {
        try(ZipFile zipFile = new ZipFile(file.toFile())){
            Path testDirectory = file.getParent();

            zipFile.entries().asIterator().forEachRemaining(zipEntry -> {
                log.info(zipEntry.getName());
                log.info(zipEntry.isDirectory());

                File destination = new File(testDirectory.toFile(), zipEntry.getName());

                if(zipEntry.isDirectory()){
                    if(!destination.mkdirs()){
                        throw new RuntimeException("Cannot make dir: " + destination.getName());
                    }
                }else{
                    if(!destination.getParentFile().exists()){
                        if(!destination.getParentFile().mkdirs()){
                            throw new RuntimeException("Cannot make dir: " + destination.getName());
                        }
                    }

                    try (InputStream in = zipFile.getInputStream(zipEntry);
                         OutputStream out = new FileOutputStream(destination)) {
                        in.transferTo(out);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });

            zipFile.close();

            Files.delete(file);

            return CompletableFuture.completedFuture(null);
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
