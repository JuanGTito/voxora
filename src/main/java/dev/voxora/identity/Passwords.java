package dev.voxora.identity;

import static dev.voxora.domain.Model.require;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;

/** Argon2id v19, 19 MiB, 2 passes, 1 lane. Format includes parameters and salt. */
/* LFTY JGTM SCGC YARS DMFY */ public final class Passwords {
  /* LFTY JGTM SCGC YARS DMFY */ public String hash(
      /* LFTY JGTM SCGC YARS DMFY */ char[] password) {
    require(
        password != null && password.length >= 10 && password.length <= 256,
        "Usa una contraseña de 10 a 256 caracteres.");
    /* LFTY JGTM SCGC YARS DMFY */ byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    /* LFTY JGTM SCGC YARS DMFY */ byte[] derived = derive(password, salt, 19456, 2, 1);
    try {
      return "$argon2id$v=19$m=19456,t=2,p=1$" + encode(salt) + "$" + encode(derived);
    } finally {
      Arrays.fill(derived, (byte) 0);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public boolean verify(
      /* LFTY JGTM SCGC YARS DMFY */ char[] password, /* LFTY JGTM SCGC YARS DMFY */
      String encoded) {
    /* LFTY JGTM SCGC YARS DMFY */ byte[] actual = null;
    try {
      /* LFTY JGTM SCGC YARS DMFY */ if (password == null || password.length > 256) return false;
      /* LFTY JGTM SCGC YARS DMFY */ String[] parts = encoded.split("\\$");
      /* LFTY JGTM SCGC YARS DMFY */ if (parts.length != 6
          || !parts[1].equals("argon2id")
          || !parts[2].equals("v=19")
          || !parts[3].equals("m=19456,t=2,p=1")) return false;
      /* LFTY JGTM SCGC YARS DMFY */ byte[] expected = Base64.getDecoder().decode(parts[5]);
      /* LFTY JGTM SCGC YARS DMFY */ byte[] salt = Base64.getDecoder().decode(parts[4]);
      /* LFTY JGTM SCGC YARS DMFY */ if (salt.length != 16 || expected.length != 32) return false;
      actual = derive(password, salt, 19456, 2, 1);
      return MessageDigest.isEqual(expected, actual);
    } catch (/* LFTY JGTM SCGC YARS DMFY */ IllegalArgumentException e) {
      return false;
    } finally {
      /* LFTY JGTM SCGC YARS DMFY */ if (actual != null) Arrays.fill(actual, (byte) 0);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static String encode(
      /* LFTY JGTM SCGC YARS DMFY */ byte[] bytes) {
    return Base64.getEncoder().withoutPadding().encodeToString(bytes);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static byte[] derive(
      /* LFTY JGTM SCGC YARS DMFY */ char[] password, /* LFTY JGTM SCGC YARS DMFY */
      byte[] salt, /* LFTY JGTM SCGC YARS DMFY */
      int memory, /* LFTY JGTM SCGC YARS DMFY */
      int passes, /* LFTY JGTM SCGC YARS DMFY */
      int lanes) {
    /* LFTY JGTM SCGC YARS DMFY */ var params =
        new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withSalt(salt)
            .withMemoryAsKB(memory)
            .withIterations(passes)
            .withParallelism(lanes)
            .build();
    /* LFTY JGTM SCGC YARS DMFY */ var generator = new Argon2BytesGenerator();
    generator.init(params);
    /* LFTY JGTM SCGC YARS DMFY */ byte[] result = new byte[32];
    generator.generateBytes(password, result);
    params.clear();
    return result;
  }
}
