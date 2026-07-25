package ru.logunov.bydsplit;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Cipher;

/**
 * Минимальный клиент двоичного ADB-протокола. Он намеренно поддерживает только
 * локальный shell: произвольный удалённый адрес приложению не доступен.
 */
final class LocalAdbClient implements Closeable {
    private static final String TAG = "BYD_LOCAL_ADB";
    private static final int A_CNXN = 0x4e584e43;
    private static final int A_AUTH = 0x48545541;
    private static final int A_OPEN = 0x4e45504f;
    private static final int A_OKAY = 0x59414b4f;
    private static final int A_CLSE = 0x45534c43;
    private static final int A_WRTE = 0x45545257;
    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_PUBLIC_KEY = 3;
    private static final int VERSION = 0x01000000;
    private static final int MAX_PAYLOAD = 1024 * 1024;
    private static final byte[] SHA1_DIGEST_INFO = hex(
            "3021300906052b0e03021a05000414");

    private final LocalAdbKeyStore keyStore;
    private final AtomicInteger nextLocalId = new AtomicInteger(1);
    private Socket socket;
    private InputStream input;
    private OutputStream output;

    LocalAdbClient(LocalAdbKeyStore keyStore) {
        this.keyStore = keyStore;
    }

    synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    synchronized void connect() throws Exception {
        if (isConnected()) {
            return;
        }
        close();
        Socket candidate = new Socket();
        candidate.connect(new InetSocketAddress("127.0.0.1", 5555), 1800);
        candidate.setSoTimeout(65000);
        socket = candidate;
        input = candidate.getInputStream();
        output = candidate.getOutputStream();

        KeyPair keys = keyStore.loadOrCreate();
        send(A_CNXN, VERSION, 4096,
                "host::bydsplit\0".getBytes(StandardCharsets.US_ASCII));
        boolean sentPublicKey = false;
        while (true) {
            Packet response = readPacket();
            if (response.command == A_CNXN) {
                socket.setSoTimeout(15000);
                return;
            }
            if (response.command != A_AUTH || response.arg0 != AUTH_TOKEN) {
                throw new IllegalStateException("Unexpected ADB handshake");
            }
            if (!sentPublicKey) {
                send(A_AUTH, AUTH_SIGNATURE, 0,
                        signToken(keys, response.payload));
                sentPublicKey = true;
            } else {
                send(A_AUTH, AUTH_PUBLIC_KEY, 0,
                        keyStore.encodeAdbPublicKey(keys.getPublic()));
            }
        }
    }

    synchronized String shell(String command) throws Exception {
        return runService("shell:" + command + "\0", false);
    }

    synchronized String shellV2(String command) throws Exception {
        return runService("shell,v2,raw:" + command + "\0", true);
    }

