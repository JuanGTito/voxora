package dev.voxora.store;

import dev.voxora.domain.Model.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Application port. Every callback is one isolated transaction. */
/* LFTY JGTM SCGC YARS DMFY */ public interface Repository {
  /* LFTY JGTM SCGC YARS DMFY */ <T> T transact(
      /* LFTY JGTM SCGC YARS DMFY */ Function<Tx, T> work);

  /* LFTY JGTM SCGC YARS DMFY */ void snapshot(/* LFTY JGTM SCGC YARS DMFY */ Path destination);

  /* LFTY JGTM SCGC YARS DMFY */ void restoreDatabase(/* LFTY JGTM SCGC YARS DMFY */ Path source);

  /* LFTY JGTM SCGC YARS DMFY */ Path root();

  /* LFTY JGTM SCGC YARS DMFY */ interface Tx {
    /* LFTY JGTM SCGC YARS DMFY */ List<User> users();

    /* LFTY JGTM SCGC YARS DMFY */ Optional<Account> account(
        /* LFTY JGTM SCGC YARS DMFY */ String username);

    /* LFTY JGTM SCGC YARS DMFY */ User user(/* LFTY JGTM SCGC YARS DMFY */ long id);

    /* LFTY JGTM SCGC YARS DMFY */ boolean permitted(
        /* LFTY JGTM SCGC YARS DMFY */ long userId, /* LFTY JGTM SCGC YARS DMFY */
        String permission);

    /* LFTY JGTM SCGC YARS DMFY */ long addUser(
        /* LFTY JGTM SCGC YARS DMFY */ String username, /* LFTY JGTM SCGC YARS DMFY */
        String hash, /* LFTY JGTM SCGC YARS DMFY */
        Role role, /* LFTY JGTM SCGC YARS DMFY */
        Specialty specialty);

    /* LFTY JGTM SCGC YARS DMFY */ void updateUser(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
        Role role, /* LFTY JGTM SCGC YARS DMFY */
        Specialty specialty, /* LFTY JGTM SCGC YARS DMFY */
        boolean active);

    /* LFTY JGTM SCGC YARS DMFY */ void password(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */ String hash);

    /* LFTY JGTM SCGC YARS DMFY */ List<Incident> incidents();

    /* LFTY JGTM SCGC YARS DMFY */ Incident incident(/* LFTY JGTM SCGC YARS DMFY */ long id);

    /* LFTY JGTM SCGC YARS DMFY */ long addIncident(
        /* LFTY JGTM SCGC YARS DMFY */ String title,
        /* LFTY JGTM SCGC YARS DMFY */ String equipment,
        /* LFTY JGTM SCGC YARS DMFY */ String location,
        /* LFTY JGTM SCGC YARS DMFY */ String description,
        /* LFTY JGTM SCGC YARS DMFY */ Priority priority,
        /* LFTY JGTM SCGC YARS DMFY */ Specialty specialty,
        /* LFTY JGTM SCGC YARS DMFY */ long actor,
        /* LFTY JGTM SCGC YARS DMFY */ Instant at);

    /* LFTY JGTM SCGC YARS DMFY */ void assign(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
        long technician, /* LFTY JGTM SCGC YARS DMFY */
        long actor, /* LFTY JGTM SCGC YARS DMFY */
        Instant at);

    /* LFTY JGTM SCGC YARS DMFY */ void changeStatus(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
        Status status, /* LFTY JGTM SCGC YARS DMFY */
        String reason, /* LFTY JGTM SCGC YARS DMFY */
        long actor, /* LFTY JGTM SCGC YARS DMFY */
        Instant at);

    /* LFTY JGTM SCGC YARS DMFY */ Version addVersion(
        /* LFTY JGTM SCGC YARS DMFY */ long incidentId, /* LFTY JGTM SCGC YARS DMFY */
        String content, /* LFTY JGTM SCGC YARS DMFY */
        long author, /* LFTY JGTM SCGC YARS DMFY */
        String kind, /* LFTY JGTM SCGC YARS DMFY */
        Instant at);

    /* LFTY JGTM SCGC YARS DMFY */ void draft(
        /* LFTY JGTM SCGC YARS DMFY */ long incidentId, /* LFTY JGTM SCGC YARS DMFY */
        String content, /* LFTY JGTM SCGC YARS DMFY */
        Instant at);

    /* LFTY JGTM SCGC YARS DMFY */ void submitted(
        /* LFTY JGTM SCGC YARS DMFY */ long incidentId, /* LFTY JGTM SCGC YARS DMFY */
        long versionId);

    /* LFTY JGTM SCGC YARS DMFY */ List<Version> versions(
        /* LFTY JGTM SCGC YARS DMFY */ long incidentId);

    /* LFTY JGTM SCGC YARS DMFY */ Version version(/* LFTY JGTM SCGC YARS DMFY */ long id);

    /* LFTY JGTM SCGC YARS DMFY */ void review(
        /* LFTY JGTM SCGC YARS DMFY */ long incidentId, /* LFTY JGTM SCGC YARS DMFY */
        long versionId, /* LFTY JGTM SCGC YARS DMFY */
        long actor, /* LFTY JGTM SCGC YARS DMFY */
        String decision, /* LFTY JGTM SCGC YARS DMFY */
        String reason, /* LFTY JGTM SCGC YARS DMFY */
        Instant at);

    /* LFTY JGTM SCGC YARS DMFY */ void event(
        /* LFTY JGTM SCGC YARS DMFY */ Long incidentId, /* LFTY JGTM SCGC YARS DMFY */
        long actor, /* LFTY JGTM SCGC YARS DMFY */
        String action, /* LFTY JGTM SCGC YARS DMFY */
        String detail, /* LFTY JGTM SCGC YARS DMFY */
        Instant at);

    /* LFTY JGTM SCGC YARS DMFY */ List<Event> events(
        /* LFTY JGTM SCGC YARS DMFY */ Long incidentId);

    /* LFTY JGTM SCGC YARS DMFY */ int inactivityMinutes();

    /* LFTY JGTM SCGC YARS DMFY */ void inactivityMinutes(
        /* LFTY JGTM SCGC YARS DMFY */ int minutes);

    /* LFTY JGTM SCGC YARS DMFY */ long addVoice(
        /* LFTY JGTM SCGC YARS DMFY */ long incidentId, /* LFTY JGTM SCGC YARS DMFY */
        long actor, /* LFTY JGTM SCGC YARS DMFY */
        String audioPath, /* LFTY JGTM SCGC YARS DMFY */
        Instant at);

    /* LFTY JGTM SCGC YARS DMFY */ VoiceEntry voice(/* LFTY JGTM SCGC YARS DMFY */ long id);

    /* LFTY JGTM SCGC YARS DMFY */ List<VoiceEntry> voices(
        /* LFTY JGTM SCGC YARS DMFY */ long incidentId);

    /* LFTY JGTM SCGC YARS DMFY */ void transcription(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */ String rawText);

    /* LFTY JGTM SCGC YARS DMFY */ void extraction(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
        String corrected, /* LFTY JGTM SCGC YARS DMFY */
        String hash, /* LFTY JGTM SCGC YARS DMFY */
        String json, /* LFTY JGTM SCGC YARS DMFY */
        String proposal);

    /* LFTY JGTM SCGC YARS DMFY */ void voiceVersion(
        /* LFTY JGTM SCGC YARS DMFY */ long versionId, /* LFTY JGTM SCGC YARS DMFY */ long voiceId);
  }
}
