package dev.voxora.identity;

import static dev.voxora.domain.Model.*;

import dev.voxora.domain.Model.*;
import dev.voxora.store.Repository;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/* LFTY · JGTM · SCGC · YARS · DMFY */
public final class IdentityService {
  private record Session(long userId, Instant lastActivity) {}

  private final Repository repository;
  private final Passwords passwords = new Passwords();
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();
  private final Clock clock;
  private final String dummyHash = passwords.hash("non-user-timing-password".toCharArray());

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public IdentityService(Repository repository) {
    this(repository, Clock.systemUTC());
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public IdentityService(Repository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public boolean needsSetup() {
    return repository.transact(tx -> tx.users().isEmpty());
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void setup(String username, char[] password) {
    String normalized = normalize(username);
    String hash = passwords.hash(password);
    repository.transact(
        tx -> {
          require(tx.users().isEmpty(), "La instalación ya está inicializada.");
          long id = tx.addUser(normalized, hash, Role.SUPERADMIN, Specialty.GENERAL);
          tx.event(null, id, "INSTALACION_INICIAL", "Primer superadministrador", clock.instant());
          return null;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public String login(String username, char[] password) {
    String normalized = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    User user =
        repository.transact(
            tx -> {
              Optional<Account> account = tx.account(normalized);
              boolean valid =
                  passwords.verify(password, account.map(Account::hash).orElse(dummyHash));
              require(
                  valid && account.isPresent() && account.get().user().active(),
                  "Usuario o contraseña incorrectos.");
              User u = account.get().user();
              tx.event(null, u.id(), "LOGIN", "Inicio de sesión", clock.instant());
              return u;
            });
    String token = UUID.randomUUID().toString();
    sessions.put(token, new Session(user.id(), clock.instant()));
    return token;
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public User authorize(Repository.Tx tx, String token) {
    Session session = token == null ? null : sessions.get(token);
    require(session != null, "Inicia sesión para continuar.");
    if (!clock
        .instant()
        .isBefore(session.lastActivity.plus(Duration.ofMinutes(tx.inactivityMinutes())))) {
      sessions.remove(token);
      throw new RuleViolation("La sesión se bloqueó por inactividad. Inicia sesión nuevamente.");
    }
    User user = tx.user(session.userId);
    require(user.active(), "La cuenta está desactivada.");
    return user;
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public User current(String token) {
    return repository.transact(tx -> authorize(tx, token));
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public User requirePermission(Repository.Tx tx, String token, String permission) {
    User u = authorize(tx, token);
    require(tx.permitted(u.id(), permission), "No tienes permiso para realizar esta acción.");
    return u;
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void activity(String token) {
    repository.transact(
        tx -> {
          authorize(tx, token);
          sessions.computeIfPresent(token, (k, s) -> new Session(s.userId, clock.instant()));
          return null;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public Duration idle(String token) {
    Session session = sessions.get(token);
    return session == null
        ? Duration.ZERO
        : Duration.between(session.lastActivity, clock.instant());
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void logout(String token, boolean inactivity) {
    Session session = token == null ? null : sessions.remove(token);
    if (session != null)
      repository.transact(
          tx -> {
            tx.event(
                null,
                session.userId,
                inactivity ? "BLOQUEO_INACTIVIDAD" : "LOGOUT",
                "Sesión cerrada",
                clock.instant());
            return null;
          });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void revokeAll() {
    sessions.clear();
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public List<User> users(String token) {
    return repository.transact(
        tx -> {
          User u = authorize(tx, token);
          require(u.role().managesIncidents(), "Sin permiso para consultar usuarios.");
          return tx.users();
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void createUser(
      String token, String username, char[] password, Role role, Specialty specialty) {
    String normalized = normalize(username);
    require(role != null && specialty != null, "Selecciona rol y especialidad.");
    String hash = passwords.hash(password);
    repository.transact(
        tx -> {
          User actor = authorize(tx, token);
          require(
              actor.role() == Role.SUPERADMIN,
              "Solo soporte administra las cuentas en esta versión.");
          require(tx.account(normalized).isEmpty(), "El usuario ya existe.");
          long id = tx.addUser(normalized, hash, role, specialty);
          tx.event(
              null,
              actor.id(),
              "USUARIO_CREADO",
              "Usuario " + id + " · " + role.name(),
              clock.instant());
          return null;
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void updateUser(String token, long id, Role role, Specialty specialty, boolean active) {
    require(role != null && specialty != null, "Selecciona rol y especialidad.");
    repository.transact(
        tx -> {
          User actor = authorize(tx, token);
          require(actor.role() == Role.SUPERADMIN, "Solo soporte administra cuentas.");
          User old = tx.user(id);
          require(
              actor.id() != id || (role == Role.SUPERADMIN && active),
              "No puedes quitarte el acceso de soporte.");
          long others =
              tx.users().stream()
                  .filter(u -> u.id() != id && u.active() && u.role() == Role.SUPERADMIN)
                  .count();
          require(
              old.role() != Role.SUPERADMIN || (active && role == Role.SUPERADMIN) || others > 0,
              "Debe quedar un superadministrador activo.");
          boolean assigned =
              tx.incidents().stream()
                  .anyMatch(
                      i -> Objects.equals(i.assignedTo(), id) && i.status() != Status.COMPLETADO);
          require(
              !assigned || (active && role == Role.TECHNICIAN && specialty == old.specialty()),
              "Reasigna o finaliza las incidencias abiertas antes de cambiar este técnico.");
          tx.updateUser(id, role, specialty, active);
          tx.event(null, actor.id(), "USUARIO_MODIFICADO", "Usuario " + id, clock.instant());
          return null;
        });
    sessions.entrySet().removeIf(e -> e.getValue().userId == id && !e.getKey().equals(token));
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void resetPassword(String token, long id, char[] password) {
    String hash = passwords.hash(password);
    repository.transact(
        tx -> {
          User actor = authorize(tx, token);
          require(actor.role() == Role.SUPERADMIN, "Solo soporte restablece contraseñas.");
          tx.user(id);
          tx.password(id, hash);
          tx.event(null, actor.id(), "CONTRASENA_RESTABLECIDA", "Usuario " + id, clock.instant());
          return null;
        });
    sessions.entrySet().removeIf(e -> e.getValue().userId == id);
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public int inactivityMinutes(String token) {
    return repository.transact(
        tx -> {
          authorize(tx, token);
          return tx.inactivityMinutes();
        });
  }

  /* LFTY · JGTM · SCGC · YARS · DMFY */
  public void inactivityMinutes(String token, int minutes) {
    require(minutes >= 5 && minutes <= 30, "El intervalo debe estar entre 5 y 30 minutos.");
    repository.transact(
        tx -> {
          User actor = authorize(tx, token);
          require(actor.role() == Role.SUPERADMIN, "Solo soporte configura la sesión.");
          tx.inactivityMinutes(minutes);
          tx.event(
              null,
              actor.id(),
              "CONFIGURACION",
              "Inactividad: " + minutes + " minutos",
              clock.instant());
          return null;
        });
  }

  private static String normalize(String username) {
    String value = required(username, "usuario", 40).toLowerCase(Locale.ROOT);
    require(
        value.matches("[a-z0-9][a-z0-9._-]{2,39}"),
        "Usuario: 3–40 letras sin tildes, números, punto, guion o guion bajo.");
    return value;
  }
}
