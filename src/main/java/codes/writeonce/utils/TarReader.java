package codes.writeonce.utils;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.overviewproject.mime_types.GetBytesException;
import org.overviewproject.mime_types.MimeTypeDetector;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.zip.GZIPOutputStream;

import static java.nio.channels.FileChannel.MapMode.READ_ONLY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE_NEW;
import static java.nio.file.StandardOpenOption.READ;
import static java.nio.file.StandardOpenOption.WRITE;

public final class TarReader {

    private static final String DIGEST_ALGORITHM = "SHA-256";

    @Nonnull
    public static TarArch readTar(
            @Nonnull Path path,
            @Nonnull Path uncompressedDataPath,
            @Nonnull Path compressedDataPath
    ) throws NoSuchAlgorithmException, IOException {

        try (var fileInputStream = Files.newInputStream(path)) {
            return readTar(fileInputStream, uncompressedDataPath, compressedDataPath);
        }
    }

    @Nonnull
    public static TarArch readTar(
            @Nonnull InputStream fileInputStream,
            @Nonnull Path uncompressedDataPath,
            @Nonnull Path compressedDataPath
    ) throws NoSuchAlgorithmException, IOException {

        final var now = Instant.now();
        final var buffer = new byte[0x1000000];
        final var entries = new HashMap<String, TarEntry>();
        final var digest = MessageDigest.getInstance(DIGEST_ALGORITHM);

        Files.deleteIfExists(uncompressedDataPath);
        Files.deleteIfExists(compressedDataPath);

        try (var uncompressedChannel = FileChannel.open(uncompressedDataPath, READ, WRITE, CREATE_NEW);
             var compressedChannel = FileChannel.open(compressedDataPath, READ, WRITE, CREATE_NEW);
             var uncompressedOutputStream = Channels.newOutputStream(uncompressedChannel);
             var compressedOutputStream = Channels.newOutputStream(compressedChannel);
             var xzInputStream = new XZCompressorInputStream(fileInputStream);
             var tarInputStream = new TarArchiveInputStream(xzInputStream, UTF_8.name())) {

            while (true) {
                final var entry = tarInputStream.getNextTarEntry();
                if (entry == null) {
                    break;
                }
                if (entry.isFile()) {
                    final var name = entry.getName();
                    final var size = entry.getSize();
                    if (size > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException();
                    }
                    final var lastModifiedDate = entry.getLastModifiedDate().toInstant();
                    if (lastModifiedDate.isAfter(now)) {
                        throw new IllegalArgumentException();
                    }
                    final var offset = (int) uncompressedChannel.size();
                    final var archOffset = (int) compressedChannel.size();
                    digest.reset();
                    try (var gzipOutputStream = new GZIPOutputStream(new NoCloseOutputStream(compressedOutputStream))) {
                        var remained = size;
                        while (remained != 0) {
                            final var read = tarInputStream.read(buffer, 0, (int) Math.min(buffer.length, remained));
                            if (read < 0) {
                                throw new IllegalArgumentException();
                            }
                            digest.update(buffer, 0, read);
                            uncompressedOutputStream.write(buffer, 0, read);
                            remained -= read;
                            gzipOutputStream.write(buffer, 0, read);
                        }
                    }
                    final var archSize = (int) compressedChannel.size() - archOffset;
                    entries.put("/" + name, new TarEntry(
                            offset,
                            (int) size,
                            archOffset,
                            archSize,
                            lastModifiedDate,
                            Base64.getEncoder().encodeToString(digest.digest())
                    ));
                }
            }

            final var uncompressedDataBuffer = uncompressedChannel.map(READ_ONLY, 0, uncompressedChannel.size());
            final var compressedDataBuffer = compressedChannel.map(READ_ONLY, 0, compressedChannel.size());

            final var mimeTypeDetector = new MimeTypeDetector();
            entries.forEach((k, v) -> {
                try {
                    final var bytes = new byte[v.size];
                    uncompressedDataBuffer.get(v.offset, bytes);
                    final var mimeType = mimeTypeDetector.detectMimeType(k, () -> bytes);
                    v.setMimeType(addCharset(mimeType));
                } catch (GetBytesException e) {
                    throw new IllegalArgumentException(e);
                }
            });

            return new TarArch(entries, uncompressedDataBuffer, compressedDataBuffer);
        }
    }

    public static void main(String[] args) throws GetBytesException {
        final var mimeTypeDetector = new MimeTypeDetector();
        System.out.println(mimeTypeDetector.detectMimeType(
                Path.of("C:\\Users\\alexe\\IdeaProjects\\trade-mate\\main-1.0.0-SNAPSHOT\\static\\images\\loader.gif")));
    }

    @Nullable
    private static String addCharset(@Nullable String mimeType) {

        if (mimeType == null) {
            return null;
        }

        return switch (mimeType) {
            case "application/json", "application/javascript", "text/html", "text/xml", "text/plain", "text/css", "text/markdown", "image/svg+xml" ->
                    mimeType + "; charset=UTF-8";
            default -> mimeType;
        };
    }

    public static class TarArch {

        public final HashMap<String, TarEntry> entries;
        public final ByteBuffer buffer;
        public final ByteBuffer compressedBuffer;

        public TarArch(HashMap<String, TarEntry> entries, ByteBuffer buffer, ByteBuffer compressedBuffer) {
            this.entries = entries;
            this.buffer = buffer;
            this.compressedBuffer = compressedBuffer;
        }
    }

    public static class TarEntry {

        public final int offset;

        public final int size;

        public final int archOffset;

        public final int archSize;

        @Nonnull
        public final Instant lastModifiedDate;

        @Nonnull
        public final String digest;

        @Nullable
        public String mimeType;

        public TarEntry(int offset, int size, int archOffset, int archSize, @Nonnull Instant lastModifiedDate,
                @Nonnull String digest) {
            this.offset = offset;
            this.size = size;
            this.archOffset = archOffset;
            this.archSize = archSize;
            this.lastModifiedDate = lastModifiedDate;
            this.digest = digest;
        }

        private void setMimeType(@Nullable String mimeType) {
            this.mimeType = mimeType;
        }
    }

    private static final class NoCloseOutputStream extends OutputStream {

        @Nonnull
        private final OutputStream out;

        public NoCloseOutputStream(@Nonnull OutputStream out) {
            this.out = out;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
        }

        @Override
        public void flush() throws IOException {
            out.flush();
        }

        @Override
        public void write(@Nonnull byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void write(@Nonnull byte[] b) throws IOException {
            out.write(b);
        }

        @Override
        public void close() throws IOException {
            out.flush();
        }
    }

    private TarReader() {
        // empty
    }
}
