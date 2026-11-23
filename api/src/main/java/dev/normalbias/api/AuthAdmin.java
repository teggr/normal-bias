package dev.normalbias.api;

import com.fasterxml.jackson.databind.*;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;

/** Owner-only filesystem transport to the RUNNING service, never a second H2 writer. */
@Component
@DependsOn("flywayInitializer")
class AuthAdmin implements AutoCloseable {
    private final AuthService service; private final ObjectMapper json;
    private ServerSocketChannel listener; private Path socket;
    AuthAdmin(AuthService service, ObjectMapper json, StorageDirectory directory, AuthSecurity security) throws IOException {
        this.service = service; this.json = json;
        if (security.demo()) return;
        socket = socket(directory.root()); Path parent = socket.getParent();
        try { Files.createDirectory(parent, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))); }
        catch (FileAlreadyExistsException ignored) { }
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || !Files.getOwner(parent).equals(Files.getOwner(directory.root())))
            throw new IOException("Unsafe local administration directory");
        Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("rwx------"));
        if (Files.exists(socket, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.readAttributes(socket, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS).isOther() || !Files.getOwner(socket).equals(Files.getOwner(parent)))
                throw new IOException("Unsafe local administration socket");
            Files.delete(socket); // Data-directory ownership is already held by this process.
        }
        listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        listener.bind(UnixDomainSocketAddress.of(socket));
        Files.setPosixFilePermissions(socket, PosixFilePermissions.fromString("rw-------"));
        Thread.ofVirtual().name("normalbias-local-admin").start(() -> {
            while (listener.isOpen()) {
                try { SocketChannel connection = listener.accept(); Thread.ofVirtual().start(() -> serve(connection)); }
                catch (IOException closed) { if (listener.isOpen()) throw new UncheckedIOException(closed); }
            }
        });
    }
    static Path socket(Path data) throws IOException {
        // A short, stable Unix-domain path also works with macOS's small path limit.
        return Path.of("/tmp", "normalbias-admin-" + AuthService.hash(data.toRealPath().toString()).substring(0, 32), "control.sock");
    }
    private void serve(SocketChannel connection) {
        try (connection; var input = new DataInputStream(Channels.newInputStream(connection)); var output = new DataOutputStream(Channels.newOutputStream(connection))) {
            int length = input.readInt(); if (length < 1 || length > 16_384) return;
            byte[] bytes = input.readNBytes(length); if (bytes.length != length) return;
            Map<String, Object> response;
            try { response = Map.of("ok", true, "result", execute(json.readTree(bytes))); }
            catch (ResponseStatusException failure) { response = Map.of("ok", false, "message", Objects.requireNonNullElse(failure.getReason(), "Command failed")); }
            catch (Exception failure) { response = Map.of("ok", false, "message", "Invalid administration command"); }
            byte[] reply = json.writeValueAsBytes(response); output.writeInt(reply.length); output.write(reply); output.flush();
        } catch (IOException ignored) { /* Never log credential-bearing commands. */ }
    }
    private Object execute(JsonNode request) {
        String operation = request.path("operation").asText(), username = request.path("username").asText();
        String password = request.path("password").asText(null), id = request.path("id").asText();
        int offset = request.path("offset").asInt(0), limit = request.path("limit").asInt(50);
        return switch (operation) {
            case "bootstrap" -> { service.bootstrap(username, password); yield Map.of("created", true); }
            case "invite" -> service.invitation();
            case "reset" -> service.reset(username);
            case "recover-owner" -> { service.recoverOwner(username, password); yield Map.of("recovered", true); }
            case "disable", "enable" -> { service.enabled(username, operation.equals("enable")); yield Map.of("enabled", operation.equals("enable")); }
            case "users" -> service.accounts(offset, limit);
            case "sessions" -> service.sessions(username, offset, limit);
            case "revoke" -> { service.revoke(id); yield Map.of("revoked", true); }
            case "revoke-link" -> { service.revokeLink(id); yield Map.of("revoked", true); }
            case "upload-token" -> {
                var issued = service.uploadCredential(username);
                yield Map.of("id", issued.identity().credentialId(), "token", issued.token(), "absoluteExpiresAt", issued.identity().absoluteExpires());
            }
            default -> throw new IllegalArgumentException("Invalid operation");
        };
    }
    @PreDestroy public void close() throws IOException {
        if (listener != null) { listener.close(); Files.deleteIfExists(socket); }
    }
    static Map<String, Object> exchange(Path data, Map<String, Object> request) throws IOException {
        Path socket = socket(data);
        if (Files.isSymbolicLink(socket) || !Files.getOwner(socket).equals(Files.getOwner(data))) throw new IOException("Unsafe administration socket");
        ObjectMapper json = new ObjectMapper();
        try (SocketChannel connection = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            connection.connect(UnixDomainSocketAddress.of(socket));
            var output = new DataOutputStream(Channels.newOutputStream(connection)); var input = new DataInputStream(Channels.newInputStream(connection));
            byte[] bytes = json.writeValueAsBytes(request); output.writeInt(bytes.length); output.write(bytes); output.flush();
            int length = input.readInt(); if (length < 1 || length > 1_048_576) throw new IOException("Invalid administration response");
            byte[] reply = input.readNBytes(length); if (reply.length != length) throw new IOException("Incomplete administration response");
            return json.readValue(reply, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        }
    }
    static void command(String[] args) {
        try {
            Path data = Path.of("./data"), tokenOutput = null; boolean stdin = false; var words = new ArrayList<String>();
            var request = new LinkedHashMap<String, Object>();
            for (String argument : Arrays.copyOfRange(args, 1, args.length)) {
                if (argument.startsWith("--data-dir=")) data = Path.of(argument.substring(11));
                else if (argument.startsWith("--token-out=")) tokenOutput = Path.of(argument.substring(12));
                else if (argument.equals("--password-stdin")) stdin = true;
                else if (argument.startsWith("--offset=")) request.put("offset", Integer.parseInt(argument.substring(9)));
                else if (argument.startsWith("--limit=")) request.put("limit", Integer.parseInt(argument.substring(8)));
                else if (argument.startsWith("--")) throw new IllegalArgumentException();
                else words.add(argument);
            }
            if (words.isEmpty()) throw new IllegalArgumentException();
            String operation = words.getFirst(); request.put("operation", operation);
            if (tokenOutput != null && !operation.equals("upload-token")) throw new IllegalArgumentException();
            boolean noArgument = Set.of("invite", "users").contains(operation);
            if (words.size() != (noArgument ? 1 : 2)) throw new IllegalArgumentException();
            if (!noArgument) request.put(Set.of("revoke", "revoke-link").contains(operation) ? "id" : "username", words.get(1));
            if (Set.of("bootstrap", "recover-owner").contains(operation)) {
                String password;
                if (stdin) {
                    byte[] input = System.in.readNBytes(1025); if (input.length > 1024) throw new IllegalArgumentException();
                    password = new String(input, java.nio.charset.StandardCharsets.UTF_8).replaceFirst("\\r?\\n$", ""); Arrays.fill(input, (byte) 0);
                } else {
                    Console console = System.console(); if (console == null) throw new IOException("Use an interactive terminal or --password-stdin");
                    char[] first = console.readPassword("New password: "), second = console.readPassword("Confirm password: ");
                    if (first == null || second == null || !Arrays.equals(first, second)) throw new IOException("Passwords did not match");
                    password = new String(first); Arrays.fill(first, '\0'); Arrays.fill(second, '\0');
                }
                request.put("password", password);
            } else if (stdin) throw new IllegalArgumentException();
            Map<String, Object> response = exchange(data, request);
            if (!Boolean.TRUE.equals(response.get("ok"))) throw new IOException((String) response.get("message"));
            if (tokenOutput != null) {
                @SuppressWarnings("unchecked") var result = new LinkedHashMap<>((Map<String, Object>) response.get("result"));
                String token = (String) result.remove("token");
                try (var channel = Files.newByteChannel(tokenOutput, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))) {
                    var buffer = java.nio.ByteBuffer.wrap((token + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    while (buffer.hasRemaining()) channel.write(buffer);
                }
                result.put("credentialFile", tokenOutput.toString()); response = Map.of("result", result);
            }
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out, response.get("result"));
        } catch (IllegalArgumentException failure) {
            System.err.println("Usage: api.jar admin OPERATION [USERNAME|ID] --data-dir=PATH [--password-stdin] [--token-out=PATH] [--offset=N --limit=N]"); System.exit(2);
        } catch (IOException failure) { System.err.println("Local administration failed: " + failure.getMessage()); System.exit(2); }
    }
}