    private String runService(String serviceName, boolean shellV2)
            throws Exception {
        connect();
        int localId = nextLocalId.getAndIncrement();
        byte[] service = serviceName.getBytes(StandardCharsets.UTF_8);
        send(A_OPEN, localId, 0, service);

        int remoteId = 0;
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        while (true) {
            Packet packet = readPacket();
            if (packet.command == A_OKAY && packet.arg1 == localId) {
                remoteId = packet.arg0;
                continue;
            }
            if (packet.command == A_WRTE && packet.arg1 == localId) {
                remoteId = packet.arg0;
                result.write(packet.payload);
                send(A_OKAY, localId, remoteId, new byte[0]);
                continue;
            }
            if (packet.command == A_CLSE && packet.arg1 == localId) {
                send(A_CLSE, localId,
                        remoteId == 0 ? packet.arg0 : remoteId, new byte[0]);
                byte[] raw = result.toByteArray();
                return shellV2
                        ? decodeShellV2(raw)
                        : new String(raw, StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * Запускает полностью перенаправленный фоновый процесс. Старый adbd DiLink
     * оставляет legacy-shell поток открытым, пока жив любой его потомок, поэтому
     * после подтверждения OPEN транспорт намеренно отсоединяется.
     */
    synchronized void shellDetached(String command) throws Exception {
        connect();
        int localId = nextLocalId.getAndIncrement();
        send(A_OPEN, localId, 0, ("shell:" + command + "\0")
                .getBytes(StandardCharsets.UTF_8));
        while (true) {
            Packet packet = readPacket();
            if (packet.command == A_OKAY && packet.arg1 == localId) {
                try {
                    Thread.sleep(350);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
                close();
                return;
            }
            if (packet.command == A_CLSE && packet.arg1 == localId) {
                close();
                throw new IllegalStateException(
                        "ADB rejected detached shell");
            }
        }
    }

    @Override
    public synchronized void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
        socket = null;
        input = null;
        output = null;
    }

    private byte[] signToken(KeyPair keys, byte[] token) throws Exception {
        if (token.length != 20) {
            throw new IllegalArgumentException("Unexpected AUTH token length");
        }
        byte[] block = new byte[256];
        block[0] = 0;
        block[1] = 1;
        int separator = block.length - token.length
                - SHA1_DIGEST_INFO.length - 1;
        java.util.Arrays.fill(block, 2, separator, (byte) 0xff);
        block[separator] = 0;
        System.arraycopy(SHA1_DIGEST_INFO, 0, block,
                separator + 1, SHA1_DIGEST_INFO.length);
        System.arraycopy(token, 0, block,
                block.length - token.length, token.length);
        Cipher rsa = Cipher.getInstance("RSA/ECB/NoPadding");
        rsa.init(Cipher.ENCRYPT_MODE, keys.getPrivate());
        return rsa.doFinal(block);
    }

    private void send(int command, int arg0, int arg1, byte[] payload)
            throws Exception {
        int checksum = 0;
        for (byte value : payload) {
            checksum += value & 0xff;
        }
        ByteBuffer header = ByteBuffer.allocate(24)
                .order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command);
        header.putInt(arg0);
        header.putInt(arg1);
        header.putInt(payload.length);
        header.putInt(checksum);
        header.putInt(command ^ 0xffffffff);
        output.write(header.array());
        output.write(payload);
        output.flush();
    }

    private Packet readPacket() throws Exception {
        byte[] headerBytes = readExactly(24);
        ByteBuffer header = ByteBuffer.wrap(headerBytes)
                .order(ByteOrder.LITTLE_ENDIAN);
        int command = header.getInt();
        int arg0 = header.getInt();
        int arg1 = header.getInt();
        int length = header.getInt();
        int expectedChecksum = header.getInt();
        int magic = header.getInt();
        if (magic != (command ^ 0xffffffff)
                || length < 0 || length > MAX_PAYLOAD) {
            throw new IllegalStateException("Invalid ADB packet");
        }
        byte[] payload = readExactly(length);
        int checksum = 0;
        for (byte value : payload) {
            checksum += value & 0xff;
        }
        if (checksum != expectedChecksum) {
            throw new IllegalStateException("ADB checksum mismatch");
        }
        return new Packet(command, arg0, arg1, payload);
    }

    private byte[] readExactly(int length) throws Exception {
        byte[] bytes = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(bytes, offset, length - offset);
            if (read < 0) {
                throw new EOFException("ADB connection closed");
            }
            offset += read;
        }
        return bytes;
    }

    private static byte[] hex(String text) {
        byte[] result = new byte[text.length() / 2];
        for (int index = 0; index < result.length; index++) {
            result[index] = (byte) Integer.parseInt(
                    text.substring(index * 2, index * 2 + 2), 16);
        }
        return result;
    }

    private static String decodeShellV2(byte[] raw) {
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        int offset = 0;
        while (offset + 5 <= raw.length) {
            int stream = raw[offset] & 0xff;
            int length = ByteBuffer.wrap(raw, offset + 1, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).getInt();
            offset += 5;
            if (length < 0 || offset + length > raw.length) {
                break;
            }
            if (stream == 1 || stream == 2) {
                text.write(raw, offset, length);
            }
            offset += length;
        }
        return new String(text.toByteArray(), StandardCharsets.UTF_8);
    }

    private static final class Packet {
        final int command;
        final int arg0;
        final int arg1;
        final byte[] payload;

        Packet(int command, int arg0, int arg1, byte[] payload) {
            this.command = command;
            this.arg0 = arg0;
            this.arg1 = arg1;
            this.payload = payload;
        }
    }
}
