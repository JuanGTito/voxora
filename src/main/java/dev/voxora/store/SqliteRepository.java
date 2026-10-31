package dev.voxora.store;

import static dev.voxora.domain.Model.require;

import dev.voxora.domain.Model.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

/* LFTY JGTM SCGC YARS DMFY */ public final class SqliteRepository implements Repository {
  /* LFTY JGTM SCGC YARS DMFY */ private final Path root;
  /* LFTY JGTM SCGC YARS DMFY */ private final Path database;

  /* LFTY JGTM SCGC YARS DMFY */ public SqliteRepository(/* LFTY JGTM SCGC YARS DMFY */ Path root) {
    this.root = root.toAbsolutePath().normalize();
    this.database = this.root.resolve("data/voxora.db");
    try {
      Files.createDirectories(database.getParent());
      Files.createDirectories(this.root.resolve("reports"));
      Class.forName("org.sqlite.JDBC");
      migrate();
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      throw storage(e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Override
  public Path root() {
    return root;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private Connection open() throws SQLException {
    return open(database);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static Connection open(
      /* LFTY JGTM SCGC YARS DMFY */ Path path) throws SQLException {
    /* LFTY JGTM SCGC YARS DMFY */ Connection c =
        DriverManager.getConnection("jdbc:sqlite:" + path);
    try (
    /* LFTY JGTM SCGC YARS DMFY */ Statement s = c.createStatement()) {
      s.execute("PRAGMA foreign_keys=ON");
      s.execute("PRAGMA busy_timeout=5000");
    }
    return c;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static final List<String> MIGRATIONS =
      List.of("/db/migration/V001__core.sql", "/db/migration/V002__voice.sql");

  /* LFTY JGTM SCGC YARS DMFY */ private static String migration(
      /* LFTY JGTM SCGC YARS DMFY */ int version) throws Exception {
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var stream =
        Objects.requireNonNull(
            SqliteRepository.class.getResourceAsStream(MIGRATIONS.get(version - 1)))) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static String checksum(
      /* LFTY JGTM SCGC YARS DMFY */ String sql) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(sql.getBytes(StandardCharsets.UTF_8)));
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static Map<Integer, String> applied(
      /* LFTY JGTM SCGC YARS DMFY */ Connection c) throws Exception {
    /* LFTY JGTM SCGC YARS DMFY */ Map<Integer, String> versions = new LinkedHashMap<>();
    try (
    /* LFTY JGTM SCGC YARS DMFY */ var s = c.createStatement();
        /* LFTY JGTM SCGC YARS DMFY */ var r =
            s.executeQuery("SELECT version,checksum FROM schema_migrations ORDER BY version")) {
      /* LFTY JGTM SCGC YARS DMFY */ while (r.next()) {
        /* LFTY JGTM SCGC YARS DMFY */ int v = r.getInt(1);
        require(
            v == versions.size() + 1
                && v <= MIGRATIONS.size()
                && checksum(migration(v)).equals(r.getString(2)),
            "La base pertenece a otra versión o la migración fue modificada.");
        versions.put(v, r.getString(2));
      }
    }
    return versions;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private void migrate() throws Exception {
    try (
    /* LFTY JGTM SCGC YARS DMFY */ Connection c = open()) {
      c.setAutoCommit(false);
      try (
      /* LFTY JGTM SCGC YARS DMFY */ Statement s = c.createStatement()) {
        s.execute(
            "CREATE TABLE IF NOT EXISTS schema_migrations(version INTEGER PRIMARY KEY, checksum"
                + " TEXT NOT NULL, applied_at TEXT NOT NULL)");
        /* LFTY JGTM SCGC YARS DMFY */ Map<Integer, String> versions = applied(c);
        /* LFTY JGTM SCGC YARS DMFY */ for (
        /* LFTY JGTM SCGC YARS DMFY */ int v = versions.size() + 1; v <= MIGRATIONS.size(); v++) {
          /* LFTY JGTM SCGC YARS DMFY */ String sql = migration(v);
          /* LFTY JGTM SCGC YARS DMFY */ for (
          /* LFTY JGTM SCGC YARS DMFY */ String command :
              sql.split(";")) /* LFTY JGTM SCGC YARS DMFY */
            if (!command.isBlank()) s.execute(command);
          execute(
              c, "INSERT INTO schema_migrations VALUES(?,?,?)", v, checksum(sql), Instant.now());
        }
        c.commit();
      } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
        c.rollback();
        throw e;
      }
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Override
  public synchronized <T> T transact(/* LFTY JGTM SCGC YARS DMFY */ Function<Tx, T> work) {
    try (
    /* LFTY JGTM SCGC YARS DMFY */ Connection c = open()) {
      c.setAutoCommit(false);
      try {
        /* LFTY JGTM SCGC YARS DMFY */ T result = work.apply(new SqlTx(c));
        c.commit();
        return result;
      } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
        c.rollback();
        throw e;
      }
    } catch (/* LFTY JGTM SCGC YARS DMFY */ RuleViolation e) {
      throw e;
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      throw storage(e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Override
  public synchronized void snapshot(/* LFTY JGTM SCGC YARS DMFY */ Path destination) {
    require(!Files.exists(destination), "El destino de respaldo ya existe.");
    try (
    /* LFTY JGTM SCGC YARS DMFY */ Connection c = open()) {
      execute(c, "VACUUM INTO ?", destination.toAbsolutePath().toString());
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      throw storage(e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ public static void validate(
      /* LFTY JGTM SCGC YARS DMFY */ Path source) {
    require(Files.isRegularFile(source), "No se encontró la base del respaldo.");
    try (
    /* LFTY JGTM SCGC YARS DMFY */ Connection c = open(source);
        /* LFTY JGTM SCGC YARS DMFY */ Statement s = c.createStatement()) {
      try (
      /* LFTY JGTM SCGC YARS DMFY */ ResultSet r = s.executeQuery("PRAGMA integrity_check")) {
        require(r.next() && "ok".equals(r.getString(1)), "La base de respaldo no está íntegra.");
      }
      try (
      /* LFTY JGTM SCGC YARS DMFY */ ResultSet r = s.executeQuery("PRAGMA foreign_key_check")) {
        require(!r.next(), "El respaldo tiene relaciones inválidas.");
      }
      try {
        require(!applied(c).isEmpty(), "El respaldo no tiene migraciones válidas.");
      } catch (/* LFTY JGTM SCGC YARS DMFY */ RuleViolation e) {
        throw e;
      } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
        throw storage(e);
      }
      try (
      /* LFTY JGTM SCGC YARS DMFY */ ResultSet r =
          s.executeQuery("SELECT count(*) FROM users WHERE active=1 AND role='SUPERADMIN'")) {
        require(r.next() && r.getInt(1) > 0, "El respaldo no tiene un superadministrador activo.");
      }
    } catch (/* LFTY JGTM SCGC YARS DMFY */ SQLException e) {
      throw storage(e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ @Override
  public synchronized void restoreDatabase(/* LFTY JGTM SCGC YARS DMFY */ Path source) {
    validate(source);
    try {
      /* LFTY JGTM SCGC YARS DMFY */ Path staged = database.resolveSibling("restore-staged.db");
      Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING);
      Files.move(
          staged, database, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      migrate();
    } catch (/* LFTY JGTM SCGC YARS DMFY */ Exception e) {
      throw storage(e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static RuntimeException storage(
      /* LFTY JGTM SCGC YARS DMFY */ Exception e) {
    return new IllegalStateException("No se pudo completar la operación de almacenamiento.", e);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static void bind(
      /* LFTY JGTM SCGC YARS DMFY */ PreparedStatement p, /* LFTY JGTM SCGC YARS DMFY */
      Object... args)
      throws SQLException {
    /* LFTY JGTM SCGC YARS DMFY */ for (
    /* LFTY JGTM SCGC YARS DMFY */ int i = 0; i < args.length; i++) {
      /* LFTY JGTM SCGC YARS DMFY */ Object arg = args[i];
      p.setObject(
          i + 1,
          arg instanceof /* LFTY JGTM SCGC YARS DMFY */ Enum<?> en
              ? en.name()
              : arg instanceof Instant ? arg.toString() : arg);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static int execute(
      /* LFTY JGTM SCGC YARS DMFY */ Connection c, /* LFTY JGTM SCGC YARS DMFY */
      String sql, /* LFTY JGTM SCGC YARS DMFY */
      Object... args)
      throws SQLException {
    try (
    /* LFTY JGTM SCGC YARS DMFY */ PreparedStatement p = c.prepareStatement(sql)) {
      bind(p, args);
      return p.executeUpdate();
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static long insert(
      /* LFTY JGTM SCGC YARS DMFY */ Connection c, /* LFTY JGTM SCGC YARS DMFY */
      String sql, /* LFTY JGTM SCGC YARS DMFY */
      Object... args)
      throws SQLException {
    execute(c, sql, args);
    try (
    /* LFTY JGTM SCGC YARS DMFY */ Statement s = c.createStatement();
        /* LFTY JGTM SCGC YARS DMFY */ ResultSet r = s.executeQuery("SELECT last_insert_rowid()")) {
      r.next();
      return r.getLong(1);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ @FunctionalInterface
  private interface Mapper<T> {
    /* LFTY JGTM SCGC YARS DMFY */ T map(
        /* LFTY JGTM SCGC YARS DMFY */ ResultSet r) throws SQLException;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static <T> List<T> query(
      /* LFTY JGTM SCGC YARS DMFY */ Connection c, /* LFTY JGTM SCGC YARS DMFY */
      String sql, /* LFTY JGTM SCGC YARS DMFY */
      Mapper<T> mapper, /* LFTY JGTM SCGC YARS DMFY */
      Object... args) {
    try (
    /* LFTY JGTM SCGC YARS DMFY */ PreparedStatement p = c.prepareStatement(sql)) {
      bind(p, args);
      try (
      /* LFTY JGTM SCGC YARS DMFY */ ResultSet r = p.executeQuery()) {
        /* LFTY JGTM SCGC YARS DMFY */ List<T> values = new ArrayList<>();
        /* LFTY JGTM SCGC YARS DMFY */ while (r.next()) values.add(mapper.map(r));
        return values;
      }
    } catch (/* LFTY JGTM SCGC YARS DMFY */ SQLException e) {
      throw storage(e);
    }
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static User mapUser(
      /* LFTY JGTM SCGC YARS DMFY */ ResultSet r) throws SQLException {
    return new User(
        r.getLong("id"),
        r.getString("username"),
        Role.valueOf(r.getString("role")),
        Specialty.valueOf(r.getString("specialty")),
        r.getInt("active") == 1);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static Instant instant(
      /* LFTY JGTM SCGC YARS DMFY */ ResultSet r, /* LFTY JGTM SCGC YARS DMFY */ String name)
      throws SQLException {
    /* LFTY JGTM SCGC YARS DMFY */ String text = r.getString(name);
    return text == null ? null : Instant.parse(text);
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static Long nullableLong(
      /* LFTY JGTM SCGC YARS DMFY */ ResultSet r, /* LFTY JGTM SCGC YARS DMFY */ String name)
      throws SQLException {
    /* LFTY JGTM SCGC YARS DMFY */ long value = r.getLong(name);
    return r.wasNull() ? null : value;
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static final String INCIDENT_QUERY =
      "SELECT i.*,coalesce(u.username,'Sin asignar') technician FROM incidents i LEFT JOIN users u"
          + " ON u.id=i.assigned_to";

  /* LFTY JGTM SCGC YARS DMFY */ private static Incident mapIncident(
      /* LFTY JGTM SCGC YARS DMFY */ ResultSet r) throws SQLException {
    return new Incident(
        r.getLong("id"),
        r.getString("title"),
        r.getString("equipment"),
        r.getString("location"),
        r.getString("description"),
        Priority.valueOf(r.getString("priority")),
        Specialty.valueOf(r.getString("specialty")),
        Status.valueOf(r.getString("status")),
        nullableLong(r, "assigned_to"),
        r.getString("technician"),
        r.getLong("created_by"),
        instant(r, "created_at"),
        instant(r, "updated_at"),
        instant(r, "started_at"),
        instant(r, "finished_at"),
        r.getString("draft"),
        nullableLong(r, "submitted_version"),
        r.getString("observation"),
        r.getInt("revision"));
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static final String VERSION_QUERY =
      "SELECT v.*,u.username author FROM report_versions v JOIN users u ON u.id=v.author_id";

  /* LFTY JGTM SCGC YARS DMFY */ private static Version mapVersion(
      /* LFTY JGTM SCGC YARS DMFY */ ResultSet r) throws SQLException {
    return new Version(
        r.getLong("id"),
        r.getLong("incident_id"),
        r.getInt("version"),
        r.getString("content"),
        r.getLong("author_id"),
        r.getString("author"),
        r.getString("kind"),
        instant(r, "created_at"));
  }

  /* LFTY JGTM SCGC YARS DMFY */ private static final class SqlTx implements Tx {
    /* LFTY JGTM SCGC YARS DMFY */ private final Connection c;

    /* LFTY JGTM SCGC YARS DMFY */ SqlTx(/* LFTY JGTM SCGC YARS DMFY */ Connection c) {
      this.c = c;
    }

    /* LFTY JGTM SCGC YARS DMFY */ private void exec(
        /* LFTY JGTM SCGC YARS DMFY */ String sql, /* LFTY JGTM SCGC YARS DMFY */ Object... args) {
      try {
        execute(c, sql, args);
      } catch (/* LFTY JGTM SCGC YARS DMFY */ SQLException e) {
        throw storage(e);
      }
    }

    /* LFTY JGTM SCGC YARS DMFY */ private long add(
        /* LFTY JGTM SCGC YARS DMFY */ String sql, /* LFTY JGTM SCGC YARS DMFY */ Object... args) {
      try {
        return insert(c, sql, args);
      } catch (/* LFTY JGTM SCGC YARS DMFY */ SQLException e) {
        throw storage(e);
      }
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public List<User> users() {
      return query(c, "SELECT * FROM users ORDER BY username", SqliteRepository::mapUser);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public Optional<Account> account(/* LFTY JGTM SCGC YARS DMFY */ String username) {
      return query(
              c,
              "SELECT * FROM users WHERE username=?",
              /* LFTY JGTM SCGC YARS DMFY */ r ->
                  new Account(mapUser(r), r.getString("password_hash")),
              username)
          .stream()
          .findFirst();
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public User user(/* LFTY JGTM SCGC YARS DMFY */ long id) {
      return query(c, "SELECT * FROM users WHERE id=?", SqliteRepository::mapUser, id).stream()
          .findFirst()
          .orElseThrow(() -> new RuleViolation("Usuario inexistente."));
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public boolean permitted(
        /* LFTY JGTM SCGC YARS DMFY */ long userId, /* LFTY JGTM SCGC YARS DMFY */
        String permission) {
      return !query(
              c,
              "SELECT 1 FROM users u JOIN role_permissions p ON p.role=u.role WHERE u.id=? AND"
                  + " u.active=1 AND p.permission=?",
              /* LFTY JGTM SCGC YARS DMFY */ r -> r.getInt(1),
              userId,
              permission)
          .isEmpty();
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public long addUser(
        /* LFTY JGTM SCGC YARS DMFY */ String username, /* LFTY JGTM SCGC YARS DMFY */
        String hash, /* LFTY JGTM SCGC YARS DMFY */
        Role role, /* LFTY JGTM SCGC YARS DMFY */
        Specialty specialty) {
      return add(
          "INSERT INTO users(username,password_hash,role,specialty,created_at) VALUES(?,?,?,?,?)",
          username,
          hash,
          role,
          specialty,
          Instant.now());
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void updateUser(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
        Role role, /* LFTY JGTM SCGC YARS DMFY */
        Specialty specialty, /* LFTY JGTM SCGC YARS DMFY */
        boolean active) {
      exec(
          "UPDATE users SET role=?,specialty=?,active=? WHERE id=?",
          role,
          specialty,
          active ? 1 : 0,
          id);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void password(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */ String hash) {
      exec("UPDATE users SET password_hash=? WHERE id=?", hash, id);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public List<Incident> incidents() {
      return query(c, INCIDENT_QUERY + " ORDER BY i.id DESC", SqliteRepository::mapIncident);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public Incident incident(/* LFTY JGTM SCGC YARS DMFY */ long id) {
      return query(c, INCIDENT_QUERY + " WHERE i.id=?", SqliteRepository::mapIncident, id).stream()
          .findFirst()
          .orElseThrow(() -> new RuleViolation("Incidencia inexistente."));
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public long addIncident(
        /* LFTY JGTM SCGC YARS DMFY */ String title,
        /* LFTY JGTM SCGC YARS DMFY */ String equipment,
        /* LFTY JGTM SCGC YARS DMFY */ String location,
        /* LFTY JGTM SCGC YARS DMFY */ String description,
        /* LFTY JGTM SCGC YARS DMFY */ Priority priority,
        /* LFTY JGTM SCGC YARS DMFY */ Specialty specialty,
        /* LFTY JGTM SCGC YARS DMFY */ long actor,
        /* LFTY JGTM SCGC YARS DMFY */ Instant at) {
      /* LFTY JGTM SCGC YARS DMFY */ long id =
          add(
              "INSERT INTO"
                  + " incidents(title,equipment,location,description,priority,specialty,created_by,created_at,updated_at)"
                  + " VALUES(?,?,?,?,?,?,?,?,?)",
              title,
              equipment,
              location,
              description,
              priority,
              specialty,
              actor,
              at,
              at);
      exec(
          "INSERT INTO incident_status_history(incident_id,to_status,changed_by,reason,at)"
              + " VALUES(?,'PENDIENTE',?,'Creación',?)",
          id,
          actor,
          at);
      return id;
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void assign(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
        long technician, /* LFTY JGTM SCGC YARS DMFY */
        long actor, /* LFTY JGTM SCGC YARS DMFY */
        Instant at) {
      exec(
          "UPDATE incident_assignments SET unassigned_at=? WHERE incident_id=? AND unassigned_at IS"
              + " NULL",
          at,
          id);
      exec(
          "INSERT INTO incident_assignments(incident_id,technician_id,assigned_by,assigned_at)"
              + " VALUES(?,?,?,?)",
          id,
          technician,
          actor,
          at);
      exec("UPDATE incidents SET assigned_to=? WHERE id=?", technician, id);
      changeStatus(id, Status.ASIGNADO, "Asignación", actor, at);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void changeStatus(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
        Status status, /* LFTY JGTM SCGC YARS DMFY */
        String reason, /* LFTY JGTM SCGC YARS DMFY */
        long actor, /* LFTY JGTM SCGC YARS DMFY */
        Instant at) {
      /* LFTY JGTM SCGC YARS DMFY */ Incident old = incident(id);
      exec(
          "UPDATE incidents SET status=?,updated_at=?,revision=revision+1 WHERE id=?",
          status,
          at,
          id);
      exec(
          "INSERT INTO"
              + " incident_status_history(incident_id,from_status,to_status,changed_by,reason,at)"
              + " VALUES(?,?,?,?,?,?)",
          id,
          old.status(),
          status,
          actor,
          reason,
          at);
      /* LFTY JGTM SCGC YARS DMFY */ if (status == Status.EN_PROCESO && old.startedAt() == null) {
        exec("UPDATE incidents SET started_at=? WHERE id=?", at, id);
        /* LFTY JGTM SCGC YARS DMFY */ long attention =
            add(
                "INSERT INTO attentions(incident_id,technician_id,started_at) VALUES(?,?,?)",
                id,
                old.assignedTo(),
                at);
        exec("INSERT INTO reports(attention_id) VALUES(?)", attention);
      }
      /* LFTY JGTM SCGC YARS DMFY */ if (status == Status.EN_PROCESO
          && old.status() == Status.EN_REVISION)
        exec("UPDATE incidents SET observation=? WHERE id=?", reason, id);
      /* LFTY JGTM SCGC YARS DMFY */ if (status == Status.COMPLETADO) {
        exec("UPDATE incidents SET finished_at=? WHERE id=?", at, id);
        exec("UPDATE attentions SET finished_at=? WHERE incident_id=?", at, id);
      }
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public Version addVersion(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
        String content, /* LFTY JGTM SCGC YARS DMFY */
        long author, /* LFTY JGTM SCGC YARS DMFY */
        String kind, /* LFTY JGTM SCGC YARS DMFY */
        Instant at) {
      /* LFTY JGTM SCGC YARS DMFY */ long versionId =
          add(
              "INSERT INTO"
                  + " report_versions(report_id,incident_id,version,content,author_id,kind,created_at)"
                  + " SELECT r.id,?,coalesce((SELECT max(version) FROM report_versions WHERE"
                  + " incident_id=?),0)+1,?,?,?,? FROM reports r JOIN attentions a ON"
                  + " a.id=r.attention_id WHERE a.incident_id=?",
              id,
              id,
              content,
              author,
              kind,
              at,
              id);
      return version(versionId);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void draft(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
        String content, /* LFTY JGTM SCGC YARS DMFY */
        Instant at) {
      exec(
          "UPDATE incidents SET draft=?,updated_at=?,revision=revision+1 WHERE id=?",
          content,
          at,
          id);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void submitted(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */ long versionId) {
      exec("UPDATE report_versions SET kind='ENVIADO' WHERE id=? AND incident_id=?", versionId, id);
      exec("UPDATE incidents SET submitted_version=? WHERE id=?", versionId, id);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public List<Version> versions(/* LFTY JGTM SCGC YARS DMFY */ long incidentId) {
      return query(
          c,
          VERSION_QUERY + " WHERE v.incident_id=? ORDER BY v.version DESC",
          SqliteRepository::mapVersion,
          incidentId);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public Version version(/* LFTY JGTM SCGC YARS DMFY */ long id) {
      return query(c, VERSION_QUERY + " WHERE v.id=?", SqliteRepository::mapVersion, id).stream()
          .findFirst()
          .orElseThrow(() -> new RuleViolation("Versión inexistente."));
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void review(
        /* LFTY JGTM SCGC YARS DMFY */ long incidentId, /* LFTY JGTM SCGC YARS DMFY */
        long versionId, /* LFTY JGTM SCGC YARS DMFY */
        long actor, /* LFTY JGTM SCGC YARS DMFY */
        String decision, /* LFTY JGTM SCGC YARS DMFY */
        String reason, /* LFTY JGTM SCGC YARS DMFY */
        Instant at) {
      exec(
          "INSERT INTO report_reviews(incident_id,version_id,reviewer_id,decision,comments,at)"
              + " VALUES(?,?,?,?,?,?)",
          incidentId,
          versionId,
          actor,
          decision,
          reason,
          at);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void event(
        /* LFTY JGTM SCGC YARS DMFY */ Long incidentId, /* LFTY JGTM SCGC YARS DMFY */
        long actor, /* LFTY JGTM SCGC YARS DMFY */
        String action, /* LFTY JGTM SCGC YARS DMFY */
        String detail, /* LFTY JGTM SCGC YARS DMFY */
        Instant at) {
      exec(
          "INSERT INTO audit_logs(incident_id,actor_id,action,detail,at) VALUES(?,?,?,?,?)",
          incidentId,
          actor,
          action,
          detail,
          at);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public List<Event> events(/* LFTY JGTM SCGC YARS DMFY */ Long incidentId) {
      return query(
          c,
          "SELECT e.*,u.username actor FROM audit_logs e JOIN users u ON u.id=e.actor_id"
              + (incidentId == null ? "" : " WHERE e.incident_id=?")
              + " ORDER BY e.id DESC",
          /* LFTY JGTM SCGC YARS DMFY */ r ->
              new Event(
                  r.getLong("id"),
                  nullableLong(r, "incident_id"),
                  r.getString("actor"),
                  r.getString("action"),
                  r.getString("detail"),
                  instant(r, "at")),
          incidentId == null ? new Object[0] : new Object[] {incidentId});
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public int inactivityMinutes() {
      return query(
              c,
              "SELECT value FROM app_settings WHERE"
                  + " key='inactivity_minutes'", /* LFTY JGTM SCGC YARS DMFY */
              r -> r.getInt(1))
          .getFirst();
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void inactivityMinutes(/* LFTY JGTM SCGC YARS DMFY */ int minutes) {
      exec("UPDATE app_settings SET value=? WHERE key='inactivity_minutes'", minutes);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public long addVoice(
        /* LFTY JGTM SCGC YARS DMFY */ long incidentId, /* LFTY JGTM SCGC YARS DMFY */
        long actor, /* LFTY JGTM SCGC YARS DMFY */
        String audioPath, /* LFTY JGTM SCGC YARS DMFY */
        Instant at) {
      return add(
          "INSERT INTO voice_entries(incident_id,actor_id,audio_path,created_at) VALUES(?,?,?,?)",
          incidentId,
          actor,
          audioPath,
          at);
    }

    /* LFTY JGTM SCGC YARS DMFY */ private VoiceEntry mapVoice(
        /* LFTY JGTM SCGC YARS DMFY */ ResultSet r) throws SQLException {
      return new VoiceEntry(
          r.getLong("id"),
          r.getLong("incident_id"),
          r.getLong("actor_id"),
          r.getString("audio_path"),
          r.getString("raw_text"),
          r.getString("corrected_text"),
          r.getString("source_hash"),
          r.getString("extraction_json"),
          r.getString("proposal"),
          instant(r, "created_at"));
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public VoiceEntry voice(/* LFTY JGTM SCGC YARS DMFY */ long id) {
      return query(c, "SELECT * FROM voice_entries WHERE id=?", this::mapVoice, id).stream()
          .findFirst()
          .orElseThrow(() -> new RuleViolation("Registro de voz inexistente."));
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public List<VoiceEntry> voices(/* LFTY JGTM SCGC YARS DMFY */ long incidentId) {
      return query(
          c,
          "SELECT * FROM voice_entries WHERE incident_id=? ORDER BY id DESC",
          this::mapVoice,
          incidentId);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void transcription(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */ String text) {
      exec(
          "UPDATE voice_entries SET raw_text=?,corrected_text=? WHERE id=? AND raw_text=''",
          text,
          text,
          id);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void extraction(
        /* LFTY JGTM SCGC YARS DMFY */ long id, /* LFTY JGTM SCGC YARS DMFY */
        String corrected, /* LFTY JGTM SCGC YARS DMFY */
        String hash, /* LFTY JGTM SCGC YARS DMFY */
        String json, /* LFTY JGTM SCGC YARS DMFY */
        String proposal) {
      exec(
          "UPDATE voice_entries SET corrected_text=?,source_hash=?,extraction_json=?,proposal=?"
              + " WHERE id=?",
          corrected,
          hash,
          json,
          proposal,
          id);
    }

    /* LFTY JGTM SCGC YARS DMFY */ @Override
    public void voiceVersion(
        /* LFTY JGTM SCGC YARS DMFY */ long versionId, /* LFTY JGTM SCGC YARS DMFY */
        long voiceId) {
      exec("UPDATE report_versions SET voice_entry_id=? WHERE id=?", voiceId, versionId);
    }
  }
}
