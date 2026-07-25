package ru.logunov.bydsplit;

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

final class LocalAdbKeyStore {
    private static final String PRIVATE_FILE = "private.pk8";
    private static final String PUBLIC_FILE = "public.der";
    private static final int ADB_RSA_WORDS = 64;
    private static final int ADB_RSA_BYTES = ADB_RSA_WORDS * 4;

    private final File directory;

    LocalAdbKeyStore(Context context) {
        directory = new File(context.getFilesDir(), "local_adb");
    }

    synchronized KeyPair loadOrCreate() throws Exception {
        File privateFile = new File(directory, PRIVATE_FILE);
        File publicFile = new File(directory, PUBLIC_FILE);
        if (privateFile.isFile() && publicFile.isFile()) {
            try {
                KeyFactory factory = KeyFactory.getInstance("RSA");
                PrivateKey privateKey = factory.generatePrivate(
                        new PKCS8EncodedKeySpec(readAll(privateFile)));
                PublicKey publicKey = factory.generatePublic(
                        new X509EncodedKeySpec(readAll(publicFile)));
                return new KeyPair(publicKey, privateKey);
            } catch (Exception ignored) {
                // Повреждённая пара заменяется новой.
            }
        }

        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create ADB key directory");
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        writeAll(privateFile, pair.getPrivate().getEncoded());
        writeAll(publicFile, pair.getPublic().getEncoded());
        return pair;
    }

    byte[] encodeAdbPublicKey(PublicKey key) {
        RSAPublicKey rsa = (RSAPublicKey) key;
        BigInteger modulus = rsa.getModulus();
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        long n0inv = modulus.and(two32.subtract(BigInteger.ONE))
                .modInverse(two32).negate().mod(two32).longValue();
        BigInteger rr = BigInteger.ONE.shiftLeft(ADB_RSA_BYTES * 8)
                .mod(modulus);

        ByteBuffer structure = ByteBuffer.allocate(4 + 4
                + ADB_RSA_BYTES + ADB_RSA_BYTES + 4)
                .order(ByteOrder.LITTLE_ENDIAN);
        structure.putInt(ADB_RSA_WORDS);
        structure.putInt((int) n0inv);
        structure.put(toLittleEndian(modulus, ADB_RSA_BYTES));
        structure.put(toLittleEndian(rr, ADB_RSA_BYTES));
        structure.putInt(rsa.getPublicExponent().intValue());

        String encoded = Base64.encodeToString(
                structure.array(), Base64.NO_WRAP);
        return (encoded + " bydsplit@dilink\0")
                .getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static byte[] toLittleEndian(BigInteger value, int size) {
        byte[] bigEndian = value.toByteArray();
        byte[] result = new byte[size];
        int useful = bigEndian.length;
        if (useful > 1 && bigEndian[0] == 0) {
            useful--;
        }
        for (int index = 0; index < Math.min(useful, size); index++) {
            result[index] = bigEndian[bigEndian.length - 1 - index];
        }
        return result;
    }

    private static byte[] readAll(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < bytes.length) {
                int read = input.read(bytes, offset, bytes.length - offset);
                if (read < 0) {
                    throw new IllegalStateException("Unexpected EOF");
                }
                offset += read;
            }
        }
        return bytes;
    }

    private static void writeAll(File file, byte[] bytes) throws Exception {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.getFD().sync();
        }
    }
}
