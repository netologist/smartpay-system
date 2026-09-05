package com.hozgan.smartpay.common.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * High-performance, RFC 9562 compliant UUIDv7 generator.
 * <p>
 * UUIDv7 layout (128 bits):
 * <ul>
 *   <li>0-47: 48-bit unsigned Big-Endian integer Unix Epoch timestamp in milliseconds.</li>
 *   <li>48-51: 4-bit version field set to 0111 (7).</li>
 *   <li>52-63: 12-bit pseudo-random data.</li>
 *   <li>64-65: 2-bit variant field set to 10.</li>
 *   <li>66-127: 62-bit pseudo-random data.</li>
 * </ul>
 */
public final class UuidV7 {

    private static final long VERSION_7 = 0x0000_0000_0000_7000L;
    private static final long VARIANT_RFC_9562 = 0x8000_0000_0000_0000L;
    private static final long VARIANT_MASK = 0x3FFF_FFFF_FFFF_FFFFL;
    private static final long RAND_A_MASK = 0x0FFFL;

    private UuidV7() {
        // static utility class
    }

    /**
     * Generates a time-ordered UUIDv7 using current system time.
     *
     * @return time-ordered UUIDv7
     */
    public static UUID generate() {
        return generate(System.currentTimeMillis());
    }

    /**
     * Generates a UUIDv7 using a specific epoch millisecond timestamp.
     *
     * @param epochMilli timestamp in milliseconds since Unix epoch
     * @return time-ordered UUIDv7
     */
    public static UUID generate(long epochMilli) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        long randA = random.nextLong() & RAND_A_MASK;
        long msb = (epochMilli << 16) | VERSION_7 | randA;

        long randB = random.nextLong() & VARIANT_MASK;
        long lsb = VARIANT_RFC_9562 | randB;

        return new UUID(msb, lsb);
    }

    /**
     * Generates a UUIDv7 using an {@link Instant}.
     *
     * @param instant timestamp instant
     * @return time-ordered UUIDv7
     */
    public static UUID generate(Instant instant) {
        Objects.requireNonNull(instant, "instant cannot be null");
        return generate(instant.toEpochMilli());
    }

    /**
     * Extracts the Unix timestamp as an {@link Instant} from a UUIDv7.
     *
     * @param uuid UUIDv7 instance
     * @return creation instant
     */
    public static Instant extractTimestamp(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid cannot be null");
        long epochMilli = uuid.getMostSignificantBits() >>> 16;
        return Instant.ofEpochMilli(epochMilli);
    }

    /**
     * Verifies if a given UUID conforms to the UUIDv7 specification.
     *
     * @param uuid UUID to check
     * @return true if UUID version is 7 and variant is 2 (RFC 4122 / RFC 9562)
     */
    public static boolean isUuidV7(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        return uuid.version() == 7 && uuid.variant() == 2;
    }
}
