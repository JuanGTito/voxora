package dev.voxora.store;

import dev.voxora.domain.Model.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Application port. Every callback is one isolated transaction. */
/* LFTY · JGTM · SCGC · YARS · DMFY */
public interface Repository {
  <T> T transact(Function<Tx, T> work);

  void snapshot(Path destination);

  void restoreDatabase(Path source);

  Path root();

  interface Tx {
    List<User> users();

    Optional<Account> account(String username);

    User user(long id);

    boolean permitted(long userId, String permission);

    long addUser(String username, String hash, Role role, Specialty specialty);

    void updateUser(long id, Role role, Specialty specialty, boolean active);

    void password(long id, String hash);

    List<Incident> incidents();

    Incident incident(long id);

    long addIncident(
        String title,
        String equipment,
        String location,
        String description,
        Priority priority,
        Specialty specialty,
        long actor,
        Instant at);

    void assign(long id, long technician, long actor, Instant at);

    void changeStatus(long id, Status status, String reason, long actor, Instant at);

    Version addVersion(long incidentId, String content, long author, String kind, Instant at);

    void draft(long incidentId, String content, Instant at);

    void submitted(long incidentId, long versionId);

    List<Version> versions(long incidentId);

    Version version(long id);

    void review(
        long incidentId, long versionId, long actor, String decision, String reason, Instant at);

    void event(Long incidentId, long actor, String action, String detail, Instant at);

    List<Event> events(Long incidentId);

    int inactivityMinutes();

    void inactivityMinutes(int minutes);

    long addVoice(long incidentId, long actor, String audioPath, Instant at);

    VoiceEntry voice(long id);

    List<VoiceEntry> voices(long incidentId);

    void transcription(long id, String rawText);

    void extraction(long id, String corrected, String hash, String json, String proposal);

    void voiceVersion(long versionId, long voiceId);
  }
}
