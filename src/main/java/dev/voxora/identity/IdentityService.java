package dev.voxora.identity;

import static dev.voxora.domain.Model.*;

import dev.voxora.domain.Model.*;
import dev.voxora.store.Repository;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/* LFTY JGTM SCGC YARS DMFY */ public final class IdentityService {
  /* LFTY JGTM SCGC YARS DMFY */ private record Session(
      /* LFTY JGTM SCGC YARS DMFY */ long userId, /* LFTY JGTM SCGC YARS DMFY */
      Instant lastActivity) {}

  /* LFTY JGTM SCGC YARS DMFY */ private final Repository repository;
  /* LFTY JGTM SCGC YARS DMFY */ private final Passwords passwords = new Passwords();
  /* LFTY JGTM SCGC YARS DMFY */ private final Map<String, Session> sessions =
      new ConcurrentHashMap<>();
  /* LFTY JGTM SCGC YARS DMFY */ private final Clock clock;
  /* LFTY JGTM SCGC YARS DMFY */ private final String dummyHash =
      passwords.hash("non-user-timing-password".toCharArray());

  /* LFTY JGTM SCGC YARS DMFY */ public IdentityService(
      /* LFTY JGTM SCGC YARS DMFY */ Repository repository) {
    this(repository, Clock.systemUTC());
  }

  /* LFTY JGTM SCGC YARS DMFY */ public IdentityService(
      /* LFTY JGTM SCGC YARS DMFY */ Repository repository, /* LFTY JGTM SCGC YARS DMFY */
      Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  /* LFTY JGTM SCGC YARS DMFY */ public boolean needsSetup() {
    return repository.transact(/* LFTY JGTM SCGC YARS DMFY */ tx -> tx.users().isEmpty());
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void setup(
      /* LFTY JGTM SCGC YARS DMFY */ String username, /* LFTY JGTM SCGC YARS DMFY */
      char[] password) {
    /* LFTY JGTM SCGC YARS DMFY */ String normalized = normalize(username);
    /* LFTY JGTM SCGC YARS DMFY */ String hash = passwords.hash(password);
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          require(tx.users().isEmpty(), "La instalación ya está inicializada.");
          /* LFTY JGTM SCGC YARS DMFY */ long id =
              tx.addUser(normalized, hash, Role.SUPERADMIN, Specialty.GENERAL);
          tx.event(null, id, "INSTALACION_INICIAL", "Primer superadministrador", clock.instant());
          return null;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public String login(
      /* LFTY JGTM SCGC YARS DMFY */ String username, /* LFTY JGTM SCGC YARS DMFY */
      char[] password) {
    /* LFTY JGTM SCGC YARS DMFY */ String normalized =
        username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    /* LFTY JGTM SCGC YARS DMFY */ User user =
        repository.transact(
            /* LFTY JGTM SCGC YARS DMFY */ tx -> {
              /* LFTY JGTM SCGC YARS DMFY */ Optional<Account> account = tx.account(normalized);
              /* LFTY JGTM SCGC YARS DMFY */ boolean valid =
                  passwords.verify(password, account.map(Account::hash).orElse(dummyHash));
              require(
                  valid && account.isPresent() && account.get().user().active(),
                  "Usuario o contraseña incorrectos.");
              /* LFTY JGTM SCGC YARS DMFY */ User u = account.get().user();
              tx.event(null, u.id(), "LOGIN", "Inicio de sesión", clock.instant());
              return u;
            });
    /* LFTY JGTM SCGC YARS DMFY */ String token = UUID.randomUUID().toString();
    sessions.put(token, new Session(user.id(), clock.instant()));
    return token;
  }

  /* LFTY JGTM SCGC YARS DMFY */ public User authorize(
      /* LFTY JGTM SCGC YARS DMFY */ Repository.Tx tx, /* LFTY JGTM SCGC YARS DMFY */
      String token) {
    /* LFTY JGTM SCGC YARS DMFY */ Session session = token == null ? null : sessions.get(token);
    require(session != null, "Inicia sesión para continuar.");
    /* LFTY JGTM SCGC YARS DMFY */ if (!clock
        .instant()
        .isBefore(session.lastActivity.plus(Duration.ofMinutes(tx.inactivityMinutes())))) {
      sessions.remove(token);
      throw new RuleViolation("La sesión se bloqueó por inactividad. Inicia sesión nuevamente.");
    }
    /* LFTY JGTM SCGC YARS DMFY */ User user = tx.user(session.userId);
    require(user.active(), "La cuenta está desactivada.");
    return user;
  }

  /* LFTY JGTM SCGC YARS DMFY */ public User current(/* LFTY JGTM SCGC YARS DMFY */ String token) {
    return repository.transact(/* LFTY JGTM SCGC YARS DMFY */ tx -> authorize(tx, token));
  }

  /* LFTY JGTM SCGC YARS DMFY */ public User requirePermission(
      /* LFTY JGTM SCGC YARS DMFY */ Repository.Tx tx, /* LFTY JGTM SCGC YARS DMFY */
      String token, /* LFTY JGTM SCGC YARS DMFY */
      String permission) {
    /* LFTY JGTM SCGC YARS DMFY */ User u = authorize(tx, token);
    require(tx.permitted(u.id(), permission), "No tienes permiso para realizar esta acción.");
    return u;
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void activity(/* LFTY JGTM SCGC YARS DMFY */ String token) {
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          authorize(tx, token);
          sessions.computeIfPresent(
              token,
              (/* LFTY JGTM SCGC YARS DMFY */ k, /* LFTY JGTM SCGC YARS DMFY */ s) ->
                  new Session(s.userId, clock.instant()));
          return null;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public Duration idle(/* LFTY JGTM SCGC YARS DMFY */ String token) {
    /* LFTY JGTM SCGC YARS DMFY */ Session session = sessions.get(token);
    return session == null
        ? Duration.ZERO
        : Duration.between(session.lastActivity, clock.instant());
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void logout(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      boolean inactivity) {
    /* LFTY JGTM SCGC YARS DMFY */ Session session = token == null ? null : sessions.remove(token);
    /* LFTY JGTM SCGC YARS DMFY */ if (session != null)
      repository.transact(
          /* LFTY JGTM SCGC YARS DMFY */ tx -> {
            tx.event(
                null,
                session.userId,
                inactivity ? "BLOQUEO_INACTIVIDAD" : "LOGOUT",
                "Sesión cerrada",
                clock.instant());
            return null;
          });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void revokeAll() {
    sessions.clear();
  }

  /* LFTY JGTM SCGC YARS DMFY */ public List<User> users(
      /* LFTY JGTM SCGC YARS DMFY */ String token) {
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User u = authorize(tx, token);
          require(u.role().managesIncidents(), "Sin permiso para consultar usuarios.");
          return tx.users();
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void createUser(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      String username, /* LFTY JGTM SCGC YARS DMFY */
      char[] password, /* LFTY JGTM SCGC YARS DMFY */
      Role role, /* LFTY JGTM SCGC YARS DMFY */
      Specialty specialty) {
    /* LFTY JGTM SCGC YARS DMFY */ String normalized = normalize(username);
    require(role != null && specialty != null, "Selecciona rol y especialidad.");
    /* LFTY JGTM SCGC YARS DMFY */ String hash = passwords.hash(password);
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User actor = authorize(tx, token);
          require(
              actor.role() == Role.SUPERADMIN,
              "Solo soporte administra las cuentas en esta versión.");
          require(tx.account(normalized).isEmpty(), "El usuario ya existe.");
          /* LFTY JGTM SCGC YARS DMFY */ long id = tx.addUser(normalized, hash, role, specialty);
          tx.event(
              null,
              actor.id(),
              "USUARIO_CREADO",
              "Usuario " + id + " · " + role.name(),
              clock.instant());
          return null;
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void updateUser(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long id, /* LFTY JGTM SCGC YARS DMFY */
      Role role, /* LFTY JGTM SCGC YARS DMFY */
      Specialty specialty, /* LFTY JGTM SCGC YARS DMFY */
      boolean active) {
    require(role != null && specialty != null, "Selecciona rol y especialidad.");
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User actor = authorize(tx, token);
          require(actor.role() == Role.SUPERADMIN, "Solo soporte administra cuentas.");
          /* LFTY JGTM SCGC YARS DMFY */ User old = tx.user(id);
          require(
              actor.id() != id || (role == Role.SUPERADMIN && active),
              "No puedes quitarte el acceso de soporte.");
          /* LFTY JGTM SCGC YARS DMFY */ long others =
              tx.users().stream()
                  .filter(
                      /* LFTY JGTM SCGC YARS DMFY */ u ->
                          u.id() != id && u.active() && u.role() == Role.SUPERADMIN)
                  .count();
          require(
              old.role() != Role.SUPERADMIN || (active && role == Role.SUPERADMIN) || others > 0,
              "Debe quedar un superadministrador activo.");
          /* LFTY JGTM SCGC YARS DMFY */ boolean assigned =
              tx.incidents().stream()
                  .anyMatch(
                      /* LFTY JGTM SCGC YARS DMFY */ i ->
                          Objects.equals(i.assignedTo(), id) && i.status() != Status.COMPLETADO);
          require(
              !assigned || (active && role == Role.TECHNICIAN && specialty == old.specialty()),
              "Reasigna o finaliza las incidencias abiertas antes de cambiar este técnico.");
          tx.updateUser(id, role, specialty, active);
          tx.event(null, actor.id(), "USUARIO_MODIFICADO", "Usuario " + id, clock.instant());
          return null;
        });
    sessions
        .entrySet()
        .removeIf(
            /* LFTY JGTM SCGC YARS DMFY */ e ->
                e.getValue().userId == id && !e.getKey().equals(token));
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void resetPassword(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */
      long id, /* LFTY JGTM SCGC YARS DMFY */
      char[] password) {
    /* LFTY JGTM SCGC YARS DMFY */ String hash = passwords.hash(password);
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User actor = authorize(tx, token);
          require(actor.role() == Role.SUPERADMIN, "Solo soporte restablece contraseñas.");
          tx.user(id);
          tx.password(id, hash);
          tx.event(null, actor.id(), "CONTRASENA_RESTABLECIDA", "Usuario " + id, clock.instant());
          return null;
        });
    sessions.entrySet().removeIf(/* LFTY JGTM SCGC YARS DMFY */ e -> e.getValue().userId == id);
  }

  /* LFTY JGTM SCGC YARS DMFY */ public int inactivityMinutes(
      /* LFTY JGTM SCGC YARS DMFY */ String token) {
    return repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          authorize(tx, token);
          return tx.inactivityMinutes();
        });
  }

  /* LFTY JGTM SCGC YARS DMFY */ public void inactivityMinutes(
      /* LFTY JGTM SCGC YARS DMFY */ String token, /* LFTY JGTM SCGC YARS DMFY */ int minutes) {
    require(minutes >= 5 && minutes <= 30, "El intervalo debe estar entre 5 y 30 minutos.");
    repository.transact(
        /* LFTY JGTM SCGC YARS DMFY */ tx -> {
          /* LFTY JGTM SCGC YARS DMFY */ User actor = authorize(tx, token);
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

  /* LFTY JGTM SCGC YARS DMFY */ private static String normalize(
      /* LFTY JGTM SCGC YARS DMFY */ String username) {
    /* LFTY JGTM SCGC YARS DMFY */ String value =
        required(username, "usuario", 40).toLowerCase(Locale.ROOT);
    require(
        value.matches("[a-z0-9][a-z0-9._-]{2,39}"),
        "Usuario: 3–40 letras sin tildes, números, punto, guion o guion bajo.");
    return value;
  }
}
