package be.uzleuven.ihe.dicom.creator.evil;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;

/**
 * Random decision helper for EVIL generators.
 *
 * Intentionally isolated in the .evil package so it can't affect normal generators.
 */
public final class EvilDice {

    /**
     * If -Devil.seed is provided, we switch to a deterministic Random.
     * Otherwise we use SecureRandom for unpredictability.
     */
    private static final Random RND = initRandom();

    private EvilDice() {
    }

    private static Random initRandom() {
        String seedProp = System.getProperty("Devil.seed");
        if (seedProp == null || seedProp.trim().isEmpty()) {
            seedProp = System.getProperty("evil.seed");
        }
        if (seedProp == null || seedProp.trim().isEmpty()) {
            seedProp = System.getProperty("Evil.seed");
        }
        if (seedProp == null || seedProp.trim().isEmpty()) {
            seedProp = System.getProperty("evilSeed");
        }
        if (seedProp == null || seedProp.trim().isEmpty()) {
            seedProp = System.getProperty("DevilSeed");
        }
        if (seedProp == null || seedProp.trim().isEmpty()) {
            return new SecureRandom();
        }
        try {
            long seed = Long.parseLong(seedProp.trim());
            return new Random(seed);
        } catch (NumberFormatException e) {
            return new SecureRandom();
        }
    }

    /**
     * Returns true with probability {@code p}.
     *
     * @param p probability in [0,1]
     */
    public static boolean chance(double p) {
        if (p <= 0) return false;
        if (p >= 1) return true;
        return RND.nextDouble() < p;
    }

    public static int randomInt(int bound) {
        return RND.nextInt(bound);
    }

    /**
     * Pick one option.
     */
    public static <T> T oneOf(T[] options) {
        if (options == null || options.length == 0) {
            throw new IllegalArgumentException("options must not be empty");
        }
        return options[RND.nextInt(options.length)];
    }

    /**
     * @return a random hex-ish token (useful for filenames)
     */
    public static String token(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(Integer.toHexString(RND.nextInt(16)));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
