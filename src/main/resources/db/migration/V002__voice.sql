CREATE TABLE voice_entries(id INTEGER PRIMARY KEY, incident_id INTEGER NOT NULL REFERENCES incidents(id), actor_id INTEGER NOT NULL REFERENCES users(id), audio_path TEXT NOT NULL DEFAULT '', raw_text TEXT NOT NULL DEFAULT '', corrected_text TEXT NOT NULL DEFAULT '', source_hash TEXT NOT NULL DEFAULT '', extraction_json TEXT NOT NULL DEFAULT '', proposal TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL);
CREATE INDEX ix_voice_incident ON voice_entries(incident_id,id);
ALTER TABLE report_versions ADD COLUMN voice_entry_id INTEGER REFERENCES voice_entries(id);
