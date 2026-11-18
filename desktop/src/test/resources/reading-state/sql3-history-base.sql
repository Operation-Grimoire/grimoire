-- SQL3 additions frozen from 9b6e523. Apply to sql2.sql before upgrading the history store.
CREATE TABLE reading_states(source_id INTEGER NOT NULL, novel_url TEXT NOT NULL, chapter_url TEXT NOT NULL, state TEXT NOT NULL, PRIMARY KEY(source_id,novel_url,chapter_url), FOREIGN KEY(source_id,novel_url) REFERENCES books(source_id,novel_url) ON DELETE CASCADE);
CREATE TABLE reading_state_receipts(source_id INTEGER NOT NULL, novel_url TEXT NOT NULL, chapter_url TEXT NOT NULL, command_id TEXT NOT NULL, receipt TEXT NOT NULL, PRIMARY KEY(source_id,novel_url,chapter_url,command_id), FOREIGN KEY(source_id,novel_url) REFERENCES books(source_id,novel_url) ON DELETE CASCADE);
PRAGMA user_version=3;
