package dev.normalbias.api;

import java.io.IOException;
import java.nio.channels.*;
import java.nio.file.*;

/** Prevents multiple API processes from owning the same data directory. */
public final class StorageDirectory implements AutoCloseable {
    private final Path root;
    private final FileChannel channel;
    private final FileLock lock;

    public StorageDirectory(Path directory, boolean create) throws IOException {
        root = directory.toAbsolutePath().normalize();
        if (create) Files.createDirectories(root);
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Data directory must be an existing directory, not a symlink");
        Path lockFile = root.resolve(".normal-bias.lock");
        if (Files.isSymbolicLink(lockFile)) throw new IOException("Data-directory lock must not be a symlink");
        channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try {
            acquired = channel.tryLock();
        } catch (IOException | OverlappingFileLockException e) {
            channel.close();
            throw new IOException("Data directory is already in use by another API process", e);
        }
        if (acquired == null) {
            channel.close();
            throw new IOException("Data directory is already in use by another API process");
        }
        lock = acquired;
    }

    public Path root() { return root; }

    @Override public void close() throws IOException {
        try { lock.release(); }
        finally { channel.close(); }
    }
}
